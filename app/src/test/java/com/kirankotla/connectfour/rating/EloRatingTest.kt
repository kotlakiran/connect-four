package com.kirankotla.connectfour.rating

import org.junit.Assert.*
import org.junit.Test

class EloRatingTest {

    @Test
    fun testExpectedScoreEqualRatingsIsFiftyPercent() {
        val expected = EloRating.expectedScore(1200, 1200)
        assertEquals(0.5, expected, 0.001)
    }

    @Test
    fun testExpectedScoreSymmetry() {
        val exp1 = EloRating.expectedScore(1400, 1200)
        val exp2 = EloRating.expectedScore(1200, 1400)
        assertEquals(1.0, exp1 + exp2, 0.001)
        assertTrue(exp1 > 0.5)
        assertTrue(exp2 < 0.5)
    }

    @Test
    fun testProvisionalKFactorYieldsHigherDelta() {
        val provisionalChange = EloRating.calculate(
            rating = 1200,
            opponentRating = 1200,
            actualScore = 1.0,
            gamesPlayed = 5
        )
        val establishedChange = EloRating.calculate(
            rating = 1200,
            opponentRating = 1200,
            actualScore = 1.0,
            gamesPlayed = 30
        )

        assertEquals(20, provisionalChange.delta)
        assertEquals(12, establishedChange.delta)
        assertTrue(provisionalChange.delta > establishedChange.delta)
        assertTrue(provisionalChange.isProvisional)
        assertFalse(establishedChange.isProvisional)
    }

    @Test
    fun testDrawYieldsZeroChangeAgainstEqualOpponent() {
        val change = EloRating.calculate(
            rating = 1500,
            opponentRating = 1500,
            actualScore = 0.5,
            gamesPlayed = 25
        )
        assertEquals(0, change.delta)
        assertEquals(1500, change.after)
    }

    @Test
    fun testFloorLimit() {
        val change = EloRating.calculate(
            rating = 105,
            opponentRating = 3000,
            actualScore = 0.0,
            gamesPlayed = 2
        )
        assertTrue(change.after >= EloRating.RATING_FLOOR)
    }

    @Test
    fun testRatingTiers() {
        assertEquals(RatingTier.BRONZE, RatingTier.forRating(1050))
        assertEquals(RatingTier.SILVER, RatingTier.forRating(1150))
        assertEquals(RatingTier.GOLD, RatingTier.forRating(1350))
        assertEquals(RatingTier.PLATINUM, RatingTier.forRating(1550))
        assertEquals(RatingTier.DIAMOND, RatingTier.forRating(1750))
    }
}
