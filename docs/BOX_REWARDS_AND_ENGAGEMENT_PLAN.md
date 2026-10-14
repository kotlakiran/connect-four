# Connect Four — Box Rewards & Engagement Upgrade Plan

Status: Draft v1 · Owner: @kotlakiran · Last updated: 2026-10-07

## Goal

Make completing a box feel rewarding and collectible. When a player closes a box,
something can "pop out" of it — coins, gems, jokers, or power-ups — backed by a
reward-model system that drives daily return, collection, and progression. Keep
the existing deterministic rules, scoring, AI, anti-cheat, and multiplayer intact.

## Design principles

- **Rewards are cosmetic/meta, never rule-changing in ranked online play.** Box
  pop rewards affect the meta economy (coins, collectibles), not the board
  outcome, so online fairness and `AntiCheatEngine` stay untouched.
- **Jokers / power-ups are an opt-in mode**, not default competitive play. Ship
  them first in local and campaign modes, then an explicitly labelled "Power-Up"
  online queue — never in Ranked.
- **Deterministic + seeded.** Reward rolls use a per-match seed so replays and
  tests stay reproducible.
- **Reuse what exists.** Build on `GameEngine.applyMove` box completion,
  `ProgressionProcessor` / `MatchResultReward`, `AchievementCatalog`, and the
  existing `CelebratoryParticleOverlay` and box-fill animation in `ConnectFourBoard`.

## Grounding (verified in HEAD)

- Box completion: `rules/GameEngine.kt` → `findNewlyCompletedBoxes` + `applyMove`
  (produces `newlyCompletedBoxes`, `MoveRecord.claimedBoxIds`).
- State: `model/GameState.kt` (`boxOwners`, `moveHistory`).
- Progression: `data/ProgressionProcessor.kt` (`MatchResultReward`), `data/Entities.kt`
  (`PlayerStatsEntity`, `MatchSummaryEntity`), `data/AchievementCatalog.kt`.
- Board FX: `ui/components/ConnectFourBoard.kt` box-fill animation,
  `ui/components/CelebratoryParticleOverlay.kt`.

---

## Phase 1 — Reward model foundation (data only)

Add a self-contained reward domain so UI and economy can build on it.

- `model/reward/BoxReward.kt`
  - `enum RewardKind { COIN, GEM, JOKER, POWER_UP, COLLECTIBLE, NOTHING }`
  - `data class BoxReward(kind, amount, id, rarity)`
  - `enum Rarity { COMMON, RARE, EPIC, LEGENDARY }`
- `model/reward/RewardTable.kt`
  - Weighted drop table per board context (normal box vs double-capture vs chain).
  - `fun roll(seed, context): BoxReward` — pure, deterministic.
- `model/reward/Joker.kt` + `JokerCatalog`
  - First jokers: `EXTRA_TURN`, `STEAL_BOX`, `SHIELD` (blocks one steal),
    `BOMB` (clear one opponent-adjacent edge preview), `DOUBLE_COINS`.
  - Each has id, title, description, rarity, icon id, and whether it is
    `rankedSafe` (default false).
- Unit tests: drop-rate distribution within tolerance, determinism for a fixed
  seed, rarity weighting, and "NOTHING" baseline.

No gameplay wiring yet — this phase is safe and fully testable.

## Phase 2 — Economy & persistence

- Extend `PlayerStatsEntity` (Room) with `coins`, `gems`, and a
  `collectiblesJson` set. **Use an explicit Room `Migration`**, not destructive
  fallback (see existing hardening note), to avoid wiping stats.
- `data/economy/RewardWallet.kt` via `ProgressionProcessor`:
  `awardReward(BoxReward)`, `spend(coins/gems)`, balance flows.
- Extend `MatchResultReward` / `MatchSummaryEntity` to record coins/gems earned
  per match for the results screen.
- Tests: wallet add/spend, migration preserves existing rows, match summary totals.

## Phase 3 — "Pop out of the box" visuals (local first)

- In `GameScreen` (local), when `moveHistory` grows with a `claimedBoxIds` entry,
  roll `RewardTable` with the match seed and enqueue a per-box reward animation.
- New `ui/components/BoxRewardBurst.kt`: coin/gem/joker sprite springs out of the
  box center, arcs up, and flies to the wallet HUD counter. Reuse the existing
  spring + particle style from `ConnectFourBoard` / `CelebratoryParticleOverlay`.
- Add a compact wallet HUD (coins/gems) to the game top bar.
- Respect `reduceMotion`: snap rewards to the counter with no arc.
- Chain/double-capture upgrades the burst (bigger, rarer drop context).

## Phase 4 — Jokers as a playable layer (local + campaign)

- Add an optional `PowerUpState` to `GameState` (null by default, so existing
  games and online are unaffected).
- Jokers earned from boxes fill a small tray; tapping a joker applies a scoped,
  deterministic effect through a new `rules/PowerUpEngine.kt` that wraps
  `GameEngine` without mutating its core rules.
- Keep each joker effect pure and unit-tested (e.g. `STEAL_BOX` reassigns one
  `boxOwners` entry; `EXTRA_TURN` sets `turnRetained`).
- Gate behind a "Power-Up Mode" toggle in local Setup and selected campaign
  stages. Standard and Ranked remain classic.

## Phase 5 — Collection & engagement loop

- `Collectibles`: a themed set (e.g. 30 "arcade relic" cards) dropped as rare box
  rewards; a new Collection screen shows owned/locked with duplicates converting
  to coins.
- Daily reward streak + "first box of the day drops a guaranteed rare".
- New `AchievementCatalog` entries: `first_joker_used`, `collect_10_relics`,
  `coins_1000`, `legendary_drop`, `power_up_win`.
- Optional shop: spend coins/gems on themes, mascots, board skins (all cosmetic).

## Phase 6 — Online integration (careful, last)

- Box rewards in online **Casual/Power-Up** queues only; computed client-side for
  animation, reconciled server-authoritatively if/when the match Cloud Function
  exists. Ranked stays reward-free on the board.
- Persist earned coins/gems server-side to prevent client inflation before any
  real-money or cross-device economy.

---

## Suggested first slice (small, shippable)

1. Phase 1 reward model + tests.
2. Phase 3 coin burst + wallet HUD in local games only (cosmetic coins).

This delivers the "reward pops out of the box" feel immediately, with zero risk
to online fairness, and sets up the economy for jokers and collectibles next.

## Risks & guardrails

- **Online fairness:** never let jokers or drops change board outcome in Ranked.
- **Room migrations:** add explicit migrations before shipping new stats columns.
- **Perf:** cap concurrent burst animations; pool sprites; honor `reduceMotion`.
- **Scope:** ship Phase 1 + 3 before building the shop or online economy.
