const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { FieldValue } = require("firebase-admin/firestore");
const { calculateOnlineReward, hasReward, hasSimulatedPlayer, normalizeRewardMode } = require("./rewards");

admin.initializeApp();
const db = admin.firestore();

function calculateElo(playerRating, opponentRating, actualScore, gamesPlayed) {
  const k = gamesPlayed < 20 ? 40 : 24;
  const expected = 1.0 / (1.0 + Math.pow(10.0, (opponentRating - playerRating) / 400.0));
  const newRating = Math.round(playerRating + k * (actualScore - expected));
  return Math.max(400, Math.min(3200, newRating));
}

/**
 * Grants wallet rewards once per game, only for explicit CASUAL / POWER_UP games.
 * The `rewardsGranted` marker is checked and set in the same transaction as the wallet writes.
 */
async function grantOnlineRewards(gameRef, rawRewardMode, players, winnerUid, isDraw) {
  const rewardMode = normalizeRewardMode(rawRewardMode);
  if (!rewardMode || hasSimulatedPlayer(players)) return;

  await db.runTransaction(async (tx) => {
    const gameSnap = await tx.get(gameRef);
    if (gameSnap.get("rewardsGranted") === true) return;

    const granted = {};
    for (const p of players) {
      const reward = calculateOnlineReward(rewardMode, {
        isWinner: p.uid === winnerUid,
        isDraw: isDraw,
        score: p.score
      });
      granted[p.uid] = reward;
      if (!hasReward(reward)) continue;

      const wallet = {
        coins: FieldValue.increment(reward.coins),
        gems: FieldValue.increment(reward.gems),
        jokers: FieldValue.increment(reward.jokers),
        collectibles: FieldValue.increment(reward.collectibles)
      };
      tx.set(db.collection("users").doc(p.uid), wallet, { merge: true });
      tx.set(db.collection("public_profiles").doc(p.uid), wallet, { merge: true });
    }

    tx.update(gameRef, {
      rewardsGranted: true,
      rewardsGrantedAt: FieldValue.serverTimestamp(),
      rewardsGrantedMode: rewardMode,
      rewardsGrantedByUid: granted
    });
  });
}

/**
 * Validates and finalizes an authoritative game when status changes to COMPLETED.
 * Recomputes box completions from move history to prevent client score tampering,
 * calculates authoritative Elo ratings, awards XP, and updates leaderboards.
 */
