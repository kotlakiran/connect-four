const REWARD_MODES = Object.freeze({ CASUAL: "CASUAL", POWER_UP: "POWER_UP" });

const MAX_SCORE_FOR_REWARD = 100;

/** Returns CASUAL/POWER_UP only for an explicit, exact value; anything else (missing, legacy, RANKED) is null. */
function normalizeRewardMode(value) {
  return value === REWARD_MODES.CASUAL || value === REWARD_MODES.POWER_UP ? value : null;
}

const ZERO_REWARD = Object.freeze({ coins: 0, gems: 0, jokers: 0, collectibles: 0 });

/**
 * Pure reward calculation from verified results.
 * @param {string|undefined} rewardMode raw reward mode stored on the game
 * @param {{isWinner:boolean,isDraw:boolean,score:number}} result verified outcome for one player
 */
function calculateOnlineReward(rewardMode, result) {
  const mode = normalizeRewardMode(rewardMode);
  if (!mode) return { ...ZERO_REWARD };

  const rawScore = Number.isFinite(result.score) ? Math.floor(result.score) : 0;
  const score = Math.max(0, Math.min(MAX_SCORE_FOR_REWARD, rawScore));
  const isWinner = !!result.isWinner && !result.isDraw;
  const isDraw = !!result.isDraw;

  const outcomeCoins = isWinner ? 30 : (isDraw ? 20 : 10);
  const reward = {
    coins: outcomeCoins + score * 2,
    gems: isWinner ? 1 : 0,
    jokers: 0,
    collectibles: 0
  };

  if (mode === REWARD_MODES.POWER_UP) {
    reward.coins += 10;
    if (isWinner) reward.jokers = 1;
    if (isWinner && score >= 5) reward.collectibles = 1;
  }
  return reward;
}

function hasReward(reward) {
  return reward.coins > 0 || reward.gems > 0 || reward.jokers > 0 || reward.collectibles > 0;
}

const SIMULATED_UID_PREFIXES = ["user_guest_", "bot_", "opponent_"];

/** True if any player is a client-simulated opponent (bot fallback), which must never earn rewards. */
function hasSimulatedPlayer(players) {
  return players.some((p) => typeof p.uid === "string" && SIMULATED_UID_PREFIXES.some((prefix) => p.uid.startsWith(prefix)));
}

module.exports = { REWARD_MODES, normalizeRewardMode, calculateOnlineReward, hasReward, hasSimulatedPlayer };
