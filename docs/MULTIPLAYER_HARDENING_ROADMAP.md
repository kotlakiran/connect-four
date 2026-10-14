# Connect Four — Multiplayer Hardening & Competitive Roadmap

Status: Draft v1 · Owner: @kotlakiran · Last updated: 2026-10-05

Priority order: **(1) reliability & security first**, then (2) competitive play,
then (3) retention. (4) foundations run in parallel. Each item is tagged with its
real current state in this repo:

- ✅ **DONE** — already in HEAD
- 🟡 **PARTIAL** — scaffolding exists, gaps remain
- ⬜ **TODO** — not started

Grounding (verified in HEAD):
`multiplayer/engine/AntiCheatEngine.kt`, `multiplayer/engine/MultiplayerStateMachine.kt`,
`multiplayer/repository/{Firebase,Default,Simulated}MultiplayerRepository.kt`,
`multiplayer/model/OnlineModels.kt`, `network/NetworkMonitor.kt`,
`firestore.rules`, `firebase.json` (emulators configured), `gradlew` (wrapper present),
`data/ConnectFourDatabase.kt` (version=2, `fallbackToDestructiveMigration`).

---

## 1. Harden multiplayer (HIGHEST PRIORITY)

### 1.1 Reconnection & resume after app restart — 🟡 PARTIAL
- `MultiplayerStateMachine` already models `PLAYING ⇄ PAUSED`; `NetworkMonitor`
  already exposes `isConnected`. `OnlinePlayerDoc` has `connected` + `lastPingAt`.
- Gaps:
  - On cold start, re-attach to an in-progress `games/{gameId}` if the local
    player is still in `playerUids` and `status == PLAYING`; restore the board
    from the authoritative `OnlineGameDoc` (claimedEdges/boxOwners/version).
  - Persist the active `gameId` locally (DataStore) so resume survives process death.
  - Drive `PAUSED` from presence: if opponent `lastPingAt` is stale > N s, show
    "Opponent reconnecting…"; auto-resume to `PLAYING` on fresh ping.
  - Reconnect backoff for the writer (retry `submitMove`/presence on transient
    Firestore errors).
- Acceptance: kill either app mid-match → relaunch → board + turn restored; opponent
  drop → PAUSED overlay → auto-resume within a few seconds of their return.

### 1.2 Lobby expiry & opponent-disconnect handling — ⬜ TODO
- Add `expiresAt` to lobby/matchmaking docs; a client-side sweep (and a scheduled
  Cloud Function) cancels `WAITING` lobbies older than the TTL (e.g., 2 min).
- `cancelMatchmaking()` exists on the repo interface — wire a visible timeout in
  the matchmaking UI ("No players found — try again").
- Disconnect during `PLAYING`: after grace period, offer the connected player
  "Claim win (opponent left)" → transition to `COMPLETED` with a `DISCONNECT`
  result reason; record it so stats/ranking can treat it fairly.
- Acceptance: stale lobbies auto-clear; a mid-match abandon resolves deterministically.

### 1.3 Server-authoritative move validation — 🟡 PARTIAL
- `AntiCheatEngine.validateMove` already enforces: game in `PLAYING`, correct
  `currentTurn`, **monotonic `version`**, edge-not-claimed, edge-in-bounds; and
  `applyAuthoritativeMove` computes boxes/score/turn/winner authoritatively.
- `firestore.rules` already requires `version == resource.data.version + 1` and
  participant-only updates. **But this logic currently runs client-side.**
- Gap (the real hardening): move the authoritative apply to a **Cloud Function**
  (callable `submitMove`) so clients cannot write arbitrary board state. Rules
  then restrict `games/{id}` board-field writes to the Admin SDK only; clients
  write just their intent (an `events/{id}` doc) and read the function's result.
- `firebase.json` already declares a `functions` codebase — add the function there.
- Acceptance: a tampered client (wrong turn, skipped version, illegal edge,
  forged score) is rejected server-side; only the function mutates board state.

### 1.4 Firestore security-rule testing — ⬜ TODO
- `firebase.json` already configures the emulator suite (auth/firestore/functions).
- Add `@firebase/rules-unit-testing` tests (Node) covering:
  - unauthenticated reads/writes denied;
  - non-participant cannot update a game;
  - version must increment by exactly 1;
  - `public_profiles` cannot contain `email`/`token`/`billing`;
  - users cannot flip `isAdmin`/`verifiedWinner`;
  - leaderboards are client-read-only.
- Wire `firebase emulators:exec "npm test"` into CI.
- Acceptance: rules test suite green in the emulator; CI fails on rule regressions.

---

## 2. Improve competitive play

### 2.1 Rematch button — 🟡 PARTIAL
- `OnlineGameEvent` is a generic event channel; the local spec already defines a
  `Rematch` message. Online: on `RESULTS`, either player taps Rematch → create a
  new `games` doc reusing the same `playerUids`, swap first turn, both re-ready.
- Acceptance: one tap from results back into a new match with the same opponent.

### 2.2 Friend / invite links — 🟡 PARTIAL
- Repo already has `getFriends`, `addFriend`, `inviteFriend`, `acceptInvite`,
  `searchPlayers`; `GameInvite` model + `invitations/` rules exist.
- Gaps: deep links (`https://connectfour.app/invite?room=CODE` + custom scheme) that
  open the app straight into `joinPrivateGame(roomCode)`; share-sheet for the link;
  App Links verification + manifest intent-filter.
- Acceptance: tapping a shared link installs/opens and lands in the host's lobby.

