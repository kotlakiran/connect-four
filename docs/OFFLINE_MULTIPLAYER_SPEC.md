# Connect Four — Offline / Local Multiplayer Design Spec

Status: Draft v1 · Owner: @kotlakiran · Last updated: 2026-10-05

## 1. Goal

Let two phones play a live Connect Four match **with no internet**, over:

- **Bluetooth** (phones near each other, no network), and
- **Same Wi‑Fi / LAN** (phones on one router or a phone hotspot).

The full match is playable fully offline. When either device is back online, the
finished match (result, XP, stats, replay) **syncs to Firebase asynchronously** —
the game never blocks on the network.

### Non-goals (this spec)
- Cross-internet realtime play between remote players (that is the existing
  `FirebaseMultiplayerRepository` online path — unchanged here).
- Spectators, voice chat, 3+ player local matches (future).
- Tournaments / matchmaking over LAN.

## 2. Guiding principles

1. **Offline-first.** Local play has zero Firebase dependency at runtime. If
   Firebase is never reachable, the game and all progression still work.
2. **Reuse the existing engine.** The authoritative game rules stay in
   `rules/GameEngine.kt`. Local multiplayer is a *transport*, not a new rules fork.
3. **One session contract.** Reuse the online model vocabulary
   (`OnlineGameEvent`, `OnlineGameStatus`, `MultiplayerStateMachine`) so online
   and local share the same ViewModel/UI surface where possible.
4. **Host-authoritative.** One device is the host (source of truth); the guest
   sends intents and renders host-confirmed state. Mirrors the existing
   server-authoritative Firebase design and keeps `AntiCheatEngine` relevant.
5. **Deferred, idempotent sync.** Each finished match has a stable `matchId`;
   re-syncing is a no-op (upsert). Both devices may sync the same match safely.

## 3. Transport choice

### Primary: Google **Nearby Connections API** (`play-services-nearby`)
Nearby Connections already abstracts **Bluetooth, BLE, and Wi‑Fi (incl. Wi‑Fi
Direct / hotspot)** behind one API and auto-selects the best radio. Using
`Strategy.P2P_POINT_TO_POINT` gives a single reliable 1:1 byte stream for exactly
two players — which is what Connect Four needs. This covers *both* requested transports
(Bluetooth + same Wi‑Fi) with one code path.

- Pros: one API for BT + LAN, encrypted channel, handles discovery/handshake,
  survives radio switching, no manual socket code.
- Cons: depends on Google Play services (fine — the app already uses
  `play-services-auth`).

### Fallback / future: raw sockets
If a Play-services-free path is ever needed, add a `LanSocketTransport`
(host opens a `ServerSocket`; discovery via NSD/mDNS `_connectfour._tcp`) and a
`BluetoothRfcommTransport` (classic RFCOMM, fixed app UUID). These implement the
same `LocalTransport` interface, so the rest of the stack is untouched.

Decision: **ship Nearby Connections first**; keep the `LocalTransport` interface
clean so sockets can slot in later without touching game/UI code.

## 4. Architecture

```
ui/ (Compose)            LocalLobbyScreen, LocalGameScreen (reuse GameScreen board)
   │
viewmodel/               LocalMatchViewModel  ── drives GameEngine, owns session
   │
multiplayer/local/
   ├─ LocalMultiplayerRepository (interface)   ← mirrors MultiplayerRepository
   ├─ NearbyLocalRepository (impl)             ← Nearby Connections
   ├─ LocalTransport (interface)               ← send/receive framed messages
   │    ├─ NearbyTransport (impl, phase 1)
   │    ├─ LanSocketTransport (impl, later)
   │    └─ BluetoothRfcommTransport (impl, later)
   ├─ LocalSessionRole { HOST, GUEST }
   └─ wire/LocalMessage (sealed) + codec (JSON)
   │
rules/GameEngine.kt      authoritative move application (unchanged)
   │
data/ (Room)             SavedGameEntity (resume), MatchSummary/Replay (sync queue)
   │
sync/
   ├─ PendingSyncEntity + dao            ← queue of finished matches awaiting upload
   └─ MatchSyncWorker (WorkManager)      ← flushes queue when online (idempotent)
```

Key idea: `LocalTransport` moves **bytes**; `NearbyLocalRepository` turns bytes
into `LocalMessage`s and feeds confirmed moves into `GameEngine`, exposing the
same `StateFlow<GameState>` the UI already consumes.

## 5. Session lifecycle

Reuse `OnlineGameStatus` + `MultiplayerStateMachine` with a local mapping:

