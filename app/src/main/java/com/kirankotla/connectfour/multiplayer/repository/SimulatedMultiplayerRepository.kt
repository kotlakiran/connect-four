package com.kirankotla.connectfour.multiplayer.repository

import com.kirankotla.connectfour.auth.OnlineStatus
import com.kirankotla.connectfour.auth.PublicPlayerProfile
import com.kirankotla.connectfour.model.AiDifficulty
import com.kirankotla.connectfour.model.BoardSpec
import com.kirankotla.connectfour.multiplayer.engine.AntiCheatEngine
import com.kirankotla.connectfour.multiplayer.model.*
import com.kirankotla.connectfour.rules.GameEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class SimulatedMultiplayerRepository(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : MultiplayerRepository {

    private val _currentSession = MutableStateFlow<OnlineGameDoc?>(null)
    override val currentSession: StateFlow<OnlineGameDoc?> = _currentSession.asStateFlow()

    private val _playersInSession = MutableStateFlow<List<OnlinePlayerDoc>>(emptyList())
    override val playersInSession: StateFlow<List<OnlinePlayerDoc>> = _playersInSession.asStateFlow()

    private val _gameEvents = MutableSharedFlow<OnlineGameEvent>(extraBufferCapacity = 64)
    override val gameEvents: SharedFlow<OnlineGameEvent> = _gameEvents.asSharedFlow()

    private val _incomingInvites = MutableStateFlow<List<GameInvite>>(emptyList())
    override val incomingInvites: StateFlow<List<GameInvite>> = _incomingInvites.asStateFlow()

    private var simulatedOpponentJob: Job? = null
    private var simulatedGameCode: String? = null

    override suspend fun startQuickMatch(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int, rewardMode: String): Result<String> {
        val gameId = "sim_${UUID.randomUUID().toString().take(8)}"
        val host = player.copy(colorHex = "#FF5252")
        _currentSession.value = OnlineGameDoc(
            gameId = gameId,
            status = OnlineGameStatus.WAITING,
            gameMode = "QUICK_MATCH",
            createdBy = host.uid,
            currentTurn = host.uid,
            playerUids = listOf(host.uid),
            rows = board.rows,
            cols = board.cols,
            variantId = board.id,
            maxPlayers = board.maxPlayers,
            turnTimeSeconds = normalizeTurnTimeSeconds(turnTimeSeconds),
            rewardMode = resolveRewardModeForGame("QUICK_MATCH", rewardMode)
        )
        _playersInSession.value = listOf(host)

        // Match the ViewModel's 10-second fallback window.
        scope.launch {
            delay(10000L)
            if (_currentSession.value?.gameId == gameId && _currentSession.value?.status == OnlineGameStatus.WAITING) {
                val simulatedPlayer = SimulatedPlayerCatalog.all.random()
                val opponent = OnlinePlayerDoc(
                    uid = "opponent_${UUID.randomUUID().toString().take(6)}",
                    displayName = simulatedPlayer.displayName,
                    avatarId = simulatedPlayer.avatarId,
                    colorHex = "#06B6D4",
                    ready = true,
                    connected = true
                )
                _playersInSession.value = listOf(host, opponent)
                _currentSession.value = _currentSession.value?.copy(
                    status = OnlineGameStatus.MATCHED,
                    playerUids = listOf(host.uid, opponent.uid)
                )
            }
        }

        return Result.success(gameId)
    }

    override suspend fun cancelMatchmaking() {
        simulatedOpponentJob?.cancel()
        leaveSession()
    }

    override suspend fun createPrivateGame(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int, rewardMode: String): Result<Pair<String, String>> {
        val gameId = "sim_priv_${UUID.randomUUID().toString().take(8)}"
        val code = generateRoomCode()
        simulatedGameCode = code
        val host = player.copy(colorHex = "#FF5252")
        _currentSession.value = OnlineGameDoc(
            gameId = gameId,
            status = OnlineGameStatus.WAITING,
            gameMode = "PRIVATE_ROOM",
            createdBy = host.uid,
            roomCode = code,
            currentTurn = host.uid,
            playerUids = listOf(host.uid),
            rows = board.rows,
            cols = board.cols,
            variantId = board.id,
            maxPlayers = board.maxPlayers,
            turnTimeSeconds = normalizeTurnTimeSeconds(turnTimeSeconds),
            rewardMode = resolveRewardModeForGame("PRIVATE_ROOM", rewardMode)
        )
        _playersInSession.value = listOf(host)
        return Result.success(Pair(gameId, code))
    }

    override suspend fun joinPrivateGame(player: OnlinePlayerDoc, roomCode: String): Result<String> {
        val gameId = "sim_priv_${UUID.randomUUID().toString().take(8)}"
        val host = OnlinePlayerDoc(
            uid = "host_friend_1",
            displayName = "Alex",
            avatarId = "mavi",
            colorHex = "#FF5252",
            ready = true
        )
        val guest = player.copy(colorHex = "#06B6D4", ready = true)
        _playersInSession.value = listOf(host, guest)
        _currentSession.value = OnlineGameDoc(
            gameId = gameId,
            status = OnlineGameStatus.MATCHED,
            gameMode = "PRIVATE_ROOM",
            roomCode = roomCode.uppercase(),
            createdBy = host.uid,
            currentTurn = host.uid,
            playerUids = listOf(host.uid, guest.uid),
            rows = 3,
            cols = 3
        )
        return Result.success(gameId)
    }

    override suspend fun setPlayerReady(gameId: String, isReady: Boolean) {
        val current = _currentSession.value ?: return
        val currentPlayers = _playersInSession.value
        val updatedPlayers = currentPlayers.map {
            if (!it.uid.startsWith("opponent_")) it.copy(ready = isReady) else it
        }
        _playersInSession.value = updatedPlayers

        val allReady = updatedPlayers.size >= 2 && updatedPlayers.all { it.ready }
        if (allReady) {
            val updated = current.copy(
                status = OnlineGameStatus.PLAYING,
                startedAt = System.currentTimeMillis()
            )
            _currentSession.value = updated
            val hostUid = updatedPlayers.firstOrNull()?.uid ?: updated.createdBy
            if (updated.currentTurn.isNotBlank() && updated.currentTurn != hostUid) {
                scheduleOpponentMove(updated)
            }
        }
    }

    override suspend fun startGame(gameId: String) {
        val current = _currentSession.value ?: return
        val updated = current.copy(
            status = OnlineGameStatus.PLAYING,
            startedAt = System.currentTimeMillis()
        )
        _currentSession.value = updated
        val hostUid = _playersInSession.value.firstOrNull()?.uid ?: updated.createdBy
        if (updated.currentTurn.isNotBlank() && updated.currentTurn != hostUid) {
            scheduleOpponentMove(updated)
        }
    }

    override suspend fun submitMove(gameId: String, edgeId: String): Result<Unit> {
        val current = _currentSession.value ?: return Result.failure(IllegalStateException("No session"))
        val players = _playersInSession.value
        val hostUid = players.firstOrNull()?.uid ?: return Result.failure(IllegalStateException("No host"))

        try {
            val res = AntiCheatEngine.applyAuthoritativeMove(
                game = current,
                players = players,
                playerId = current.currentTurn,
                edgeId = edgeId
            )
            _currentSession.value = res.updatedGame
            _playersInSession.value = res.updatedPlayers

            val event = OnlineGameEvent(
                eventId = "sim_ev_${System.currentTimeMillis()}",
                gameId = gameId,
                playerId = current.currentTurn,
                type = "PLAYER_MOVE",
                payload = mapOf("edgeId" to edgeId),
                version = res.updatedGame.version
            )
            scope.launch { _gameEvents.emit(event) }

            // Trigger opponent turn if it is opponent's turn and not game over
            if (!res.isGameOver && res.updatedGame.currentTurn != hostUid) {
                scheduleOpponentMove(res.updatedGame)
            }

            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun scheduleOpponentMove(game: OnlineGameDoc) {
        simulatedOpponentJob?.cancel()
        simulatedOpponentJob = scope.launch {
            val opponentUid = game.currentTurn
            val cells = game.boxOwners.mapValues { (_, owner) -> if (owner == game.createdBy) 1 else 2 }
            val dummyState = com.kirankotla.connectfour.model.GameState(
                gameId = game.gameId,
                rows = game.rows,
                cols = game.cols,
                mode = com.kirankotla.connectfour.model.GameMode.VS_AI,
                players = listOf(
                    com.kirankotla.connectfour.model.Player(id = 1, name = "P1", colorHex = "#FF5252", mascotId = "piko"),
                    com.kirankotla.connectfour.model.Player(id = 2, name = "P2", colorHex = "#06B6D4", mascotId = "mavi", isAi = true)
                ),
                currentPlayerIndex = 1,
                boardCells = cells,
                boxOwners = cells,
                claimedEdges = game.claimedEdges.toSet()
            )

            // Dynamic human-like reaction timing:
            // Fast deliberate tap (900ms - 1600ms) when capturing a win
            // Thoughtful deliberation (1800ms - 3200ms) on open or critical boards
            val scoring = com.kirankotla.connectfour.ai.AiPlayer.getScoringEdges(dummyState, GameEngine.getLegalEdges(dummyState))
            val humanDelay = if (scoring.isNotEmpty()) {
                kotlin.random.Random.nextLong(900L, 1600L)
            } else {
                kotlin.random.Random.nextLong(1400L, 2400L)
            }
            delay(humanDelay)

            val active = _currentSession.value ?: return@launch
            if (active.isFinished || active.currentTurn == active.createdBy) return@launch

            // Use human-simulated AI with realistic blunder rate (~14%)
            val move = GameEngine.selectHumanSimulatedMove(dummyState, mistakeRate = 0.14f)
            val col = move.removePrefix("col_").toIntOrNull() ?: 0

            if (move.isNotBlank() && active.claimedEdges.count { it == "col_$col" } < active.rows) {
                val res = AntiCheatEngine.applyAuthoritativeMove(
                    game = active,
                    players = _playersInSession.value,
                    playerId = opponentUid,
                    edgeId = move
                )
                _currentSession.value = res.updatedGame
                _playersInSession.value = res.updatedPlayers

                val event = OnlineGameEvent(
                    eventId = "sim_ev_${System.currentTimeMillis()}",
                    gameId = active.gameId,
                    playerId = opponentUid,
                    type = "PLAYER_MOVE",
                    payload = mapOf("edgeId" to move),
                    version = res.updatedGame.version
                )
                _gameEvents.emit(event)

                // If opponent retained turn, make another move
                if (res.retainedTurn && !res.isGameOver) {
                    scheduleOpponentMove(res.updatedGame)
                }
            }
        }
    }

    override suspend fun resignGame(gameId: String) {
        val current = _currentSession.value ?: return
        val opponentUid = _playersInSession.value.find { it.uid != current.createdBy }?.uid ?: "opponent"
        _currentSession.value = current.copy(
            status = OnlineGameStatus.COMPLETED,
            winnerId = opponentUid,
            completedAt = System.currentTimeMillis()
        )
    }

    override suspend fun claimAbandonmentWin(gameId: String) {
        val current = _currentSession.value ?: return
        _currentSession.value = current.copy(
            status = OnlineGameStatus.COMPLETED,
            winnerId = current.createdBy,
            completedAt = System.currentTimeMillis()
        )
    }

    override suspend fun leaveSession() {
        simulatedOpponentJob?.cancel()
        _currentSession.value = null
        _playersInSession.value = emptyList()
    }

    override suspend fun sendPresencePing(gameId: String) {
        // No-op for simulated mode
    }

    override suspend fun searchPlayers(query: String): List<PublicPlayerProfile> {
        val mockPlayers = listOf(
            PublicPlayerProfile(uid = "user_alex", displayName = "Alex", avatarId = "mavi", level = 12, score = 12450, gamesPlayed = 125, gamesWon = 78, onlineStatus = OnlineStatus.ONLINE),
            PublicPlayerProfile(uid = "user_rahul", displayName = "Rahul", avatarId = "bolt", level = 10, score = 11980, gamesPlayed = 98, gamesWon = 62, onlineStatus = OnlineStatus.ONLINE),
            PublicPlayerProfile(uid = "user_priya", displayName = "Priya", avatarId = "sprout", level = 8, score = 9870, gamesPlayed = 85, gamesWon = 50, onlineStatus = OnlineStatus.OFFLINE),
            PublicPlayerProfile(uid = "user_marcus", displayName = "Marcus", avatarId = "ember", level = 15, score = 15200, gamesPlayed = 180, gamesWon = 120, onlineStatus = OnlineStatus.IN_GAME)
        )
        return mockPlayers.filter { it.displayName.contains(query, ignoreCase = true) }
    }

    override suspend fun getOnlinePlayers(): List<PublicPlayerProfile> =
        searchPlayers("").filter { it.onlineStatus == OnlineStatus.ONLINE }

    override suspend fun getFriends(): List<FriendDoc> {
        return listOf(
            FriendDoc(friendUid = "user_alex", displayName = "Alex", avatarId = "mavi", level = 12, onlineStatus = "ONLINE"),
            FriendDoc(friendUid = "user_rahul", displayName = "Rahul", avatarId = "bolt", level = 10, onlineStatus = "ONLINE"),
            FriendDoc(friendUid = "user_priya", displayName = "Priya", avatarId = "sprout", level = 8, onlineStatus = "OFFLINE")
        )
    }

    override suspend fun addFriend(targetUid: String, displayName: String, avatarId: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun inviteFriend(targetUid: String, gameId: String, roomCode: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun acceptInvite(invite: GameInvite, player: OnlinePlayerDoc): Result<String> {
        return joinPrivateGame(player, invite.roomCode)
    }

    override suspend fun respondRematch(gameId: String, accept: Boolean): Result<String> {
        val current = _currentSession.value ?: return Result.failure(IllegalStateException("No session"))
        if (!accept) {
            _currentSession.value = current.copy(rematchStatus = "DECLINED")
            return Result.success("DECLINED")
        }
        return startSimulatedRematch(gameId)
    }

    override suspend fun requestRematch(gameId: String): Result<String> {
        val current = _currentSession.value ?: return Result.failure(IllegalStateException("No session"))
        val requesterId = _playersInSession.value.firstOrNull()?.uid ?: current.createdBy
        _currentSession.value = current.copy(
            rematchRequesterId = requesterId,
            rematchStatus = "PENDING"
        )
        return Result.success(gameId)
    }

    private suspend fun startSimulatedRematch(gameId: String): Result<String> {
        val current = _currentSession.value ?: return Result.failure(IllegalStateException("No session"))
        val currentPlayers = _playersInSession.value
        val hostUid = current.createdBy
        val opponentUid = currentPlayers.find { it.uid != hostUid }?.uid ?: "opponent"

        // Swap starting turn for fair alternating rematches
        val nextFirstTurn = if (current.currentTurn == hostUid) opponentUid else hostUid

        val resetPlayers = currentPlayers.map { it.copy(score = 0, ready = true) }
        val newGameId = "match_rematch_${System.currentTimeMillis()}"

        val newGame = OnlineGameDoc(
            gameId = newGameId,
            status = OnlineGameStatus.PLAYING,
            gameMode = current.gameMode,
            roomCode = current.roomCode,
            createdBy = hostUid,
            currentTurn = nextFirstTurn,
            playerUids = current.playerUids,
            rows = current.rows,
            cols = current.cols,
            startedAt = System.currentTimeMillis()
        )

        simulatedOpponentJob?.cancel()
        _playersInSession.value = resetPlayers
        _currentSession.value = newGame

        if (nextFirstTurn == opponentUid) {
            scheduleOpponentMove(newGame)
        }

        return Result.success(newGameId)
    }

    override suspend fun sendReaction(gameId: String, reaction: Reaction): Result<Unit> {
        val active = _currentSession.value ?: return Result.failure(IllegalStateException("No session"))
        val myUid = _playersInSession.value.firstOrNull()?.uid ?: active.createdBy
        val event = OnlineGameEvent(
            eventId = "sim_ev_${System.currentTimeMillis()}",
            gameId = gameId,
            playerId = myUid,
            type = Reaction.EVENT_TYPE,
            payload = mapOf(Reaction.PAYLOAD_KEY to reaction.id),
            timestamp = System.currentTimeMillis()
        )
        _gameEvents.emit(event)

        // Occasionally have the simulated opponent respond with a reciprocal reaction!
        scope.launch {
            delay(1200L + kotlin.random.Random.nextLong(800L))
            val current = _currentSession.value ?: return@launch
            if (current.gameId == gameId && !current.isFinished) {
                val opponentUid = _playersInSession.value.find { it.uid != myUid }?.uid ?: return@launch
                val replyReaction = when (reaction) {
                    Reaction.GOOD_LUCK -> Reaction.THUMBS_UP
                    Reaction.GOOD_GAME -> Reaction.GOOD_GAME
                    Reaction.WOW, Reaction.FIRE -> Reaction.THUMBS_UP
                    Reaction.OOPS -> Reaction.THINKING
                    else -> Reaction.values().random()
                }
                val replyEvent = OnlineGameEvent(
                    eventId = "sim_ev_${System.currentTimeMillis()}",
                    gameId = gameId,
                    playerId = opponentUid,
                    type = Reaction.EVENT_TYPE,
                    payload = mapOf(Reaction.PAYLOAD_KEY to replyReaction.id),
                    timestamp = System.currentTimeMillis()
                )
                _gameEvents.emit(replyEvent)
            }
        }
        return Result.success(Unit)
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
