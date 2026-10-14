package com.kirankotla.connectfour.multiplayer.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.kirankotla.connectfour.auth.PublicPlayerProfile
import com.kirankotla.connectfour.config.FirebaseManager
import com.kirankotla.connectfour.model.BoardSpec
import com.kirankotla.connectfour.multiplayer.engine.AntiCheatEngine
import com.kirankotla.connectfour.multiplayer.engine.MultiplayerStateMachine
import com.kirankotla.connectfour.multiplayer.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.random.Random

class FirebaseMultiplayerRepository(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : MultiplayerRepository {

    private val TAG = "FirebaseMultiplayerRepo"
    private val firestore: FirebaseFirestore? get() = FirebaseManager.firestore

    private val _currentSession = MutableStateFlow<OnlineGameDoc?>(null)
    override val currentSession: StateFlow<OnlineGameDoc?> = _currentSession.asStateFlow()

    private val _playersInSession = MutableStateFlow<List<OnlinePlayerDoc>>(emptyList())
    override val playersInSession: StateFlow<List<OnlinePlayerDoc>> = _playersInSession.asStateFlow()

    private val _gameEvents = MutableSharedFlow<OnlineGameEvent>(extraBufferCapacity = 64)
    override val gameEvents: SharedFlow<OnlineGameEvent> = _gameEvents.asSharedFlow()

    private val _incomingInvites = MutableStateFlow<List<GameInvite>>(emptyList())
    override val incomingInvites: StateFlow<List<GameInvite>> = _incomingInvites.asStateFlow()

    private var gameListener: ListenerRegistration? = null
    private var playersListener: ListenerRegistration? = null
    private var eventsListener: ListenerRegistration? = null
    private var currentTicketId: String? = null
    private var currentHostGameId: String? = null
    private var currentSessionId: String = UUID.randomUUID().toString()
    private var localUid: String? = null

    private suspend fun ensureAuthenticated(): String {
        val auth = FirebaseManager.auth ?: return localUid ?: "player_${UUID.randomUUID().toString().take(8)}"
        val currentUser = auth.currentUser
        if (currentUser != null) return currentUser.uid
        return try {
            val res = auth.signInAnonymously().await()
            res.user?.uid ?: (localUid ?: "guest_${UUID.randomUUID().toString().take(8)}")
        } catch (e: Exception) {
            Log.w(TAG, "signInAnonymously fallback: ${e.message}")
            localUid ?: "guest_${UUID.randomUUID().toString().take(8)}"
        }
    }

    override suspend fun startQuickMatch(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int, rewardMode: String): Result<String> = withContext(Dispatchers.IO) {
        val turnTime = normalizeTurnTimeSeconds(turnTimeSeconds)
        val mode = resolveRewardModeForGame("QUICK_MATCH", rewardMode)
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore is not available"))
        val myUid = ensureAuthenticated()
        localUid = myUid
        val effectivePlayer = player.copy(uid = myUid, ready = true, connected = true)
        currentSessionId = UUID.randomUUID().toString()

        try {
            // Find existing waiting ticket (created in the last 60 seconds)
            val now = System.currentTimeMillis()
            val waitingSnap = db.collection("lobbies")
                .whereEqualTo("gameMode", "QUICK_MATCH")
                .whereEqualTo("status", "WAITING")
                .limit(15)
                .get()
                .await()

            var matchedGameId: String? = null
            // Prioritize exact board + turn time, then same board, so real users still always match.
            val sortedDocs = waitingSnap.documents
                .filter { doc -> normalizeOnlineRewardMode(doc.getString("rewardMode")) == mode }
                .sortedBy { doc -> ticketMatchRank(doc, board, turnTime, effectivePlayer.level) }

            for (doc in sortedDocs) {
                val ticketUid = doc.getString("playerUid")
                val ticketSessionId = doc.getString("sessionId")
                val createdAt = doc.getLong("createdAt") ?: 0L
                val candidateGameId = doc.getString("gameId") ?: doc.id
                val expiresAt = doc.getLong("expiresAt") ?: (createdAt + 120000L)

                if (now >= expiresAt) {
                    doc.reference.delete()
                    continue
                }

                val isDifferentDevice = candidateGameId != currentHostGameId &&
                    (ticketUid != effectivePlayer.uid || (ticketSessionId != null && ticketSessionId != currentSessionId))

                if (isDifferentDevice && kotlin.math.abs(now - createdAt) < 90000L) {
                    val gameSnap = db.collection("games").document(candidateGameId).get().await()
                    val gameStatus = gameSnap.getString("status")
                    val existingUids = (gameSnap.get("playerUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    if (gameSnap.exists() && gameStatus == OnlineGameStatus.WAITING.name && existingUids.size < 2) {
                        val joinPlayer = if (existingUids.contains(effectivePlayer.uid)) {
                            effectivePlayer.copy(uid = "${effectivePlayer.uid}_device2")
                        } else {
                            effectivePlayer
                        }
                        if (claimWaitingLobby(doc.id, candidateGameId, joinPlayer)) {
                            matchedGameId = candidateGameId
                            break
                        }
                    }
                }
            }

            if (matchedGameId != null) {
                return@withContext Result.success(matchedGameId)
            }

            // Create new game
            val gameId = "game_${UUID.randomUUID().toString().take(10)}"
            currentHostGameId = gameId
            val initialGame = OnlineGameDoc(
                gameId = gameId,
                status = OnlineGameStatus.WAITING,
                gameMode = "QUICK_MATCH",
                createdBy = effectivePlayer.uid,
                createdAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + 120000L,
                currentTurn = effectivePlayer.uid,
                playerUids = listOf(effectivePlayer.uid),
                rows = board.rows,
                cols = board.cols,
                variantId = board.id,
                maxPlayers = board.maxPlayers,
                turnTimeSeconds = turnTime,
                rewardMode = mode
            )

            db.collection("games").document(gameId).set(initialGame).await()
            db.collection("games").document(gameId).collection("players").document(effectivePlayer.uid).set(effectivePlayer).await()

            // Create matchmaking lobby ticket
            val ticketId = "ticket_${effectivePlayer.uid}_${currentSessionId.take(6)}"
            currentTicketId = ticketId
            val ticketMap = mapOf(
                "gameId" to gameId,
                "gameMode" to "QUICK_MATCH",
                "playerUid" to effectivePlayer.uid,
                "sessionId" to currentSessionId,
                "displayName" to effectivePlayer.displayName,
                "level" to effectivePlayer.level,
                "boardSize" to board.rows,
                "rows" to board.rows,
                "cols" to board.cols,
                "variantId" to board.id,
                "maxPlayers" to board.maxPlayers,
                "rulesVersion" to 1,
                "turnTimeSeconds" to turnTime,
                "rewardMode" to mode,
                "status" to "WAITING",
                "createdAt" to System.currentTimeMillis(),
                "expiresAt" to (System.currentTimeMillis() + 120000L)
            )
            db.collection("lobbies").document(ticketId).set(ticketMap).await()

            listenToGame(gameId)
            Result.success(gameId)
        } catch (e: Exception) {
            Log.e(TAG, "Quick match error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun pollQuickMatch(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int, rewardMode: String): Boolean = withContext(Dispatchers.IO) {
        val turnTime = normalizeTurnTimeSeconds(turnTimeSeconds)
        val mode = resolveRewardModeForGame("QUICK_MATCH", rewardMode)
        val db = firestore ?: return@withContext false
        val current = _currentSession.value ?: return@withContext false
        if (current.status != OnlineGameStatus.WAITING) return@withContext false
        val myUid = ensureAuthenticated()
        val effectivePlayer = player.copy(uid = myUid, ready = true, connected = true)
        val myGameId = current.gameId

        try {
            val now = System.currentTimeMillis()
            val waitingSnap = db.collection("lobbies")
                .whereEqualTo("gameMode", "QUICK_MATCH")
                .whereEqualTo("status", "WAITING")
                .limit(15)
                .get()
                .await()

            val sortedDocs = waitingSnap.documents
                .filter { doc -> normalizeOnlineRewardMode(doc.getString("rewardMode")) == mode }
                .sortedBy { doc -> ticketMatchRank(doc, board, turnTime, effectivePlayer.level) }

            for (doc in sortedDocs) {
                val candidateGameId = doc.getString("gameId") ?: doc.id
                if (candidateGameId == myGameId) continue

                val ticketUid = doc.getString("playerUid")
                val ticketSessionId = doc.getString("sessionId")
                val createdAt = doc.getLong("createdAt") ?: 0L

                val isDifferentDevice = candidateGameId != currentHostGameId &&
                    (ticketUid != effectivePlayer.uid || (ticketSessionId != null && ticketSessionId != currentSessionId))

                if (isDifferentDevice && kotlin.math.abs(now - createdAt) < 90000L) {
                    val gameSnap = db.collection("games").document(candidateGameId).get().await()
                    val gameStatus = gameSnap.getString("status")
                    val existingUids = (gameSnap.get("playerUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    if (gameSnap.exists() && gameStatus == OnlineGameStatus.WAITING.name && existingUids.size < 2) {
                        val joinPlayer = if (existingUids.contains(effectivePlayer.uid)) {
                            effectivePlayer.copy(uid = "${effectivePlayer.uid}_device2")
                        } else {
                            effectivePlayer
                        }

                        if (claimWaitingLobby(doc.id, candidateGameId, joinPlayer)) {
                            // Abandon our own ticket only after the real opponent claim succeeds.
                            currentTicketId?.let { tid ->
                                try { db.collection("lobbies").document(tid).delete().await() } catch (_: Exception) {}
                                currentTicketId = null
                            }
                            try {
                                db.collection("games").document(myGameId).update("status", OnlineGameStatus.CANCELLED.name)
                            } catch (_: Exception) {}
                            return@withContext true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "pollQuickMatch error: ${e.message}")
        }
        false
    }

    override suspend fun cancelMatchmaking() = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val ticketId = currentTicketId
        currentTicketId = null
        currentHostGameId = null
        try {
            if (ticketId != null) {
                db.collection("lobbies").document(ticketId).delete().await()
            }
            val session = _currentSession.value
            if (session != null && session.status == OnlineGameStatus.WAITING) {
                db.collection("games").document(session.gameId).update("status", OnlineGameStatus.CANCELLED.name)
            }
            leaveSession()
        } catch (e: Exception) {
            Log.w(TAG, "Cancel matchmaking error: ${e.message}")
        }
    }

    override suspend fun resumeActiveGame(): Boolean = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext false
        val uid = FirebaseManager.auth?.currentUser?.uid ?: return@withContext false
        try {
            val activeStatuses = setOf(
                OnlineGameStatus.WAITING.name,
                OnlineGameStatus.MATCHED.name,
                OnlineGameStatus.READY.name,
                OnlineGameStatus.PLAYING.name,
                OnlineGameStatus.PAUSED.name
            )
            val snapshot = db.collection("games")
                .whereArrayContains("playerUids", uid)
                .limit(20)
                .get()
                .await()
            val activeGame = snapshot.documents
                .filter { it.getString("status") in activeStatuses }
                .maxByOrNull { it.getLong("createdAt") ?: 0L }
                ?: return@withContext false
            currentSessionId = UUID.randomUUID().toString()
            localUid = uid
            listenToGame(activeGame.id)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Resume active game failed: ${e.message}")
            false
        }
    }

    private fun ticketMatchRank(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        board: BoardSpec,
        turnTimeSeconds: Int,
        playerLevel: Int
    ): Int {
        val legacySize = doc.getLong("boardSize")?.toInt() ?: 3
        val rows = doc.getLong("rows")?.toInt() ?: legacySize
        val cols = doc.getLong("cols")?.toInt() ?: legacySize
        val ticketTime = normalizeTurnTimeSeconds(doc.getLong("turnTimeSeconds")?.toInt())
        val penalty = matchmakingTicketPenalty(rows, cols, ticketTime, board.rows, board.cols, turnTimeSeconds)
        val ticketLevel = doc.getLong("level")?.toInt() ?: 1
        return penalty + kotlin.math.abs(ticketLevel - playerLevel).coerceAtMost(20)
    }

    override suspend fun createPrivateGame(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int, rewardMode: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val turnTime = normalizeTurnTimeSeconds(turnTimeSeconds)
        val mode = resolveRewardModeForGame("PRIVATE_ROOM", rewardMode)
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore is not available"))
        val myUid = ensureAuthenticated()
        localUid = myUid
        val effectivePlayer = player.copy(uid = myUid, ready = true, connected = true)
        try {
            val gameId = "priv_${UUID.randomUUID().toString().take(10)}"
            currentHostGameId = gameId
            val code = generateRoomCode()
            val initialGame = OnlineGameDoc(
                gameId = gameId,
                status = OnlineGameStatus.WAITING,
                gameMode = "PRIVATE_ROOM",
                createdBy = effectivePlayer.uid,
                roomCode = code,
                createdAt = System.currentTimeMillis(),
                currentTurn = effectivePlayer.uid,
                playerUids = listOf(effectivePlayer.uid),
                rows = board.rows,
                cols = board.cols,
                variantId = board.id,
                maxPlayers = board.maxPlayers,
                turnTimeSeconds = turnTime,
                rewardMode = mode
            )

            db.collection("games").document(gameId).set(initialGame).await()
            db.collection("games").document(gameId).collection("players").document(effectivePlayer.uid).set(effectivePlayer).await()

            // Index room code
            currentTicketId = "code_$code"
            db.collection("lobbies").document("code_$code").set(
                mapOf(
                    "gameId" to gameId,
                    "roomCode" to code,
                    "hostUid" to effectivePlayer.uid,
                    "playerUid" to effectivePlayer.uid, // required by firestore.rules for lobby creation
                    "boardSize" to board.rows,
                    "rows" to board.rows,
                    "cols" to board.cols,
                    "variantId" to board.id,
                    "maxPlayers" to board.maxPlayers,
                    "rulesVersion" to 1,
                    "turnTimeSeconds" to turnTime,
                    "rewardMode" to mode,
                    "gameMode" to "PRIVATE_ROOM",
                    "status" to "WAITING",
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()

            listenToGame(gameId)
            Result.success(Pair(gameId, code))
        } catch (e: Exception) {
            Log.e(TAG, "Create private game error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun joinPrivateGame(player: OnlinePlayerDoc, roomCode: String): Result<String> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore is not available"))
        val myUid = ensureAuthenticated()
        localUid = myUid
        val effectivePlayer = player.copy(uid = myUid, ready = true, connected = true)
        val code = roomCode.trim().uppercase()
        try {
            val lobbyRef = db.collection("lobbies").document("code_$code")
            val lobbySnap = lobbyRef.get().await()
            if (!lobbySnap.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Invalid private game code: $code"))
            }
            val gameId = lobbySnap.getString("gameId") ?: throw IllegalStateException("Game ID not found for code")
            val hostUid = lobbySnap.getString("hostUid") ?: ""
            if (hostUid == effectivePlayer.uid) {
                return@withContext Result.failure(IllegalArgumentException("You are the host of room $code. Share the invite with a friend."))
            }

            val gameRef = db.collection("games").document(gameId)

            // Atomic transaction guarantees only 1 opponent can enter; multiple link clickers are placed in queue
            val joinOutcome = db.runTransaction { transaction ->
                val gameSnap = transaction.get(gameRef)
                if (!gameSnap.exists()) {
                    throw IllegalStateException("Game session not found")
                }
                val currentStatus = gameSnap.getString("status") ?: OnlineGameStatus.WAITING.name
                val playerUids = (gameSnap.get("playerUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val currentQueue = (gameSnap.get("queue") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                // Already joined previously
                if (effectivePlayer.uid in playerUids) {
                    return@runTransaction "JOINED"
                }

                // If already full (2 players: host + 1 guest) or game has already started:
                if (playerUids.size >= 2 || (currentStatus != OnlineGameStatus.WAITING.name && currentStatus != OnlineGameStatus.MATCHED.name)) {
                    val updatedQueue = (currentQueue + effectivePlayer.uid).distinct()
                    transaction.update(gameRef, "queue", updatedQueue)
                    val queuePosition = updatedQueue.indexOf(effectivePlayer.uid) + 1
                    return@runTransaction "QUEUED:$queuePosition"
                }

                // First guest! Admit them exclusively as Player 2
                val updatedUids = (playerUids + effectivePlayer.uid).distinct()
                val playerDocRef = gameRef.collection("players").document(effectivePlayer.uid)
                val playerWithColor = effectivePlayer.copy(colorHex = "#22B8A7", ready = true, connected = true)
                transaction.set(playerDocRef, playerWithColor)
                transaction.update(
                    gameRef,
                    mapOf(
                        "playerUids" to updatedUids,
                        "status" to OnlineGameStatus.MATCHED.name,
                        "version" to (gameSnap.getLong("version") ?: 1L) + 1
                    )
                )
                transaction.update(lobbyRef, "status", "MATCHED")
                "JOINED"
            }.await()

            if (joinOutcome.startsWith("QUEUED:")) {
                val pos = joinOutcome.substringAfter("QUEUED:")
                return@withContext Result.failure(IllegalStateException("Match already started with another player! You are in the waiting queue (Position #$pos)."))
            }

            listenToGame(gameId)
            Result.success(gameId)
        } catch (e: Exception) {
            Log.e(TAG, "Join private game error: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun claimWaitingLobby(
        lobbyId: String,
        gameId: String,
        player: OnlinePlayerDoc
    ): Boolean {
        val db = firestore ?: return false
        val lobbyRef = db.collection("lobbies").document(lobbyId)
        val gameRef = db.collection("games").document(gameId)
        val playerRef = gameRef.collection("players").document(player.uid)

        return try {
            val claimed = db.runTransaction { transaction ->
                val lobbySnap = transaction.get(lobbyRef)
                val gameSnap = transaction.get(gameRef)
                val status = gameSnap.getString("status") ?: OnlineGameStatus.WAITING.name
                val playerUids = (gameSnap.get("playerUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val lobbyStatus = lobbySnap.getString("status")

                if (!lobbySnap.exists() || lobbyStatus != "WAITING" ||
                    !gameSnap.exists() || status != OnlineGameStatus.WAITING.name || playerUids.size >= 2
                ) {
                    return@runTransaction false
                }

                val updatedUids = (playerUids + player.uid).distinct()
                transaction.set(
                    playerRef,
                    player.copy(colorHex = "#22B8A7", ready = true, connected = true)
                )
                transaction.update(
                    gameRef,
                    mapOf(
                        "playerUids" to updatedUids,
                        "status" to OnlineGameStatus.MATCHED.name,
                        "version" to (gameSnap.getLong("version") ?: 1L) + 1
                    )
                )
                transaction.update(
                    lobbyRef,
                    mapOf(
                        "status" to "MATCHED",
                        "matchedPlayerUid" to player.uid
                    )
                )
                true
            }.await()

            if (claimed) listenToGame(gameId)
            claimed
        } catch (e: Exception) {
            Log.w(TAG, "Atomic matchmaking claim failed: ${e.message}")
            false
        }
    }

    private suspend fun joinExistingGame(gameId: String, player: OnlinePlayerDoc) {
        val db = firestore ?: return
        val gameRef = db.collection("games").document(gameId)
        val gameSnap = gameRef.get().await()
        val currentUids = (gameSnap.get("playerUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val updatedUids = (currentUids + player.uid).distinct()

        val playerWithColor = player.copy(colorHex = "#22B8A7", ready = true, connected = true)
        gameRef.collection("players").document(player.uid).set(playerWithColor).await()

        val updates = mapOf(
            "playerUids" to updatedUids,
            "status" to OnlineGameStatus.MATCHED.name,
            "version" to (gameSnap.getLong("version") ?: 1L) + 1
        )
        gameRef.update(updates).await()
        listenToGame(gameId)
    }

    override suspend fun setPlayerReady(gameId: String, isReady: Boolean) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val currentGame = _currentSession.value
        val myUid = FirebaseManager.auth?.currentUser?.uid
            ?: localUid
            ?: currentGame?.playerUids?.firstOrNull()
            ?: return@withContext

        try {
            val playerRef = db.collection("games").document(gameId).collection("players").document(myUid)
            playerRef.update("ready", isReady).await()

            // Check if both players ready
            val playersSnap = db.collection("games").document(gameId).collection("players").get().await()
            val allReady = playersSnap.documents.size >= 2 && playersSnap.documents.all { it.getBoolean("ready") == true }

            if (allReady) {
                startGame(gameId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "setPlayerReady error: ${e.message}")
        }
    }

    override suspend fun startGame(gameId: String): Unit = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val gameRef = db.collection("games").document(gameId)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(gameRef)
                val statusStr = snap.getString("status")
                if (statusStr != OnlineGameStatus.PLAYING.name && statusStr != OnlineGameStatus.COMPLETED.name) {
                    val version = snap.getLong("version") ?: 1L
                    tx.update(
                        gameRef,
                        mapOf(
                            "status" to OnlineGameStatus.PLAYING.name,
                            "startedAt" to System.currentTimeMillis(),
                            "version" to version + 1
                        )
                    )
                }
                null
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "startGame error: ${e.message}")
        }
    }

    override suspend fun submitMove(gameId: String, edgeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore is not available"))
        val currentGame = _currentSession.value ?: return@withContext Result.failure(IllegalStateException("No active session"))
        val currentPlayers = _playersInSession.value
        val movingPlayerId = currentGame.currentTurn.ifBlank {
            FirebaseManager.auth?.currentUser?.uid ?: currentGame.playerUids.firstOrNull() ?: ""
        }
        if (movingPlayerId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No player identified for move"))
        }

        try {
            // Apply move with anti-cheat engine
            val result = AntiCheatEngine.applyAuthoritativeMove(
                game = currentGame,
                players = currentPlayers,
                playerId = movingPlayerId,
                edgeId = edgeId
            )

            // Write move event
            val currentAuthUid = FirebaseManager.auth?.currentUser?.uid ?: localUid ?: movingPlayerId
            val eventId = "event_${System.currentTimeMillis()}_${Random.nextInt(1000)}"
            val event = OnlineGameEvent(
                eventId = eventId,
                gameId = gameId,
                playerId = currentAuthUid,
                type = "PLAYER_MOVE",
                payload = mapOf(
                    "edgeId" to edgeId,
                    "movingPlayerId" to movingPlayerId,
                    "retainedTurn" to result.retainedTurn.toString(),
                    "completedBoxes" to result.completedBoxIds.joinToString(",")
                ),
                timestamp = System.currentTimeMillis(),
                version = result.updatedGame.version
            )

            val batch = db.batch()
            val gameRef = db.collection("games").document(gameId)
            val eventRef = gameRef.collection("events").document(eventId)

            batch.set(eventRef, event)
            batch.set(gameRef, result.updatedGame, SetOptions.merge())

            // Update scores in player docs
            for (p in result.updatedPlayers) {
                val pRef = gameRef.collection("players").document(p.uid)
                batch.update(pRef, "score", p.score)
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "submitMove error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun resignGame(gameId: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val myUid = FirebaseManager.auth?.currentUser?.uid ?: localUid ?: return@withContext
        val opponent = _playersInSession.value.firstOrNull { it.uid != myUid }
        val gameRef = db.collection("games").document(gameId)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(gameRef)
                val version = snap.getLong("version") ?: 1L
                tx.update(
                    gameRef,
                    mapOf(
                        "status" to OnlineGameStatus.COMPLETED.name,
                        "winnerId" to (opponent?.uid ?: ""),
                        "completedAt" to System.currentTimeMillis(),
                        "version" to version + 1
                    )
                )
                null
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "resignGame error: ${e.message}")
        }
    }

    override suspend fun claimAbandonmentWin(gameId: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val myUid = FirebaseManager.auth?.currentUser?.uid ?: localUid ?: return@withContext
        val gameRef = db.collection("games").document(gameId)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(gameRef)
                val version = snap.getLong("version") ?: 1L
                tx.update(
                    gameRef,
                    mapOf(
                        "status" to OnlineGameStatus.COMPLETED.name,
                        "winnerId" to myUid,
                        "completedAt" to System.currentTimeMillis(),
                        "version" to version + 1
                    )
                )
                null
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "claimAbandonmentWin error: ${e.message}")
        }
    }

    override suspend fun leaveSession() {
        val db = firestore
        val myUid = FirebaseManager.auth?.currentUser?.uid ?: localUid
        val activeGameId = _currentSession.value?.gameId
        if (db != null && myUid != null && activeGameId != null) {
            try {
                db.collection("games").document(activeGameId).collection("players").document(myUid)
                    .update("connected", false, "lastPingAt", 0L)
            } catch (e: Exception) {
                // Ignore if document already closed
            }
        }
        gameListener?.remove()
        playersListener?.remove()
        eventsListener?.remove()
        gameListener = null
        playersListener = null
        eventsListener = null
        _currentSession.value = null
        _playersInSession.value = emptyList()
    }

    override suspend fun sendPresencePing(gameId: String) = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        val myUid = FirebaseManager.auth?.currentUser?.uid ?: return@withContext
        try {
            db.collection("games").document(gameId).collection("players").document(myUid)
                .update("lastPingAt", System.currentTimeMillis(), "connected", true)
        } catch (e: Exception) {
            Log.w(TAG, "Presence ping warning: ${e.message}")
        }
    }

    override suspend fun searchPlayers(query: String): List<PublicPlayerProfile> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext emptyList()
        try {
            val q = query.trim()
            if (q.isBlank()) return@withContext emptyList()
            val snap = db.collection("public_profiles")
                .orderBy("displayName")
                .startAt(q)
                .endAt(q + "\uf8ff")
                .limit(10)
                .get()
                .await()

            snap.documents.mapNotNull { it.toObject(PublicPlayerProfile::class.java) }
        } catch (e: Exception) {
            Log.w(TAG, "Search players error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getOnlinePlayers(): List<PublicPlayerProfile> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext emptyList()
        try {
            db.collection("public_profiles")
                .whereEqualTo("onlineStatus", "ONLINE")
                .limit(12)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(PublicPlayerProfile::class.java) }
        } catch (e: Exception) {
            Log.w(TAG, "Online player lookup error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getFriends(): List<FriendDoc> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext emptyList()
        val myUid = FirebaseManager.auth?.currentUser?.uid ?: return@withContext emptyList()
        try {
            val snap = db.collection("friends").document(myUid).collection("user_friends").get().await()
            snap.documents.mapNotNull { doc ->
                FriendDoc(
                    friendUid = doc.id,
                    displayName = doc.getString("displayName") ?: "Friend",
                    avatarId = doc.getString("avatarId") ?: "piko",
                    level = doc.getLong("level")?.toInt() ?: 1,
                    onlineStatus = doc.getString("onlineStatus") ?: "ONLINE"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Get friends error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun addFriend(targetUid: String, displayName: String, avatarId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("No database"))
        val myUid = FirebaseManager.auth?.currentUser?.uid ?: return@withContext Result.failure(IllegalStateException("Not logged in"))
        try {
            val map = mapOf(
                "displayName" to displayName,
                "avatarId" to avatarId,
                "addedAt" to System.currentTimeMillis()
            )
            db.collection("friends").document(myUid).collection("user_friends").document(targetUid).set(map).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun inviteFriend(targetUid: String, gameId: String, roomCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("No database"))
        val myUid = FirebaseManager.auth?.currentUser?.uid ?: return@withContext Result.failure(IllegalStateException("Not logged in"))
        val myName = _playersInSession.value.find { it.uid == myUid }?.displayName ?: "Player"
        try {
            val inviteId = "inv_${System.currentTimeMillis()}"
            val invite = GameInvite(
                inviteId = inviteId,
                hostUid = myUid,
                hostName = myName,
                targetUid = targetUid,
                gameId = gameId,
                roomCode = roomCode
            )
            db.collection("invitations").document(inviteId).set(invite).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acceptInvite(invite: GameInvite, player: OnlinePlayerDoc): Result<String> = withContext(Dispatchers.IO) {
        joinPrivateGame(player, invite.roomCode)
    }

    private fun listenToGame(gameId: String) {
        val db = firestore ?: return
        gameListener?.remove()
        playersListener?.remove()
        eventsListener?.remove()
        val gameRef = db.collection("games").document(gameId)

        gameListener = gameRef.addSnapshotListener { snap, err ->
            if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
            
            // Auto-transition if a rematch was created for this match
            val rematchId = snap.getString("rematchGameId")
            if (!rematchId.isNullOrBlank() && rematchId != gameId) {
                Log.i(TAG, "Rematch detected ($rematchId), automatically transitioning...")
                listenToGame(rematchId)
                return@addSnapshotListener
            }

            val statusStr = snap.getString("status") ?: OnlineGameStatus.WAITING.name
            val status = try { OnlineGameStatus.valueOf(statusStr) } catch (e: Exception) { OnlineGameStatus.WAITING }

            val doc = OnlineGameDoc(
                gameId = snap.id,
                status = status,
                gameMode = snap.getString("gameMode") ?: "QUICK_MATCH",
                createdBy = snap.getString("createdBy") ?: "",
                roomCode = snap.getString("roomCode"),
                createdAt = snap.getLong("createdAt") ?: 0L,
                startedAt = snap.getLong("startedAt"),
                completedAt = snap.getLong("completedAt"),
                currentTurn = snap.getString("currentTurn") ?: "",
                winnerId = snap.getString("winnerId"),
                isDraw = snap.getBoolean("isDraw") ?: snap.getBoolean("draw") ?: false,
                version = snap.getLong("version") ?: 1L,
                rows = snap.getLong("rows")?.toInt() ?: 3,
                cols = snap.getLong("cols")?.toInt() ?: 3,
                variantId = snap.getString("variantId")
                    ?: "square_${snap.getLong("rows")?.toInt() ?: 3}x${snap.getLong("cols")?.toInt() ?: 3}",
                maxPlayers = snap.getLong("maxPlayers")?.toInt() ?: 2,
                rulesVersion = snap.getLong("rulesVersion")?.toInt() ?: 1,
                turnTimeSeconds = normalizeTurnTimeSeconds(snap.getLong("turnTimeSeconds")?.toInt()),
                rewardMode = normalizeOnlineRewardMode(snap.getString("rewardMode")),
                claimedEdges = (snap.get("claimedEdges") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                boxOwners = (snap.get("boxOwners") as? Map<*, *>)?.mapNotNull { (k, v) ->
                    if (k is String && v is String) k to v else null
                }?.toMap() ?: emptyMap(),
                edgeOwners = (snap.get("edgeOwners") as? Map<*, *>)?.mapNotNull { (k, v) ->
                    if (k is String && v is String) k to v else null
                }?.toMap() ?: emptyMap(),
                playerUids = (snap.get("playerUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                rematchGameId = rematchId,
                rematchRequesterId = snap.getString("rematchRequesterId"),
                rematchStatus = snap.getString("rematchStatus")
            )
            _currentSession.value = doc
        }

        playersListener = gameRef.collection("players").addSnapshotListener { snaps, err ->
            if (err != null || snaps == null) return@addSnapshotListener
            val list = snaps.documents.map { d ->
                OnlinePlayerDoc(
                    uid = d.id,
                    displayName = d.getString("displayName") ?: "Player",
                    avatarId = d.getString("avatarId") ?: "piko",
                    colorHex = d.getString("colorHex") ?: "#FF6B6B",
                    score = d.getLong("score")?.toInt() ?: 0,
                    ready = d.getBoolean("ready") ?: false,
                    connected = d.getBoolean("connected") ?: true,
                    lastPingAt = d.getLong("lastPingAt") ?: System.currentTimeMillis()
                )
            }
            _playersInSession.value = list
            val currentStatus = _currentSession.value?.status
            if ((currentStatus == OnlineGameStatus.WAITING || currentStatus == OnlineGameStatus.MATCHED) &&
                list.size >= 2 && list.all { it.ready }
            ) {
                scope.launch { startGame(gameId) }
            }
        }

        eventsListener = gameRef.collection("events").addSnapshotListener { snaps, err ->
            if (err != null || snaps == null) return@addSnapshotListener
            for (change in snaps.documentChanges) {
                val d = change.document
                val rawPayload = d.get("payload") as? Map<*, *>
                val payloadMap = rawPayload?.entries?.associate {
                    (it.key?.toString() ?: "") to (it.value?.toString() ?: "")
                } ?: emptyMap()
                val event = OnlineGameEvent(
                    eventId = d.id,
                    gameId = gameId,
                    playerId = d.getString("playerId") ?: "",
                    type = d.getString("type") ?: "PLAYER_MOVE",
                    payload = payloadMap,
                    timestamp = d.getLong("timestamp") ?: System.currentTimeMillis(),
                    version = d.getLong("version") ?: 1L
                )
                scope.launch { _gameEvents.emit(event) }
            }
        }
    }

    override suspend fun requestRematch(gameId: String): Result<String> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("No database"))
        val current = _currentSession.value ?: return@withContext Result.failure(IllegalStateException("No session"))
        val myUid = FirebaseManager.auth?.currentUser?.uid ?: localUid ?: return@withContext Result.failure(IllegalStateException("Not logged in"))

        try {
            val oldRef = db.collection("games").document(gameId)
            db.runTransaction { tx ->
                val oldSnap = tx.get(oldRef)
                if (oldSnap.getString("rematchStatus") == "PENDING") {
                    throw IllegalStateException("Rematch request already pending")
                }
                tx.update(oldRef, mapOf(
                    "rematchRequesterId" to myUid,
                    "rematchStatus" to "PENDING",
                    "version" to (oldSnap.getLong("version") ?: 1L) + 1
                ))
                null
            }.await()
            Result.success(gameId)
        } catch (e: Exception) {
            Log.e(TAG, "Request rematch error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun respondRematch(gameId: String, accept: Boolean): Result<String> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("No database"))
        val current = _currentSession.value ?: return@withContext Result.failure(IllegalStateException("No session"))
        val myUid = FirebaseManager.auth?.currentUser?.uid ?: localUid ?: return@withContext Result.failure(IllegalStateException("Not logged in"))
        val oldRef = db.collection("games").document(gameId)
        try {
            if (!accept) {
                db.runTransaction { tx ->
                    val oldSnap = tx.get(oldRef)
                    if (oldSnap.getString("rematchStatus") == "PENDING" && oldSnap.getString("rematchRequesterId") != myUid) {
                        tx.update(oldRef, "rematchStatus", "DECLINED", "version", (oldSnap.getLong("version") ?: 1L) + 1)
                    }
                    null
                }.await()
                return@withContext Result.success("DECLINED")
            }

            val players = _playersInSession.value
            val opponentUid = current.playerUids.firstOrNull { it != myUid } ?: myUid
            val nextFirstTurn = if (current.currentTurn == myUid) opponentUid else myUid
            val newGameId = "match_rematch_${System.currentTimeMillis()}"
            val newRef = db.collection("games").document(newGameId)
            val newGame = mapOf(
                "gameId" to newGameId,
                "status" to OnlineGameStatus.MATCHED.name,
                "gameMode" to current.gameMode,
                "roomCode" to current.roomCode,
                "createdBy" to myUid,
                "currentTurn" to nextFirstTurn,
                "playerUids" to current.playerUids,
                "rows" to current.rows,
                "cols" to current.cols,
                "turnTimeSeconds" to current.turnTimeSeconds,
                "rewardMode" to current.rewardMode,
                "claimedEdges" to emptyList<String>(),
                "boxOwners" to emptyMap<String, String>(),
                "edgeOwners" to emptyMap<String, String>(),
                "version" to 1L,
                "startedAt" to System.currentTimeMillis()
            )
            db.runTransaction { tx ->
                val oldSnap = tx.get(oldRef)
                if (oldSnap.getString("rematchStatus") != "PENDING" || oldSnap.getString("rematchRequesterId") == myUid) {
                    throw IllegalStateException("No rematch request to accept")
                }
                tx.set(newRef, newGame)
                players.forEach { player ->
                    tx.set(newRef.collection("players").document(player.uid), player.copy(score = 0, ready = true))
                }
                tx.update(oldRef, mapOf(
                    "rematchGameId" to newGameId,
                    "rematchStatus" to "ACCEPTED",
                    "version" to (oldSnap.getLong("version") ?: 1L) + 1
                ))
                null
            }.await()
            listenToGame(newGameId)
            startGame(newGameId)
            Result.success(newGameId)
        } catch (e: Exception) {
            Log.e(TAG, "Respond rematch error: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun sendReaction(gameId: String, reaction: Reaction): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("No database"))
        val myUid = FirebaseManager.auth?.currentUser?.uid ?: localUid ?: return@withContext Result.failure(IllegalStateException("Not logged in"))
        try {
            val eventId = "reaction_${System.currentTimeMillis()}_${Random.nextInt(1000)}"
            val event = OnlineGameEvent(
                eventId = eventId,
                gameId = gameId,
                playerId = myUid,
                type = Reaction.EVENT_TYPE,
                payload = mapOf(Reaction.PAYLOAD_KEY to reaction.id),
                timestamp = System.currentTimeMillis(),
                version = 1L
            )
            db.collection("games").document(gameId).collection("events").document(eventId).set(event).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Send reaction error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