| Phase | Status | Host action | Guest action |
|-------|--------|-------------|--------------|
| Advertise/Discover | WAITING | `startAdvertising()` | `startDiscovery()` |
| Connected | MATCHED | accept connection | request connection |
| Lobby (board size, colors, who starts) | READY | broadcast `LobbyConfig`, both tap Ready | mark ready |
| In play | PLAYING | authoritative `applyMove` | send `MoveIntent`, render `StateSync` |
| Pause (disconnect) | PAUSED | buffer + show "reconnecting" | attempt re-advertise/rediscover |
| Finished | COMPLETED → RESULTS | compute result, enqueue sync | enqueue sync |

`MultiplayerStateMachine.assertValidTransition` already guards illegal jumps —
local flow reuses it verbatim.

## 6. Message protocol (`LocalMessage`)

Framed JSON over the transport (small, human-debuggable; swap to protobuf later
if needed). Every message carries `protocolVersion` and monotonically increasing
`seq` for ordering/dedup.

```kotlin
sealed interface LocalMessage {
    val protocolVersion: Int   // bump on breaking change; handshake rejects mismatch
    val seq: Long

    data class Hello(...)        : LocalMessage  // app version, player profile, role claim
    data class LobbyConfig(...)  : LocalMessage  // rows, cols, turnRule, colors, firstPlayer (HOST only)
    data class Ready(...)        : LocalMessage
    data class MoveIntent(...)   : LocalMessage  // GUEST → HOST: edgeId
    data class StateSync(...)    : LocalMessage  // HOST → GUEST: authoritative GameState delta/snapshot + version
    data class Heartbeat(...)    : LocalMessage  // presence ping (reuses sendPresencePing cadence)
    data class Resign(...)       : LocalMessage
    data class Rematch(...)      : LocalMessage
    data class Bye(...)          : LocalMessage  // graceful leave
}
```

Rules:
- **Only the host applies moves** via `GameEngine.applyMove`. Guest `MoveIntent`
  is validated host-side (legal edge, correct turn) before applying — this is
  where `AntiCheatEngine` hooks in for local too.
- Host replies with `StateSync` (authoritative). Guest UI never mutates
  `GameState` locally except optimistic highlight, reconciled on next `StateSync`.
- `seq` dedups re-sent messages after a radio blip. Out-of-order `MoveIntent`
  with stale `version` is rejected.

## 7. Offline persistence & resume

- On every confirmed move, host writes a `SavedGameEntity` snapshot (same resume
  mechanism as single-player). **Fix carried in:** `saveSnapshot` must persist a
  real serialized `GameState` into `stateJson` (currently empty) so a local match
  can resume after the app is killed. Guest stores the latest `StateSync`.
- Finished match → write `MatchSummaryEntity` + `MatchReplayEntity` **and** a new
  `PendingSyncEntity(matchId, payloadJson, createdAt)` row.

## 8. Deferred Firebase sync

```
sync/PendingSyncEntity         (matchId PK, payloadJson, status, attempts, createdAt)
sync/MatchSyncWorker           (CoroutineWorker, constraint: NETWORK_CONNECTED)
```

- `NetworkMonitor.isConnected` (already exists) → when it flips to `true`,
  enqueue `MatchSyncWorker` (unique work, `ExistingWorkPolicy.KEEP`).
- Worker reads pending rows, upserts each to Firestore under
  `matches/{matchId}` (merge) — **idempotent**, so both host and guest can push
  the same `matchId` without creating duplicates. On success, delete the row.
- Leaderboard/stat deltas are derived server-side-safe: write the raw match result
  and let existing aggregation handle totals; never double-count because `matchId`
  is the dedup key.
- Auth: sync requires a Firebase user. If the player is anonymous/not signed in,
  rows stay queued until sign-in (reuse `AuthRepository`). Local play itself never
  requires auth.

### Conflict resolution
Local matches are **append-only results**, not shared mutable state, so there is
no true merge conflict — last-writer-upsert on `matchId` is correct. Profile XP is
applied locally via `ProgressionProcessor` at match end (offline), and the Firebase
profile doc is reconciled with `max(localXp, remoteXp)` on next sign-in to avoid
regressions across devices.

## 9. Permissions & manifest

Android 12+ (API 31+) runtime permissions, requested **just before** discovery
with an in-context rationale:

- `BLUETOOTH_SCAN` (`usesPermissionFlags="neverForLocation"`)
- `BLUETOOTH_ADVERTISE`
- `BLUETOOTH_CONNECT`
- `NEARBY_WIFI_DEVICES` (`usesPermissionFlags="neverForLocation"`, API 33+)
- `ACCESS_FINE_LOCATION` (only maxSdk 30, for legacy BT/Wi‑Fi scan)
- `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `ACCESS_NETWORK_STATE`
- `INTERNET` (already present via Firebase; used only by the sync worker)

Degrade gracefully: if the user denies nearby/BT permissions, show a clear
"Local play needs Bluetooth/Wi‑Fi permission" screen with a Settings deep link;
single-player and online remain usable.

## 10. Dependencies (gradle/libs.versions.toml)

```toml
[versions]
playServicesNearby = "19.3.0"

