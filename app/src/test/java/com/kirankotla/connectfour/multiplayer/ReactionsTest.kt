package com.kirankotla.connectfour.multiplayer

import com.kirankotla.connectfour.multiplayer.model.Reaction
import com.kirankotla.connectfour.multiplayer.model.ReactionThrottle
import org.junit.Assert.*
import org.junit.Test

class ReactionsTest {

    @Test
    fun testReactionLookupById() {
        assertEquals(Reaction.GOOD_GAME, Reaction.fromId("gg"))
        assertEquals(Reaction.FIRE, Reaction.fromId("fire"))
        assertEquals(Reaction.BRAIN, Reaction.fromId("brain"))
        assertEquals(Reaction.THINKING, Reaction.fromId("think"))
        assertNull(Reaction.fromId("unknown_id"))
    }

    @Test
    fun testReactionThrottleCooldown() {
        val throttle = ReactionThrottle(cooldownMs = 2500L)
        val t0 = 10_000L

        // First send succeeds
        assertTrue(throttle.trySend(t0))

        // Immediate subsequent send blocked
        assertFalse(throttle.trySend(t0 + 500L))
        assertFalse(throttle.trySend(t0 + 2499L))

        // Send after cooldown passes succeeds
        assertTrue(throttle.trySend(t0 + 2501L))
    }

    @Test
    fun testReactionThrottleStaleness() {
        val throttle = ReactionThrottle(maxAgeMs = 15_000L)
        val now = 100_000L

        assertTrue(throttle.isFresh(eventTimestampMs = now - 5_000L, nowMs = now))
        assertTrue(throttle.isFresh(eventTimestampMs = now - 15_000L, nowMs = now))
        assertFalse(throttle.isFresh(eventTimestampMs = now - 15_001L, nowMs = now))
        assertFalse(throttle.isFresh(eventTimestampMs = now - 60_000L, nowMs = now))
    }
}
