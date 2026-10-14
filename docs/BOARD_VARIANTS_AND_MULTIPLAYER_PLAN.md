# Board Variants and Multiplayer Expansion Plan

## Product goal

Allow players to try more board shapes and support larger multiplayer matches without making the game confusing, slow, or unfair.

The existing engine already stores independent `rows` and `cols`, calculates total boxes and edges from both dimensions, and validates edge bounds using both dimensions. The main limitation is the surrounding product contract: multiplayer creation and matchmaking currently expose one square `boardSize`, and the UI presents square presets only.

## Implementation status

Completed in the first foundation slice:

- Added `BoardSpec` as a shared dimensions, labels, box/edge math, and player-limit contract.
- Added square compatibility presets plus 6 x 6, 3 x 6, 6 x 3, 8 x 6, and 8 x 8 definitions.
- Added unit coverage for rectangular box, edge, dot, and player-limit calculations.

Remaining work:

- Migrate `MultiplayerViewModel` from `selectedBoardSize: Int` to `BoardSpec` while retaining legacy square-ticket reads.
- Replace Firebase matchmaking and game creation fields with `rows`, `cols`, `variantId`, `maxPlayers`, and `rulesVersion`.
- Update Nearby advertising, discovery cards, and payload validation to carry the full specification.
- Replace the square-only board picker with rectangular preset tiles and correct box/dot labels.
- Add 3-4 player lobby slots, turn order, score cards, result handling, reconnect behavior, and rematch rules.
- Update board rendering and AI move generation for large rectangular layouts.
- Run the full device and multiplayer test matrix before enabling large boards by default.

## First fix: define dimensions correctly

The app currently mixes three concepts:

- `rows` and `cols` in the engine represent the number of boxes.
- Dots are therefore `rows + 1` by `cols + 1`.
- Some UI labels currently show `(size - 1) * (size - 1)` boxes even though the model reports `size * size` boxes.

Use this player-facing convention everywhere:

- `3 x 3 boxes` = 9 boxes and 4 x 4 dots.
- `6 x 6 boxes` = 36 boxes and 7 x 7 dots.
- `8 x 6 boxes` = 48 boxes and 9 x 7 dots.
- `3 x 6 boxes` = 18 boxes and 4 x 7 dots.

Do not describe a board as only "grid size". Display `BOXES` and `DOTS` where useful so players understand what they are selecting.

## Recommended board presets

Presets should be data, not hard-coded branches in each screen.

| ID | Boxes | Player label | Style | Multiplayer |
| --- | --- | --- | --- | --- |
| `BLITZ_2X2` | 2 x 2 | Blitz | Fast, beginner-friendly | Yes |
| `CLASSIC_3X3` | 3 x 3 | Classic | Default | Yes |
| `ARENA_4X4` | 4 x 4 | Arena | Standard competitive | Yes |
| `TACTICAL_5X5` | 5 x 5 | Tactical | More planning | Yes |
| `TITAN_6X6` | 6 x 6 | Titan | Large-board multiplayer unlock | Yes |
| `WIDE_3X6` | 3 x 6 | Wide Run | Asymmetric strategy | Yes |
| `TALL_6X3` | 6 x 3 | Tall Run | Asymmetric strategy | Yes |
| `GRAND_8X6` | 8 x 6 | Grand Arena | Large asymmetric board | Yes, 3-4 players |
| `EPIC_8X8` | 8 x 8 | Epic | Maximum supported standard board | Yes, 3-4 players |

The first release should make 6 x 6 and the asymmetric 3 x 6 / 6 x 3 boards available. Add 8 x 6 and 8 x 8 after the board viewport and multiplayer load tests pass.

## Multiplayer rules

### Player counts

- 2 players: every preset.
- 3 players: 6 x 6 and larger only.
- 4 players: 8 x 6 and 8 x 8 only in the first release.
- More than 4 players: defer until turn timing, reconnect behavior, and result UX are proven.

This keeps small boards from becoming random and overly crowded while giving large boards a meaningful reason to exist.

### Board-size eligibility

A match must carry a complete board specification:

```text
BoardSpec {
  rows: Int
  cols: Int
  maxPlayers: Int
  variantId: String
  rulesVersion: Int
}
```

Do not pass a single `boardSize` once rectangular boards ship.

### Matchmaking policy

1. Match exact `variantId`, dimensions, and player-count preference first.
2. After a wait threshold, offer an explicit "expand search" option.
3. Never silently match players onto a different shape.
4. If a player accepts flexible matchmaking, match by compatible area and aspect ratio, then show the final board before the match begins.
5. Keep a stable queue key such as `QUICK_MATCH:8x6:4P` so incompatible tickets cannot be claimed.

For private rooms, the host owns the board specification. The joiner sees the dimensions, box count, player limit, and rules version before accepting.

## Engine and algorithm work

### Turn and scoring model

The current `GameState` already uses a player list and `currentPlayerIndex`, so the engine can be extended to 3-4 players. Verify and test these rules explicitly:

- A player who completes a box keeps the turn.
- A turn advances modulo the active player count when no box is completed.
- `boxOwners` stores player IDs from 1 through N, not only 1 and 2.
- Winner and draw calculations support ties and 3-4 scores.
- Every move records the acting player ID for replay and dispute review.

### Edge and box algorithms

Keep the dimension-aware edge format. Add shared helpers instead of duplicating calculations:

