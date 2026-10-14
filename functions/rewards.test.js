const test = require("node:test");
const assert = require("node:assert/strict");
const { calculateOnlineReward, normalizeRewardMode, hasReward, hasSimulatedPlayer } = require("./rewards");

const win = { isWinner: true, isDraw: false, score: 6 };
const loss = { isWinner: false, isDraw: false, score: 3 };
const draw = { isWinner: false, isDraw: true, score: 4 };

test("missing, legacy and ranked modes grant nothing", () => {
  for (const mode of [undefined, null, "", "RANKED", "QUICK_MATCH", "casual", 5]) {
    assert.equal(normalizeRewardMode(mode), null);
    assert.equal(hasReward(calculateOnlineReward(mode, win)), false);
  }
});

test("casual rewards scale with outcome and no jokers/collectibles", () => {
  assert.deepEqual(calculateOnlineReward("CASUAL", win), { coins: 42, gems: 1, jokers: 0, collectibles: 0 });
  assert.deepEqual(calculateOnlineReward("CASUAL", draw), { coins: 28, gems: 0, jokers: 0, collectibles: 0 });
  assert.deepEqual(calculateOnlineReward("CASUAL", loss), { coins: 16, gems: 0, jokers: 0, collectibles: 0 });
});

test("power-up winner earns a joker and relic on a strong score", () => {
  assert.deepEqual(calculateOnlineReward("POWER_UP", win), { coins: 52, gems: 1, jokers: 1, collectibles: 1 });
  assert.deepEqual(calculateOnlineReward("POWER_UP", { ...win, score: 4 }).collectibles, 0);
  assert.equal(calculateOnlineReward("POWER_UP", loss).jokers, 0);
});

test("invalid or huge scores are clamped", () => {
  assert.equal(calculateOnlineReward("CASUAL", { ...loss, score: NaN }).coins, 10);
  assert.equal(calculateOnlineReward("CASUAL", { ...loss, score: 1e9 }).coins, 210);
  assert.equal(calculateOnlineReward("CASUAL", { ...loss, score: -5 }).coins, 10);
});

test("simulated opponents are detected so bot games never earn rewards", () => {
  assert.equal(hasSimulatedPlayer([{ uid: "abc" }, { uid: "user_guest_1a2b3c" }]), true);
  assert.equal(hasSimulatedPlayer([{ uid: "bot_x" }, { uid: "abc" }]), true);
  assert.equal(hasSimulatedPlayer([{ uid: "abc" }, { uid: "def" }]), false);
});