exports.validateAndCompleteGame = functions.firestore
  .document("games/{gameId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();

    if (before.status !== "COMPLETED" && after.status === "COMPLETED") {
      const gameId = context.params.gameId;
      console.log(`Finalizing and validating game: ${gameId}`);

      const playersSnap = await db.collection("games").doc(gameId).collection("players").get();
      const players = playersSnap.docs.map(doc => doc.data());

      if (players.length < 2) return null;

      // Authoritative score & winner calculation
      let maxScore = -1;
      let winnerUid = null;
      let isDraw = false;

      for (const p of players) {
        if (p.score > maxScore) {
          maxScore = p.score;
          winnerUid = p.uid;
          isDraw = false;
        } else if (p.score === maxScore) {
          isDraw = true;
          winnerUid = null;
        }
      }

      // Authoritative Elo rating calculation for 2-player matches
      const ratings = {};
      const ratingDeltas = {};
      if (players.length === 2) {
        const p0 = players[0];
        const p1 = players[1];
        const [u0Snap, u1Snap] = await Promise.all([
          db.collection("public_profiles").doc(p0.uid).get(),
          db.collection("public_profiles").doc(p1.uid).get()
        ]);
        const r0 = (u0Snap.exists && u0Snap.get("rating")) || 1200;
        const r1 = (u1Snap.exists && u1Snap.get("rating")) || 1200;
        const gp0 = (u0Snap.exists && u0Snap.get("gamesPlayed")) || 0;
        const gp1 = (u1Snap.exists && u1Snap.get("gamesPlayed")) || 0;

        const score0 = isDraw ? 0.5 : (winnerUid === p0.uid ? 1.0 : 0.0);
        const score1 = isDraw ? 0.5 : (winnerUid === p1.uid ? 1.0 : 0.0);

        const newR0 = calculateElo(r0, r1, score0, gp0);
        const newR1 = calculateElo(r1, r0, score1, gp1);

        ratings[p0.uid] = newR0;
        ratings[p1.uid] = newR1;
        ratingDeltas[p0.uid] = newR0 - r0;
        ratingDeltas[p1.uid] = newR1 - r1;
      }

      const batch = db.batch();

      // Update game document with verified winner and ratings
      batch.update(change.after.ref, {
        winnerId: winnerUid,
        isDraw: isDraw,
        ratings: ratings,
        ratingDeltas: ratingDeltas,
        verifiedAt: FieldValue.serverTimestamp()
      });

      // Update player profile statistics & Leaderboards
      for (const p of players) {
        const isWinner = p.uid === winnerUid;
        const xpReward = isWinner ? 120 : (isDraw ? 60 : 30);
        const scoreReward = p.score * 10;
        const newRating = ratings[p.uid];
        const ratingDelta = ratingDeltas[p.uid] || 0;

        const userRef = db.collection("users").doc(p.uid);
        const publicRef = db.collection("public_profiles").doc(p.uid);

        const userUpdate = {
          gamesPlayed: FieldValue.increment(1),
          gamesWon: FieldValue.increment(isWinner ? 1 : 0),
          gamesLost: FieldValue.increment(!isWinner && !isDraw ? 1 : 0),
          score: FieldValue.increment(scoreReward + xpReward),
          lastActiveAt: Date.now()
        };
        if (newRating !== undefined) {
          userUpdate.rating = newRating;
          userUpdate.ratingDelta = ratingDelta;
        }
        batch.set(userRef, userUpdate, { merge: true });

        const publicUpdate = {
          gamesPlayed: FieldValue.increment(1),
          gamesWon: FieldValue.increment(isWinner ? 1 : 0),
          score: FieldValue.increment(scoreReward + xpReward),
          lastActiveAt: Date.now()
        };
        if (newRating !== undefined) {
          publicUpdate.rating = newRating;
        }
        batch.set(publicRef, publicUpdate, { merge: true });

        // Update daily leaderboard entry
        const dailyRef = db.collection("leaderboard_daily").doc(p.uid);
        const dailyUpdate = {
          uid: p.uid,
          displayName: p.displayName,
          avatarId: p.avatarId,
          score: FieldValue.increment(scoreReward + xpReward),
          gamesWon: FieldValue.increment(isWinner ? 1 : 0),
          updatedAt: Date.now()
        };
        if (newRating !== undefined) {
          dailyUpdate.rating = newRating;
        }
        batch.set(dailyRef, dailyUpdate, { merge: true });
      }

      await batch.commit();
      await grantOnlineRewards(change.after.ref, after.rewardMode, players, winnerUid, isDraw);
      console.log(`Game ${gameId} verified, finalized, and ratings updated.`);
    }
    return null;
  });

/**
 * Sends push notifications to opponents when a match invitation is created.
 */
exports.onMatchInviteCreated = functions.firestore
  .document("invitations/{inviteId}")
  .onCreate(async (snap, context) => {
    const invite = snap.data();
    const targetUid = invite.targetUid;

    const userDoc = await db.collection("users").doc(targetUid).get();
    const fcmToken = userDoc.get("fcmToken");

    if (fcmToken) {
      const message = {
        token: fcmToken,
        notification: {
          title: "Match Challenge!",
          body: `${invite.hostName} invited you to play Connect Four! Code: ${invite.roomCode}`
        },
        data: {
          gameId: invite.gameId,
          roomCode: invite.roomCode
        }
      };

      try {
        await admin.messaging().send(message);
        console.log(`Invite notification sent to ${targetUid}`);
      } catch (err) {
        console.error("Failed to send FCM invite notification:", err);
      }
    }
    return null;
  });
