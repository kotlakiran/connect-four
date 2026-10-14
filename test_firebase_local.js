/**
 * Automated Test Suite for Connect Four Firebase Local Stack
 * Tests Firestore, Auth, Functions, and Security Rules against the local emulators.
 */

const path = require("path");
const admin = require(path.join(__dirname, "functions", "node_modules", "firebase-admin"));

const PROJECT_ID = process.env.GCLOUD_PROJECT || "demo-connectfour";
const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080";
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";

process.env.FIRESTORE_EMULATOR_HOST = FIRESTORE_HOST;
process.env.FIREBASE_AUTH_EMULATOR_HOST = AUTH_HOST;

admin.initializeApp({ projectId: PROJECT_ID });
const db = admin.firestore();

let passed = 0;
let failed = 0;

function assert(condition, message) {
  if (condition) {
    console.log(`  ✅ PASS: ${message}`);
    passed++;
  } else {
    console.error(`  ❌ FAIL: ${message}`);
    failed++;
  }
}

const tokenCache = {};
async function getIdToken(uid) {
  if (!uid) return null;
  if (tokenCache[uid]) return tokenCache[uid];

  try {
    const customToken = await admin.auth().createCustomToken(uid);
    const res = await fetch(`http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=mock-api-key`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token: customToken, returnSecureToken: true })
    });
    const authData = await res.json();
    if (authData.idToken) {
      tokenCache[uid] = authData.idToken;
      return authData.idToken;
    }
  } catch (e) {
    // ignore
  }

  // Fallback to base64url encoded unsigned JWT
  const header = Buffer.from(JSON.stringify({ alg: "none", typ: "JWT" })).toString("base64url");
  const payload = Buffer.from(JSON.stringify({
    iss: `https://securetoken.google.com/${PROJECT_ID}`,
    aud: PROJECT_ID,
    auth_time: Math.floor(Date.now() / 1000),
    user_id: uid,
    sub: uid,
    email: `${uid}@example.com`
  })).toString("base64url");
  const mockJwt = `${header}.${payload}.`;
  tokenCache[uid] = mockJwt;
  return mockJwt;
}

// Helper: Make authenticated client REST call against Firestore Emulator to test firestore.rules
async function firestoreClientRequest({ uid, method = "GET", path, body = null }) {
  const url = `http://${FIRESTORE_HOST}/v1/projects/${PROJECT_ID}/databases/(default)/documents/${path}`;
  const headers = { "Content-Type": "application/json" };

  if (uid) {
    const token = await getIdToken(uid);
    headers["Authorization"] = `Bearer ${token}`;
  }

  const options = { method, headers };
  if (body) {
    options.body = JSON.stringify(body);
  }

  const response = await fetch(url, options);
  const data = await response.json().catch(() => null);
  return { status: response.status, ok: response.ok, data };
}

// Convert JS object to Firestore REST document fields
function toFirestoreFields(obj) {
  const fields = {};
  for (const [key, value] of Object.entries(obj)) {
    if (typeof value === "string") {
      fields[key] = { stringValue: value };
    } else if (typeof value === "number") {
      fields[key] = Number.isInteger(value) ? { integerValue: String(value) } : { doubleValue: value };
    } else if (typeof value === "boolean") {
      fields[key] = { booleanValue: value };
    } else if (Array.isArray(value)) {
      fields[key] = { arrayValue: { values: value.map(v => ({ stringValue: String(v) })) } };
    } else if (value && typeof value === "object") {
      fields[key] = { mapValue: toFirestoreFields(value) };
    }
  }
  return { fields };
}