[libraries]
play-services-nearby = { group = "com.google.android.gms", name = "play-services-nearby", version.ref = "playServicesNearby" }
```

WorkManager (for the sync worker):

```toml
[versions]
workManager = "2.9.1"
[libraries]
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
```

## 11. UI flows

1. **Home → "Play Local"** new entry alongside existing modes.
2. **Role picker:** *Host a game* / *Join a game*.
3. **Host:** shows a 4‑char room code + "Looking for a player nearby…"
   (advertising). Works over BT or same Wi‑Fi automatically.
4. **Join:** list of discovered hosts (name + avatar) or enter code; tap to
   connect. Connection handshake verifies `protocolVersion`.
5. **Lobby:** host sets board size / turn rule / colors; both tap **Ready**.
6. **Game:** reuse `GameScreen` board; a small "📶 Local • Connected" chip with
   the peer's avatar; "reconnecting…" overlay on `PAUSED`.
7. **Results:** normal results screen + a subtle "Will sync when online" note if
   offline; "Rematch" re-enters lobby over the same transport.

## 12. Reliability

- **Heartbeat** every ~3s; miss 3 → `PAUSED` + reconnect attempt (host keeps
  advertising, guest keeps discovering) for up to 60s, then offer
  "End match (counts as resign?)".
- **Host migration:** out of scope v1 — if the host leaves, the match ends and is
  recorded as a disconnect (no rating penalty for local).
- **Backgrounding:** persist snapshot; Nearby connection may drop when backgrounded
  — treat as `PAUSED` and reconnect on resume.

## 13. Security / anti-cheat (local)

- Host validates every guest `MoveIntent` (legal edge + correct turn + fresh
  version) through the same checks as online; route through `AntiCheatEngine`.
- `protocolVersion` handshake prevents mismatched/tampered clients from desyncing.
- No secrets on the wire; payloads are just moves/state. Nearby's channel is
  encrypted.
- Local results are lower-trust than online: tag synced local matches with
  `source = "LOCAL_P2P"` so leaderboards/ranked can weight or exclude them.

## 14. Phased implementation plan

- **Phase 0 — Scaffolding:** add deps; create `multiplayer/local/` package;
  `LocalTransport` interface + `LocalMessage` sealed types + JSON codec; unit
  tests for codec round-trip and `MultiplayerStateMachine` local mapping.
- **Phase 1 — Nearby transport:** `NearbyTransport` (advertise/discover/connect/
  send/receive) + permission flow; a bare "connected" screen that exchanges
  `Hello`. Manual 2-device smoke test over BT and over Wi‑Fi.
- **Phase 2 — Host-authoritative game:** `NearbyLocalRepository` wiring
  `MoveIntent`/`StateSync` into `GameEngine`; `LocalMatchViewModel`; reuse
  `GameScreen`. Full match playable offline on two devices.
- **Phase 3 — Persistence & resume:** real `stateJson` snapshots; resume a local
  match after process death; reconnect/heartbeat/PAUSED handling.
- **Phase 4 — Deferred sync:** `PendingSyncEntity` + `MatchSyncWorker`; idempotent
  Firestore upsert keyed by `matchId`; `NetworkMonitor`-triggered flush; XP
  reconciliation on sign-in.
- **Phase 5 — Polish:** lobby options, rematch, discovered-host list UI, denied-
  permission screens, `source = LOCAL_P2P` tagging, analytics events.
- **Phase 6 (optional) — Socket fallback:** `LanSocketTransport` (NSD) +
  `BluetoothRfcommTransport` behind the same interface.

## 15. Testing

- **Unit:** `LocalMessage` codec round-trip; state-machine transitions; host-side
  move validation (illegal/stale/ out-of-turn rejected); sync queue idempotency
  (same `matchId` twice → one Firestore doc).
- **Instrumented (2 devices / 2 emulators):** connect over Wi‑Fi; connect over BT
  with Wi‑Fi off; full match end-to-end; kill guest mid-match → resume; airplane
  mode during results → later sync flush uploads exactly once.
- **Manual matrix:** BT-only, same-router Wi‑Fi, host-hotspot Wi‑Fi; Android 12 /
  13 / 14 permission prompts.

## 16. Open questions

- Do local matches count toward ranked/leaderboard, or stats-only? (Default:
  stats + XP yes, ranked no — `source` tag enables the policy.)
- Allow a guest with no Firebase account to play local, then prompt sign-in to
  sync? (Default: yes — local never requires auth.)
- Snapshot vs. delta in `StateSync` (default: snapshot for small boards; add delta
  if payloads grow on large grids).

---
*This is a design doc only; no runtime code changes are included in this commit.*
