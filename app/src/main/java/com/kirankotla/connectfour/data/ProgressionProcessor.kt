package com.kirankotla.connectfour.data

import com.kirankotla.connectfour.auth.PlayerProfile
import com.kirankotla.connectfour.model.AiDifficulty
import com.kirankotla.connectfour.model.GameState
import com.kirankotla.connectfour.model.Player
import com.kirankotla.connectfour.model.reward.BoxReward
import com.kirankotla.connectfour.model.reward.Rarity
import com.kirankotla.connectfour.model.reward.RewardKind
import com.kirankotla.connectfour.rules.PowerUpEngine
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import kotlin.math.pow
import kotlin.math.roundToInt

data class MatchResultReward(
    val xpEarned: Int,
    val starsEarned: Int,
    val isLevelUp: Boolean,
    val oldLevel: Int,
    val newLevel: Int
)

class ProgressionProcessor(
    val dao: ConnectFourDao,
    private val preferences: PlayerPreferences
) {
    fun walletFlow(): Flow<WalletBalance?> = dao.getWalletFlow()

    suspend fun awardReward(reward: BoxReward): PlayerStatsEntity {
        val current = getStats()
        var legendary = false
        val updated = when (reward.kind) {
            RewardKind.COIN -> current.copy(coins = current.coins + reward.amount)
            RewardKind.GEM -> current.copy(gems = current.gems + reward.amount)
            RewardKind.JOKER -> current.copy(ownedJokersJson = appendUnique(current.ownedJokersJson, reward.id))
            RewardKind.COLLECTIBLE -> {
                val (next, isLegendary) = withRelic(current, reward.id, reward.rarity)
                legendary = isLegendary
                next
            }
            RewardKind.POWER_UP, RewardKind.NOTHING -> current
        }
        if (updated != current) {
            dao.insertOrUpdateStats(updated)
            if (legendary) unlockAchievement("legendary_drop")
            unlockCollectionAchievements(updated)
        }
        return updated
    }

    /** Adds a relic (blank id = next unowned of [rarity]); duplicates convert to coins. */
    private fun withRelic(stats: PlayerStatsEntity, id: String, rarity: Rarity): Pair<PlayerStatsEntity, Boolean> {
        val owned = CollectibleCatalog.parseOwned(stats.collectiblesJson)
        val relic = if (id.isBlank()) CollectibleCatalog.nextUnowned(rarity, owned) else CollectibleCatalog.find(id)
        val relicRarity = relic?.rarity ?: rarity
        if (relic == null || relic.id in owned) {
            return stats.copy(coins = stats.coins + CollectibleCatalog.duplicateCoins(relicRarity)) to false
        }
        val next = stats.copy(collectiblesJson = appendUnique(stats.collectiblesJson, relic.id))
        return next to (relic.rarity == Rarity.LEGENDARY)
    }

    fun collectibleStateOf(stats: PlayerStatsEntity): CollectibleState {
        val owned = CollectibleCatalog.parseOwned(stats.collectiblesJson)
        val (have, missing) = CollectibleCatalog.ALL.partition { it.id in owned }
        return CollectibleState(have, missing)
    }

    suspend fun getCollectibleState(): CollectibleState = collectibleStateOf(getStats())

    fun dailyStateOf(stats: PlayerStatsEntity, today: Long): DailyRewardState {
        val claimed = !DailyRewardRules.canClaim(stats.lastDailyClaimDay, today)
        val next = DailyRewardRules.nextStreak(stats.lastDailyClaimDay, stats.dailyStreak, today)
        val liveStreak = if (stats.lastDailyClaimDay < 0 || today - stats.lastDailyClaimDay > 1) 0 else stats.dailyStreak
        return DailyRewardState(
            streak = liveStreak,
            bestStreak = stats.bestDailyStreak,
            claimedToday = claimed,
            nextStreak = next,
            nextReward = DailyRewardRules.tierFor(next)
        )
    }

    suspend fun getDailyRewardState(today: Long = currentEpochDay()): DailyRewardState =
        dailyStateOf(getStats(), today)

    /** Claims today's reward once per day; a second call the same day returns claimed=false. */
    suspend fun claimDailyReward(today: Long = currentEpochDay()): DailyClaimResult {
        val current = getStats()
        if (!DailyRewardRules.canClaim(current.lastDailyClaimDay, today)) {
            return DailyClaimResult(false, current.dailyStreak, null)
        }
        val streak = DailyRewardRules.nextStreak(current.lastDailyClaimDay, current.dailyStreak, today)
        val tier = DailyRewardRules.tierFor(streak)
        var next = current.copy(
            coins = current.coins + tier.coins,
            gems = current.gems + tier.gems,
            lastDailyClaimDay = today,
            dailyStreak = streak,
            bestDailyStreak = maxOf(current.bestDailyStreak, streak)
        )
        var relicId: String? = null
        tier.relicRarity?.let { rarity ->
            val before = CollectibleCatalog.parseOwned(next.collectiblesJson)
            val (withRelic, _) = withRelic(next, "", rarity)
            next = withRelic
            relicId = (CollectibleCatalog.parseOwned(next.collectiblesJson) - before).firstOrNull()
        }
        dao.insertOrUpdateStats(next)
        unlockCollectionAchievements(next)
        return DailyClaimResult(true, streak, tier, relicId)
    }

    /** Call after a Power-Up Mode match is won. */
    suspend fun recordPowerUpWin() = unlockAchievement("power_up_win")

    private fun currentEpochDay(): Long = java.time.LocalDate.now().toEpochDay()

    private suspend fun unlockCollectionAchievements(stats: PlayerStatsEntity) {
        if (stats.coins >= 1000) unlockAchievement("coins_1000")
        if (CollectibleCatalog.parseOwned(stats.collectiblesJson).size >= 10) unlockAchievement("collect_10_relics")
    }

    private suspend fun unlockAchievement(id: String) {
        val existing = dao.getAchievement(id)
        if (existing?.isUnlocked == true) return
        val target = AchievementCatalog.ALL_ACHIEVEMENTS.firstOrNull { it.id == id }?.targetProgress ?: 1
        dao.insertOrUpdateAchievement(
            (existing ?: AchievementEntity(id)).copy(
                progress = target,
                isUnlocked = true,
                unlockedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun ownedJokerIds(): List<String> = PowerUpEngine.ownedIds(getStats().ownedJokersJson)

    /** Removes one owned joker; returns false if the player does not own it. */
    suspend fun consumeJoker(jokerId: String): Boolean {
        val current = getStats()
        val updatedJson = PowerUpEngine.consumeFromOwnedJson(current.ownedJokersJson, jokerId) ?: return false
        dao.insertOrUpdateStats(current.copy(ownedJokersJson = updatedJson))
        unlockAchievement("first_joker_used")
        return true
    }

    private fun appendUnique(json: String, id: String): String {
        if (id.isBlank()) return json
        val values = JSONArray(json)
        for (index in 0 until values.length()) {
            if (values.optString(index) == id) return json
        }
        values.put(id)
        return values.toString()
    }

    suspend fun updateProfile(name: String, age: Int?) {
        preferences.updatePlayerProfile(name, age)
        var profile = dao.getPlayerProfile("local_player")
        if (profile == null) {
            profile = PlayerProfileEntity("local_player", name, age)
        } else {
            profile = profile.copy(name = name, age = age)
        }
        dao.insertOrUpdateProfile(profile)
    }

    suspend fun updateAvatar(avatarId: String) {
        preferences.updateSelectedMascot(avatarId)
        var profile = dao.getPlayerProfile("local_player")
        if (profile == null) {
            profile = PlayerProfileEntity("local_player", avatarId = avatarId)
        } else {
            profile = profile.copy(avatarId = avatarId)
        }
        dao.insertOrUpdateProfile(profile)
    }

    suspend fun getProfile(): PlayerProfileEntity {
        return dao.getPlayerProfile("local_player") ?: PlayerProfileEntity()
    }

    suspend fun getStats(): PlayerStatsEntity {
        return dao.getPlayerStats("local_player") ?: PlayerStatsEntity()
    }

    suspend fun syncFromCloud(profile: PlayerProfile) {
        val localProfile = getProfile()
        updateProfile(profile.displayName, localProfile.age)
        updateAvatar(profile.avatarId)
        dao.insertOrUpdateProfile(
            getProfile().copy(
                level = profile.level,
                lifetimeXp = profile.score
            )
        )

        val localStats = getStats()
        dao.insertOrUpdateStats(
            localStats.copy(
                gamesStarted = profile.gamesPlayed,
                gamesCompleted = profile.gamesPlayed,
                wins = profile.gamesWon,
                losses = profile.gamesLost
            )
        )
    }

    fun calculateRequiredXpForLevel(level: Int): Int {
        if (level <= 1) return 0
        return (100.0 * (level - 1).toDouble().pow(1.35)).roundToInt()
    }

    suspend fun processMatchCompletion(
        state: GameState,
        humanPlayerId: Int = 1,
        durationMs: Long,
        hintsUsed: Int,
        undosUsed: Int
    ): MatchResultReward {
        val userScore = state.players.find { it.id == humanPlayerId }?.score ?: 0
        val opponentScore = state.players.filter { it.id != humanPlayerId }.sumOf { it.score }
        val isWin = userScore > opponentScore
        val isDraw = userScore == opponentScore

        val baseXP = if (isWin) 120 else if (isDraw) 60 else 30
        val boardMultiplier = (state.rows * state.cols) / 9.0
        val difficultyMultiplier = when (state.aiDifficulty) {
            AiDifficulty.BEGINNER -> 0.8
            AiDifficulty.EASY -> 1.0
            AiDifficulty.MEDIUM -> 1.3
            AiDifficulty.HARD -> 1.6
            AiDifficulty.EXPERT -> 2.0
            null -> 1.0
        }
        val streakBonus = 10
        val rawXP = ((baseXP * boardMultiplier * difficultyMultiplier) + (userScore * 5)).roundToInt() + streakBonus

        val stars = when {
            isWin && (userScore - opponentScore >= 3) -> 3
            isWin -> 2
            isDraw -> 1
            else -> 1
        }

        val currentProfile = getProfile()
        val currentStats = getStats()

        val oldXp = currentProfile.lifetimeXp
        val newXp = oldXp + rawXP
        val oldLevel = currentProfile.level
        var newLevel = oldLevel
        while (newXp >= calculateRequiredXpForLevel(newLevel + 1)) {
            newLevel++
        }

        val updatedProfile = currentProfile.copy(level = newLevel, lifetimeXp = newXp)
        dao.insertOrUpdateProfile(updatedProfile)

        val newWins = currentStats.wins + if (isWin) 1 else 0
        val newLosses = currentStats.losses + if (!isWin && !isDraw) 1 else 0
        val newDraws = currentStats.draws + if (isDraw) 1 else 0
        val newStreak = if (isWin) currentStats.currentStreak + 1 else 0
        val bestStreak = maxOf(currentStats.bestStreak, newStreak)

        val updatedStats = currentStats.copy(
            gamesStarted = currentStats.gamesStarted + 1,
            gamesCompleted = currentStats.gamesCompleted + 1,
            wins = newWins,
            losses = newLosses,
            draws = newDraws,
            boxesCaptured = currentStats.boxesCaptured + userScore,
            currentStreak = newStreak,
            bestStreak = bestStreak
        )
        dao.insertOrUpdateStats(updatedStats)

        // Save summary
        val summary = MatchSummaryEntity(
            matchId = state.gameId,
            mode = state.mode.name,
            rows = state.rows,
            cols = state.cols,
            turnRule = state.turnRule.name,
            aiDifficulty = state.aiDifficulty?.name,
            result = if (isWin) "WIN" else if (isDraw) "DRAW" else "LOSS",
            userScore = userScore,
            opponentScore = opponentScore,
            durationMs = durationMs,
            movesCount = state.moveHistory.size,
            chainRecord = 0,
            hintsUsed = hintsUsed,
            undosUsed = undosUsed,
            starsEarned = stars
        )
        dao.insertMatchSummary(summary)

        return MatchResultReward(
            xpEarned = rawXP,
            starsEarned = stars,
            isLevelUp = newLevel > oldLevel,
            oldLevel = oldLevel,
            newLevel = newLevel
        )
    }
}