async function runTests() {
  console.log("==================================================");
  console.log("🔥 CONNECT FOUR FIREBASE LOCAL STACK COMPREHENSIVE TESTS");
  console.log("==================================================");
  console.log(`Connecting to Firestore: ${FIRESTORE_HOST}`);
  console.log(`Connecting to Auth: ${AUTH_HOST}\n`);

  // 1. Connectivity Check
  console.log("--- 1. Service Connectivity ---");
  try {
    const fsCheck = await fetch(`http://${FIRESTORE_HOST}/`);
    assert(fsCheck.status === 200 || fsCheck.status === 404, "Firestore Emulator responding on port 8080");
  } catch (e) {
    assert(false, `Firestore Emulator unreachable: ${e.message}`);
  }

  try {
    const authCheck = await fetch(`http://${AUTH_HOST}/`);
    assert(authCheck.status === 200 || authCheck.status === 404, "Auth Emulator responding on port 9099");
  } catch (e) {
    assert(false, `Auth Emulator unreachable: ${e.message}`);
  }

  // 2. Admin SDK Operations
  console.log("\n--- 2. Admin SDK Operations ---");
  const testDocRef = db.collection("system_tests").doc("connectivity");
  await testDocRef.set({ status: "ONLINE", timestamp: Date.now() });
  const testSnap = await testDocRef.get();
  assert(testSnap.exists && testSnap.data().status === "ONLINE", "Admin SDK write and read successful");

  // 3. Security Rules Testing (Client Calls)
  console.log("\n--- 3. Firestore Security Rules Enforcement ---");

  const player1Uid = "p1_alice";
  const player2Uid = "p2_bob";
  const intruderUid = "p3_intruder";
  const testGameId = `game_${Date.now()}`;

  // 3.1 Unauthenticated requests must be denied
  const unauthReq = await firestoreClientRequest({
    uid: null,
    method: "POST",
    path: `games?documentId=unauth_game`,
    body: toFirestoreFields({
      gameId: "unauth_game",
      status: "WAITING",
      createdBy: "anonymous"
    })
  });
  assert(unauthReq.status === 403, "Unauthenticated game creation is rejected (403 Forbidden)");

  // 3.2 Authenticated Game Creation with status WAITING
  const validGameReq = await firestoreClientRequest({
    uid: player1Uid,
    method: "POST",
    path: `games?documentId=${testGameId}`,
    body: toFirestoreFields({
      gameId: testGameId,
      status: "WAITING",
      createdBy: player1Uid,
      playerUids: [player1Uid],
      version: 1,
      rows: 3,
      cols: 3
    })
  });
  assert(validGameReq.ok, "Authenticated player can create game with status 'WAITING'");

  // 3.3 Game Creation with illegal status (e.g. PLAYING directly) must be denied
  const illegalStatusReq = await firestoreClientRequest({
    uid: player1Uid,
    method: "POST",
    path: `games?documentId=illegal_${Date.now()}`,
    body: toFirestoreFields({
      gameId: "illegal_game",
      status: "PLAYING",
      createdBy: player1Uid,
      playerUids: [player1Uid],
      version: 1
    })
  });
  assert(illegalStatusReq.status === 403, "Creating game with initial status 'PLAYING' is rejected by security rules");

  // 3.4 Joining open lobby: Player 2 joins WAITING game, transitioning to MATCHED
  const joinReq = await firestoreClientRequest({
    uid: player2Uid,
    method: "PATCH",
    path: `games/${testGameId}?updateMask.fieldPaths=status&updateMask.fieldPaths=playerUids`,
    body: toFirestoreFields({
      status: "MATCHED",
      playerUids: [player1Uid, player2Uid]
    })
  });
  assert(joinReq.ok, "Second player can transition game status from WAITING to MATCHED");

  // 3.5 Active gameplay move: Version must increment monotonically
  const moveReq = await firestoreClientRequest({
    uid: player1Uid,
    method: "PATCH",
    path: `games/${testGameId}?updateMask.fieldPaths=version&updateMask.fieldPaths=currentTurn`,
    body: toFirestoreFields({
      version: 2, // 1 -> 2 (valid)
      currentTurn: player2Uid
    })
  });
  assert(moveReq.ok, "Participant can update game state with monotonic version increment (+1)");

  // 3.6 Non-monotonic version increment (+5) must be rejected
  const skipVersionReq = await firestoreClientRequest({
    uid: player2Uid,
    method: "PATCH",
    path: `games/${testGameId}?updateMask.fieldPaths=version`,
    body: toFirestoreFields({
      version: 7 // 2 -> 7 (illegal skip)
    })
  });
  assert(skipVersionReq.status === 403, "Skipping version numbers (+5) is rejected by security rules");

  // 3.7 Non-participant cannot update the game
  const intruderReq = await firestoreClientRequest({
    uid: intruderUid,
    method: "PATCH",
    path: `games/${testGameId}?updateMask.fieldPaths=version`,
    body: toFirestoreFields({
      version: 3
    })
  });
  assert(intruderReq.status === 403, "Non-participant cannot update the game session");

  // 3.8 Rematch link: Participants can set rematchGameId with version + 1
  const rematchReq = await firestoreClientRequest({
    uid: player1Uid,
    method: "PATCH",
    path: `games/${testGameId}?updateMask.fieldPaths=rematchGameId&updateMask.fieldPaths=version`,
    body: toFirestoreFields({
      rematchGameId: `match_rematch_${Date.now()}`,
      version: 3
    })
  });
  assert(rematchReq.ok, "Participant can link rematchGameId with version + 1");

  // 3.9 Profile Protection: User cannot tamper with isAdmin
  await firestoreClientRequest({
    uid: player1Uid,
    method: "POST",
    path: `users?documentId=${player1Uid}`,
    body: toFirestoreFields({
      displayName: "Alice",
      score: 0
    })
  });
  // Update with isAdmin denied
  const adminUpdateReq = await firestoreClientRequest({
    uid: player1Uid,
    method: "PATCH",
    path: `users/${player1Uid}?updateMask.fieldPaths=isAdmin`,
    body: toFirestoreFields({
      isAdmin: true
    })
  });
  assert(adminUpdateReq.status === 403, "User cannot elevate own privileges (isAdmin forbidden in security rules)");

  // 3.10 Public Profile Protection: Cannot expose email
  const publicEmailReq = await firestoreClientRequest({
    uid: player1Uid,
    method: "POST",
    path: `public_profiles?documentId=${player1Uid}`,
    body: toFirestoreFields({
      displayName: "Alice",
      email: "secret@example.com"
    })
  });
  assert(publicEmailReq.status === 403, "Public profile cannot contain email attribute");

  // 3.11 Leaderboards are client-read-only
  const clientLeaderboardReq = await firestoreClientRequest({
    uid: player1Uid,
    method: "POST",
    path: `leaderboards?documentId=weekly`,
    body: toFirestoreFields({
      tampered: true
    })
  });
  assert(clientLeaderboardReq.status === 403, "Direct client writes to leaderboards collection are forbidden");

  // 3.12 Quick Reaction Event in game subcollection
  const reactionReq = await firestoreClientRequest({
    uid: player1Uid,
    method: "POST",
    path: `games/${testGameId}/events?documentId=reaction_test_1`,
    body: toFirestoreFields({
      eventId: "reaction_test_1",
      gameId: testGameId,
      playerId: player1Uid,
      type: "REACTION",
      payload: { reactionId: "fire" },
      timestamp: Date.now(),
      version: 1
    })
  });
  assert(reactionReq.status === 200, "Participant can send quick emote reaction via game events subcollection");

  // 4. Cloud Functions Test (validateAndCompleteGame)
  console.log("\n--- 4. Cloud Function Execution (validateAndCompleteGame) ---");
  const funcGameId = `func_game_${Date.now()}`;
  const funcGameRef = db.collection("games").doc(funcGameId);

  await funcGameRef.set({
    gameId: funcGameId,
    status: "PLAYING",
    createdBy: player1Uid,
    playerUids: [player1Uid, player2Uid],
    version: 10,
    startedAt: Date.now() - 30000
  });

  await funcGameRef.collection("players").doc(player1Uid).set({
    uid: player1Uid,
    displayName: "Alice",
    avatarId: "bolt",
    score: 8,
    ready: true
  });

  await funcGameRef.collection("players").doc(player2Uid).set({
    uid: player2Uid,
    displayName: "Bob",
    avatarId: "mavi",
    score: 5,
    ready: true
  });

  console.log("  Triggering game completion (status: PLAYING -> COMPLETED)...");
  await funcGameRef.update({
    status: "COMPLETED",
    version: 11
  });

  // Allow Cloud Function up to 4s to process background Firestore trigger
  await new Promise(r => setTimeout(r, 3500));

  const completedSnap = await funcGameRef.get();
  const completedData = completedSnap.data();
  assert(
    completedData.winnerId === player1Uid,
    `Cloud Function calculated authoritative winner: ${completedData.winnerId} (expected ${player1Uid})`
  );
  assert(
    completedData.verifiedAt !== undefined,
    "Cloud Function marked game with authoritative verifiedAt timestamp"
  );
  assert(
    completedData.ratings && completedData.ratings[player1Uid] > 1200,
    `Cloud Function calculated authoritative winner Elo rating: ${completedData.ratings?.[player1Uid]} (> 1200)`
  );
  assert(
    completedData.ratings && completedData.ratings[player2Uid] < 1200,
    `Cloud Function calculated authoritative loser Elo rating: ${completedData.ratings?.[player2Uid]} (< 1200)`
  );

  // Check that daily leaderboard was populated by Cloud Function
  const dailyDoc = await db.collection("leaderboard_daily").doc(player1Uid).get();
  assert(
    dailyDoc.exists && dailyDoc.data().displayName === "Alice",
    "Cloud Function updated daily leaderboard entry for winning player"
  );
  assert(
    dailyDoc.exists && dailyDoc.data().rating > 1200,
    `Cloud Function populated daily leaderboard with authoritative Elo rating: ${dailyDoc.data()?.rating}`
  );

  console.log("\n==================================================");
  console.log(`📊 TEST RESULTS: ${passed} Passed, ${failed} Failed`);
  console.log("==================================================");

  if (failed > 0) {
    process.exit(1);
  } else {
    process.exit(0);
  }
}

runTests().catch(err => {
  console.error("Test execution fatal error:", err);
  process.exit(1);
});