### 2.3 Matchmaking by skill — ⬜ TODO
- Add a rating field (see 2.4) to `public_profiles`; matchmaking tickets carry the
  rating and match within an expanding band over wait time. Do the pairing in a
  Cloud Function to avoid client trust.
- Acceptance: players are paired within a bounded rating window that widens on wait.

### 2.4 Ranked & casual modes + rankings — ⬜ TODO
- Introduce an Elo/Glicko rating updated **only** by the authoritative match
  function; store `ratingCasual`/`ratingRanked` separately. Casual does not affect
  ranked rating.
- Leaderboard docs already exist (`leaderboards`, `leaderboard_{daily,weekly,
  monthly}`, write: false). Populate them from the match function / scheduled
  aggregation.
- Tag match `source` (`ONLINE_RANKED` / `ONLINE_CASUAL` / `LOCAL_P2P`) so local
  P2P matches never feed ranked.
- Acceptance: ranked wins/losses move rating; leaderboards reflect it; casual/local don't.

### 2.5 Seasonal leagues — ⬜ TODO
- Season = time-boxed rating snapshot + tier (Bronze…Diamond); scheduled function
  rolls the season, archives standings, grants season rewards, soft-resets rating.
- Acceptance: a season boundary produces final standings + rewards and starts fresh.

---

## 3. Polish retention

### 3.1 Daily challenges — ⬜ TODO
- Deterministic daily seed (date-based) → fixed board/opponent/AI difficulty;
  one scored attempt/day; store in a `daily_challenge` table + Firestore doc for
  a daily leaderboard. Playable offline; result syncs via the deferred-sync queue.

### 3.2 Multiplayer achievements — 🟡 PARTIAL
- `AchievementEntity` + `AchievementCatalog` + achievements package already exist
  for local play. Add online-specific achievements (first online win, win vs higher
  rating, win streak of N online, comeback win) evaluated by the match function or
  on result ingest.

### 3.3 Win streaks & rewards — 🟡 PARTIAL
- `PlayerStatsEntity` already tracks `currentStreak`/`bestStreak`; `ProgressionProcessor`
  already updates them and awards XP/stars. Add streak-milestone rewards (cosmetics/
  XP multipliers) and surface streak prominently (Home already shows 🔥 streak).

### 3.4 Match history with replay / share — 🟡 PARTIAL
- `MatchSummaryEntity` + `MatchReplayEntity` (move log + checksum) already exist and
  are written on completion. Gaps: a replay **player UI** (step through `moveLogJson`
  over the board), and share (export result card / replay link).
- Note: fix `GameViewModel.saveSnapshot` to persist real `stateJson` (currently
  empty) so single-player/local matches can resume and feed replays.

### 3.5 Push notifications (invites & turns) — 🟡 PARTIAL
- `firebase-messaging` is already a dependency and a `notifications` package exists.
- Add FCM data messages for: incoming `GameInvite`, "it's your turn" in async play,
  rematch offers. Register token per user; send from the match/invite functions.
- Android 13+ `POST_NOTIFICATIONS` runtime permission flow.

---

## 4. Strengthen reliability (parallel track)

### 4.1 Gradle wrapper — ✅ DONE
- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.{jar,properties}` are
  present in HEAD. (Earlier "missing wrapper" note is now resolved.)
- Remaining nicety: pin/verify the wrapper distribution checksum.

### 4.2 Multiplayer integration tests — 🟡 PARTIAL
- `AntiCheatEngineTest`, `GameEngineTest`, `ProgressionProcessorTest`, `AiPlayerTest`
  exist (unit). Add integration tests against the **Firestore emulator** driving
  `FirebaseMultiplayerRepository` through a full session: create → join → ready →
  alternating `submitMove` with version increments → completion → results.
- Also unit-test `MultiplayerStateMachine` transition matrix exhaustively.

### 4.3 Emulator smoke tests (login, lobby, full match) — ⬜ TODO
- Instrumented (Compose UI) or scripted E2E: anonymous/login → create or quick-match
  lobby → both ready → play to completion → results screen. Run two emulators (or
  the `SimulatedMultiplayerRepository`) for the opponent.
- Gate releases on this smoke path.

### 4.4 Replace destructive Room migrations — ⬜ TODO
- `ConnectFourDatabase` is `version = 2` with `fallbackToDestructiveMigration()` — any
  schema change **wipes local profile/stats/history/achievements**. Replace with
  explicit `Migration` objects and `.addMigrations(...)`, add a Room schema-export
  dir + `MigrationTestHelper` test. Do this **before** adding columns for ratings/
  seasons/daily challenges (sections 2–3), which all need schema changes.
- Acceptance: upgrading across versions preserves all local data; migration test passes.

---

## Suggested execution sequence

1. **1.3 + 1.4** (Cloud Function authoritative moves + rules tests) — closes the
   biggest security gap; everything competitive depends on trusted results.
2. **4.4** (real Room migrations) — unblocks schema changes safely.
3. **1.1 + 1.2** (reconnect/resume + lobby expiry/disconnect) — reliability.
4. **4.2 + 4.3** (integration + emulator smoke) — lock in the above with tests.
5. **2.1 → 2.2 → 2.3 → 2.4 → 2.5** (rematch → invites → skill MM → ranked → seasons).
6. **3.x** retention features, each using the deferred-sync queue from the offline
   multiplayer spec so they work offline-first.

> Do **reliability & security (sections 1 and 4)** before new modes or monetization.

See also: [OFFLINE_MULTIPLAYER_SPEC.md](OFFLINE_MULTIPLAYER_SPEC.md) for the
Bluetooth / same‑Wi‑Fi local play + deferred Firebase sync design.

---
*Design/roadmap doc only; no runtime code changes in this commit.*
