package com.kirankotla.connectfour.multiplayer.model

enum class OnlineGameStatus {
    WAITING,
    MATCHED,
    READY,
    PLAYING,
    PAUSED,
    COMPLETED,
    RESULTS,
    CANCELLED
}

const val DEFAULT_TURN_TIME_SECONDS = 15
val SUPPORTED_TURN_TIME_SECONDS: List<Int> = listOf(10, 15, 30)

fun normalizeTurnTimeSeconds(seconds: Int?): Int =
    if (seconds != null && seconds in SUPPORTED_TURN_TIME_SECONDS) seconds else DEFAULT_TURN_TIME_SECONDS

/** Server-granted rewards apply only to CASUAL / POWER_UP; everything else is RANKED (no rewards). */
const val ONLINE_REWARD_MODE_RANKED = "RANKED"
const val ONLINE_REWARD_MODE_CASUAL = "CASUAL"
const val ONLINE_REWARD_MODE_POWER_UP = "POWER_UP"

fun normalizeOnlineRewardMode(mode: String?): String = when (mode) {
    ONLINE_REWARD_MODE_CASUAL, ONLINE_REWARD_MODE_POWER_UP -> mode
    else -> ONLINE_REWARD_MODE_RANKED
}

/** Reward modes a player may pick per entry point. POWER_UP is never offered for Quick Match. */
fun allowedRewardModesFor(gameMode: String): List<String> = when (gameMode) {
    "QUICK_MATCH" -> listOf(ONLINE_REWARD_MODE_RANKED, ONLINE_REWARD_MODE_CASUAL)
    else -> listOf(ONLINE_REWARD_MODE_RANKED, ONLINE_REWARD_MODE_CASUAL, ONLINE_REWARD_MODE_POWER_UP)
}

/** Normalizes a requested mode and clamps it to what the entry point allows. */
fun resolveRewardModeForGame(gameMode: String, requested: String?): String {
    val mode = normalizeOnlineRewardMode(requested)
    return if (mode in allowedRewardModesFor(gameMode)) mode else ONLINE_REWARD_MODE_CASUAL
}

/** Lower is better: exact board+time, then same board, then anything (time match still preferred). */
fun matchmakingTicketPenalty(
    ticketRows: Int,
    ticketCols: Int,
    ticketTurnTimeSeconds: Int,
    rows: Int,
    cols: Int,
    turnTimeSeconds: Int
): Int {
    val sameBoard = ticketRows == rows && ticketCols == cols
    val sameTime = ticketTurnTimeSeconds == turnTimeSeconds
    return when {
        sameBoard && sameTime -> 0
        sameBoard -> 50
        sameTime -> 100
        else -> 150
    }
}

data class OnlineGameDoc(
    val gameId: String = "",
    val status: OnlineGameStatus = OnlineGameStatus.WAITING,
    val gameMode: String = "QUICK_MATCH", // QUICK_MATCH, PRIVATE_ROOM
    val createdBy: String = "",
    val roomCode: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val currentTurn: String = "",
    val winnerId: String? = null,
    @get:com.google.firebase.firestore.PropertyName("isDraw")
    @set:com.google.firebase.firestore.PropertyName("isDraw")
    var isDraw: Boolean = false,
    val version: Long = 1L,
    val rows: Int = 3,
    val cols: Int = 3,
    val variantId: String = "classic_3x3",
    val maxPlayers: Int = 2,
    val rulesVersion: Int = 1,
    val turnTimeSeconds: Int = DEFAULT_TURN_TIME_SECONDS,
    val rewardMode: String = ONLINE_REWARD_MODE_RANKED,
    val claimedEdges: List<String> = emptyList(),
    val boxOwners: Map<String, String> = emptyMap(), // boxId -> playerUid
    val edgeOwners: Map<String, String> = emptyMap(), // edgeId -> playerUid
    val playerUids: List<String> = emptyList(),
    val rematchGameId: String? = null,
    val rematchRequesterId: String? = null,
    val rematchStatus: String? = null
) {
    val totalBoxes: Int get() = rows * cols
    val totalSlots: Int get() = rows * cols
    val totalEdges: Int get() = rows * cols
    val isFinished: Boolean get() = status == OnlineGameStatus.COMPLETED || status == OnlineGameStatus.RESULTS
}

data class OnlinePlayerDoc(
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val avatarId: String = "piko",
    val colorHex: String = "#FF6B6B",
    val level: Int = 1,
    val score: Int = 0,
    val ready: Boolean = false,
    val connected: Boolean = true,
    val lastPingAt: Long = System.currentTimeMillis()
)

data class OnlineGameEvent(
    val eventId: String = "",
    val gameId: String = "",
    val playerId: String = "",
    val type: String = "PLAYER_MOVE",
    val payload: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val version: Long = 1L
)

data class MatchmakingTicket(
    val ticketId: String = "",
    val playerUid: String = "",
    val displayName: String = "",
    val avatarId: String = "piko",
    val boardSize: Int = 3,
    val rows: Int = boardSize,
    val cols: Int = boardSize,
    val variantId: String = "classic_3x3",
    val maxPlayers: Int = 2,
    val rulesVersion: Int = 1,
    val turnTimeSeconds: Int = DEFAULT_TURN_TIME_SECONDS,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val matchedGameId: String? = null
)

data class FriendDoc(
    val friendUid: String = "",
    val displayName: String = "",
    val avatarId: String = "piko",
    val level: Int = 1,
    val onlineStatus: String = "ONLINE",
    val addedAt: Long = System.currentTimeMillis()
)

data class GameInvite(
    val inviteId: String = "",
    val hostUid: String = "",
    val hostName: String = "",
    val targetUid: String = "",
    val gameId: String = "",
    val roomCode: String = "",
    val status: String = "PENDING", // PENDING, ACCEPTED, DECLINED
    val createdAt: Long = System.currentTimeMillis()
)
