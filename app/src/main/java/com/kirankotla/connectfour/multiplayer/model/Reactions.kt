package com.kirankotla.connectfour.multiplayer.model

/**
 * Quick emotes for online and Nearby matches. A fixed set (no free text) lets
 * players react without the moderation and toxicity problems of open chat,
 * the same approach used by Clash Royale and Chess.com quick chat.
 */
enum class Reaction(val id: String, val emoji: String, val label: String) {
    GOOD_GAME("gg", "🤝", "Good game!"),
    THUMBS_UP("thumbs", "👍", "Nice!"),
    WOW("wow", "😮", "Wow!"),
    OOPS("oops", "😅", "Oops!"),
    FIRE("fire", "🔥", "On fire!"),
    BRAIN("brain", "🧠", "Clever move"),
    THINKING("think", "🤔", "Hmm..."),
    GOOD_LUCK("gl", "🍀", "Good luck!");

    companion object {
        fun fromId(id: String?): Reaction? = values().firstOrNull { it.id == id }

        /** Event type used in game event streams (Firestore events / Nearby payloads). */
        const val EVENT_TYPE = "REACTION"
        const val PAYLOAD_KEY = "reactionId"
    }
}

/** A reaction currently on screen. [key] lets the UI restart the animation for repeats. */
data class ShownReaction(val reaction: Reaction, val fromUid: String, val key: Long)

/**
 * Client-side spam guard: at most one reaction every [cooldownMs], and events older
 * than [maxAgeMs] are ignored so reloading a game's event history doesn't replay
 * stale emotes.
 */
class ReactionThrottle(
    private val cooldownMs: Long = 2500L,
    private val maxAgeMs: Long = 15_000L
) {
    private var lastSentAt: Long = Long.MIN_VALUE / 2

    fun trySend(nowMs: Long): Boolean {
        if (nowMs - lastSentAt < cooldownMs) return false
        lastSentAt = nowMs
        return true
    }

    fun isFresh(eventTimestampMs: Long, nowMs: Long): Boolean = nowMs - eventTimestampMs <= maxAgeMs
}