- `BoardSpec.totalBoxes`
- `BoardSpec.totalEdges`
- `BoardSpec.isValidEdge(edgeId)`
- `BoardSpec.boxesCompletedBy(edgeId, claimedEdges)`
- `BoardSpec.neighborsOf(edgeId)`

Use these helpers in the local engine, Firebase validation, Nearby P2P validation, AI move generation, replay, and board rendering.

### AI changes

Large and rectangular boards should not use the current small-board assumptions.

- Use a move generator based on `BoardSpec`, not fixed square loops.
- Score immediate box captures first.
- Add chain-length and sacrifice evaluation for 6 x 6 and larger.
- Limit search depth by time budget, not only by board size.
- Add difficulty-specific budgets, for example 100 ms Beginner, 350 ms Advanced, and 900 ms Ruthless.
- Fall back to a legal random move when the budget expires.
- Add deterministic seeds for tests and replay debugging.

## UI and board rendering

### Board picker

Replace the current square-only selector with a two-stage selector:

1. Preset tiles: Classic, Titan, Wide, Tall, Grand, Epic.
2. Optional custom dimensions for players who want to experiment.

Each tile should show:

- `8 x 6 BOXES`
- `48 BOXES`
- `9 x 7 DOTS`
- player limit
- estimated time: Short, Medium, Long
- a miniature rectangular board preview

The selected tile should use the accent color and a clear check state. Unselected tiles should remain readable but visually quieter.

### Board viewport

For 6 x 6 and larger boards:

- Fit the full board to the available width and height.
- Preserve a minimum edge touch target, using zoom or pan if necessary.
- Keep score and turn status outside the board.
- Avoid overlaying reactions or banners on the board.
- Use a compact status header when the board is constrained.
- Test portrait, landscape, small phones, tablets, and large font settings.

### Multiplayer lobby

Show the board specification before joining:

```text
GRAND ARENA
8 x 6 boxes | 48 boxes | up to 4 players
Long match | Host rules apply
```

For 3-4 player rooms, show connected slots and a clear ready state. Do not start until the host and all required players are ready.

## Firebase and Nearby protocol changes

### Firebase

- Replace `boardSize` fields with `rows`, `cols`, `variantId`, `maxPlayers`, and `rulesVersion`.
- Keep a migration reader that accepts legacy square `boardSize` tickets.
- Write new tickets with the full BoardSpec.
- Make claim transactions validate the complete spec and player capacity.
- Add indexes for queue dimensions and variant where required.
- Include the spec in the game document before any player can submit a move.

### Nearby P2P

- Advertise a versioned compact spec, for example `CONNECTFOUR2|name|rows|cols|variant|maxPlayers`.
- Reject incompatible protocol versions with a clear message.
- Display the host board shape and capacity in the discovered-room card.
- Keep the room list selection flow; no PIN should be required.
- Validate every received move against the host's BoardSpec.

## Limits and performance guardrails

Initial safe product limits:

- Maximum standard board: 8 x 8 boxes.
- Maximum supported players: 4.
- Maximum edges: 144 for 8 x 8.
- Maximum move payload: one edge ID plus version and player ID.
- Large-board match timeout: 30 minutes, with reconnect grace handled separately.

Before increasing limits, measure Compose rendering, Firestore update frequency, local history memory, AI move latency, and reconnect behavior on both test phones.

## Rollout phases

### Phase 1: Correctness and data contract

- Introduce `BoardSpec`.
- Fix box/dot labels.
- Add rectangular engine and anti-cheat tests.
- Preserve legacy square matchmaking reads.

### Phase 2: 6 x 6 and rectangular boards

- Add 6 x 6, 3 x 6, and 6 x 3 presets.
- Enable 2-player large-board online and private rooms.
- Update Nearby advertisement and room cards.
- Add responsive board viewport behavior.

### Phase 3: 3-4 player rooms

- Add lobby slots, ready states, turn order, score cards, results, rematch, and reconnect handling.
- Enable 3 players on 6 x 6 and 4 players on 8 x 6 / 8 x 8.
- Add load and race tests for simultaneous claims and moves.

### Phase 4: 8 x 6 and 8 x 8 experiments

- Enable Grand and Epic presets.
- Add AI time budgets and chain evaluation.
- Run device performance tests before Play release.
- Gate the feature behind a remote configuration flag for staged rollout.

## Test plan

### Unit tests

- Total boxes and edges for square and rectangular boards.
- Every valid and invalid edge boundary.
- Box completion at corners, edges, and internal cells.
- 3-4 player turn retention and score updates.
- Winner and draw calculation with ties.
- AI legal moves and time-budget fallback.

### Multiplayer tests

- Exact BoardSpec matchmaking.
- Legacy square ticket migration.
- Private-room host spec propagation.
- Nearby spec parsing and incompatible-version rejection.
- Atomic claims for 2, 3, and 4 players.
- Duplicate move rejection and version conflicts.
- Reconnect and rematch on large boards.

### UI tests

- Board picker labels match model dimensions.
- 6 x 6, 8 x 6, and 3 x 6 previews render correctly.
- Full board fits or zooms without clipped edges.
- Player cards and result dialog work with 4 players.
- Portrait, landscape, tablet, and large-font layouts.

## Success criteria

- Players can choose square and rectangular boards without misunderstanding dots versus boxes.
- 6 x 6 and larger multiplayer games remain responsive on the supported phones.
- A 3-4 player match has deterministic turn, scoring, reconnect, and result behavior.
- No incompatible board tickets are silently matched.
- Every board variant is covered by engine, anti-cheat, AI, Firebase, Nearby, and UI tests.
