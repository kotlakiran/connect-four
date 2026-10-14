package com.kirankotla.connectfour.rating

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** Competitive tier shown next to a player's rating, Bronze through Diamond. */
enum class RatingTier(val displayName: String, val emoji: String, val minRating: Int, val colorHex: String) {
    BRONZE("Bronze", "🥉", Int.MIN_VALUE, "#CD7F32"),
    SILVER("Silver", "🥈", 1100, "#9CA3AF"),
    GOLD("Gold", "🥇", 1300, "#F59E0B"),
    PLATINUM("Platinum", "💠", 1500, "#22D3EE"),
    DIAMOND("Diamond", "💎", 1700, "#8B5CF6");

    companion object {
        fun forRating(rating: Int): RatingTier = values().last { rating >= it.minRating }
    }
}

/** Rating change from one finished game, from the local player's point of view. */
data class RatingChange(
    val before: Int,
    val after: Int,
    val opponentRating: Int,
    val isProvisional: Boolean
) {
    val delta: Int get() = after - before
    val tierBefore: RatingTier get() = RatingTier.forRating(before)
    val tierAfter: RatingTier get() = RatingTier.forRating(after)
    val promoted: Boolean get() = tierAfter.ordinal > tierBefore.ordinal
    val demoted: Boolean get() = tierAfter.ordinal < tierBefore.ordinal
}

/**
 * Standard Elo, the same model Chess.com and Lichess build on.
 *
 * New players move faster (K=40 for their first [PROVISIONAL_GAMES] games) so they
 * reach their real level quickly, then settle to K=24. Ratings never drop below
 * [RATING_FLOOR], so a losing streak can't bottom out.
 *
 * Keep this in sync with `functions/index.js`, which is the authoritative source
 * for online games.
 */
object EloRating {
    const val DEFAULT_RATING = 1000
    const val PROVISIONAL_GAMES = 20
    const val RATING_FLOOR = 100
    private const val K_PROVISIONAL = 40
    private const val K_ESTABLISHED = 24

    /** Probability (0..1) that a player rated [rating] beats one rated [opponentRating]. */
    fun expectedScore(rating: Int, opponentRating: Int): Double =
        1.0 / (1.0 + 10.0.pow((opponentRating - rating) / 400.0))

    fun kFactor(gamesPlayed: Int): Int =
        if (gamesPlayed < PROVISIONAL_GAMES) K_PROVISIONAL else K_ESTABLISHED

    /**
     * @param actualScore 1.0 for a win, 0.5 for a draw, 0.0 for a loss.
     * @param gamesPlayed rated games played before this one.
     */
    fun calculate(rating: Int, opponentRating: Int, actualScore: Double, gamesPlayed: Int): RatingChange {
        require(actualScore in 0.0..1.0) { "actualScore must be between 0 and 1" }
        val expected = expectedScore(rating, opponentRating)
        val delta = (kFactor(gamesPlayed) * (actualScore - expected)).roundToInt()
        return RatingChange(
            before = rating,
            after = max(RATING_FLOOR, rating + delta),
            opponentRating = opponentRating,
            isProvisional = gamesPlayed < PROVISIONAL_GAMES
        )
    }

    /** Points still needed for the next tier, or null at Diamond. */
    fun pointsToNextTier(rating: Int): Int? {
        val next = RatingTier.values().getOrNull(RatingTier.forRating(rating).ordinal + 1) ?: return null
        return next.minRating - rating
    }
}
