package com.kirankotla.connectfour.multiplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kirankotla.connectfour.analytics.ConnectFourAnalytics
import com.kirankotla.connectfour.auth.AuthRepository
import com.kirankotla.connectfour.auth.AuthState
import com.kirankotla.connectfour.auth.PublicPlayerProfile
import com.kirankotla.connectfour.model.BoardSpec
import com.kirankotla.connectfour.multiplayer.model.*
import com.kirankotla.connectfour.multiplayer.repository.MultiplayerRepository
import com.kirankotla.connectfour.network.NetworkMonitor
import com.kirankotla.connectfour.config.FirebaseManager
import android.content.Context
import android.util.Log
import com.kirankotla.connectfour.multiplayer.repository.DefaultMultiplayerRepository
import com.kirankotla.connectfour.multiplayer.repository.NearbyMultiplayerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class MultiplayerViewModel(
    private val repository: MultiplayerRepository,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    val session: StateFlow<OnlineGameDoc?> = repository.currentSession
    val players: StateFlow<List<OnlinePlayerDoc>> = repository.playersInSession
    val multiplayerRepository: MultiplayerRepository get() = repository

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isPreparingMatch = MutableStateFlow(false)
    val isPreparingMatch: StateFlow<Boolean> = _isPreparingMatch.asStateFlow()

    private val _isResigning = MutableStateFlow(false)
    val isResigning: StateFlow<Boolean> = _isResigning.asStateFlow()

    private val _isSimulatedMatch = MutableStateFlow(false)
    val isSimulatedMatch: StateFlow<Boolean> = _isSimulatedMatch.asStateFlow()

    private val _countdown = MutableStateFlow<Int?>(null)
    val countdown: StateFlow<Int?> = _countdown.asStateFlow()

    private val _privateCode = MutableStateFlow<String?>(null)
    val privateCode: StateFlow<String?> = _privateCode.asStateFlow()

    /** True while the current lobby/session is an offline Nearby P2P one. */
    private val _isNearbySession = MutableStateFlow(false)
    val isNearbySession: StateFlow<Boolean> = _isNearbySession.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isConnected

    private val deviceFallbackUid: String = "local_${UUID.randomUUID().toString().take(8)}"

    /** Must be unique per phone, otherwise two signed-out devices collide in P2P matches. */
    val myUid: String
        get() = authRepository.currentUserId?.takeIf { it.isNotBlank() && it != "local_player" } ?: deviceFallbackUid

    private val _disconnectCountdown = MutableStateFlow<Int?>(null)
    val disconnectCountdown: StateFlow<Int?> = _disconnectCountdown.asStateFlow()

    private val _turnTimeRemaining = MutableStateFlow<Int>(DEFAULT_TURN_TIME_SECONDS)
    val turnTimeRemaining: StateFlow<Int> = _turnTimeRemaining.asStateFlow()

    private val reactionThrottle = ReactionThrottle()
    private val _currentReaction = MutableStateFlow<ShownReaction?>(null)
    val currentReaction: StateFlow<ShownReaction?> = _currentReaction.asStateFlow()

    private val _isChatMuted = MutableStateFlow(false)
    val isChatMuted: StateFlow<Boolean> = _isChatMuted.asStateFlow()

    private val _searchElapsedSeconds = MutableStateFlow(0)
    val searchElapsedSeconds: StateFlow<Int> = _searchElapsedSeconds.asStateFlow()

    private val _onlineSearchPlayers = MutableStateFlow<List<PublicPlayerProfile>>(emptyList())
    val onlineSearchPlayers: StateFlow<List<PublicPlayerProfile>> = _onlineSearchPlayers.asStateFlow()

    private var searchTimerJob: Job? = null
    private var countdownJob: Job? = null
    private var pingJob: Job? = null
    private var turnTimerJob: Job? = null
    private var disconnectTimerJob: Job? = null
    private var botMoveJob: Job? = null
    private var isSimulatedOpponent: Boolean = false
    private var isPracticeBotMatch: Boolean = false
    private var activeQuickMatchGameId: String? = null
    private var matchStartTimeMs: Long = 0L

    init {
        // Observe game events (e.g. quick reactions/emotes)
        viewModelScope.launch {
            repository.gameEvents.collect { event ->
                if (event.type == Reaction.EVENT_TYPE) {
                    val reactionId = event.payload[Reaction.PAYLOAD_KEY]
                    val reaction = Reaction.fromId(reactionId)
                    val now = System.currentTimeMillis()
                    if (reaction != null && !_isChatMuted.value && reactionThrottle.isFresh(event.timestamp, now)) {
                        _currentReaction.value = ShownReaction(
                            reaction = reaction,
                            fromUid = event.playerId,
                            key = now
                        )
                        // Clear the reaction after 3.2 seconds
                        delay(3200L)
                        if (_currentReaction.value?.key == now) {
                            _currentReaction.value = null
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.resumeActiveGame()
        }

        // Observe game session state changes for countdown and analytics
        viewModelScope.launch {
            session.collect { doc ->
                if (doc != null) {
                    if (doc.status == OnlineGameStatus.MATCHED && _countdown.value == null) {
                        _isPreparingMatch.value = true
                        _isSearching.value = false
                        searchTimerJob?.cancel()
                        _searchElapsedSeconds.value = 0
                        ConnectFourAnalytics.logMatchFound(doc.gameId, System.currentTimeMillis() - matchStartTimeMs, doc.gameMode)
                        startPreGameCountdown(doc.gameId)
                    }
                    if (doc.status == OnlineGameStatus.PLAYING) {
                        _isPreparingMatch.value = false
                        resetTurnTimer(doc)
                        checkAndTriggerBotMove(doc)
                    }
                    if (doc.status == OnlineGameStatus.COMPLETED) {
                        turnTimerJob?.cancel()
                        botMoveJob?.cancel()
                        disconnectTimerJob?.cancel()
                        _disconnectCountdown.value = null
                        handleGameCompleted(doc)
                    }
                } else {
                    _isPreparingMatch.value = false
                    _countdown.value = null
                    _isSearching.value = false
                    searchTimerJob?.cancel()
                    _searchElapsedSeconds.value = 0
                    _disconnectCountdown.value = null
                    _currentReaction.value = null
                    turnTimerJob?.cancel()
                    disconnectTimerJob?.cancel()
                }
            }
        }

        // Active heartbeat: send ping every 4s during active play
        viewModelScope.launch {
            while (true) {
                delay(4000L)
                val active = session.value
                if (active != null && active.status == OnlineGameStatus.PLAYING) {
                    repository.sendPresencePing(active.gameId)
                }
            }
        }

        // Observe opponent connection status for 60s forfeit timer
        viewModelScope.launch {
            players.collect { currentPlayers ->
                val active = session.value ?: return@collect
                if (active.status != OnlineGameStatus.PLAYING) {
                    disconnectTimerJob?.cancel()
                    _disconnectCountdown.value = null
                    return@collect
                }

                val opponent = currentPlayers.find { it.uid != myUid }
                val isBot = isSimulatedOpponent || opponent?.uid?.startsWith("bot_") == true
                if (isBot) {
                    disconnectTimerJob?.cancel()
                    _disconnectCountdown.value = null
                    return@collect
                }

                if (opponent != null) {
                    val now = System.currentTimeMillis()
                    // A connected player with no legacy timestamp is still present;
                    // only an explicit disconnect or stale real ping starts forfeiture.
                    val isOpponentStale = !opponent.connected ||
                        (opponent.lastPingAt > 0L && now - opponent.lastPingAt > 12000L)
                    
                    if (isOpponentStale && disconnectTimerJob == null) {
                        startDisconnectCountdown(active.gameId)
                    } else if (!isOpponentStale && disconnectTimerJob != null) {
                        // Opponent reconnected! Cancel forfeit timer
                        disconnectTimerJob?.cancel()
                        disconnectTimerJob = null
                        _disconnectCountdown.value = null
                    }
                }
            }
        }

        // Auto reconnect presence and sync when connection returns
        viewModelScope.launch {
            networkMonitor.isConnected.collect { connected ->
                val active = session.value
                if (connected && active != null) {
                    repository.sendPresencePing(active.gameId)
                }
            }
        }
    }

    private var lastTurnGameId: String? = null
    private var lastTurnVersion: Long = -1L

    private fun resetTurnTimer(doc: OnlineGameDoc) {
        if (doc.gameId != lastTurnGameId) {
            lastTurnGameId = doc.gameId
            lastTurnVersion = -1L
        }
        if (doc.version == lastTurnVersion) return
        lastTurnVersion = doc.version
        turnTimerJob?.cancel()
        _turnTimeRemaining.value = doc.turnTimeSeconds

        turnTimerJob = viewModelScope.launch {
            for (t in doc.turnTimeSeconds downTo 1) {
                _turnTimeRemaining.value = t
                delay(1000L)
            }
            _turnTimeRemaining.value = 0
            val latest = session.value ?: return@launch
            if (latest.status != OnlineGameStatus.PLAYING) return@launch

            // If it was my turn and timer ran out, auto-make an emergency move (like Ludo King auto-roll/play)
            if (latest.currentTurn == myUid) {
                autoPlayTurn(latest)
            } else if (_isNearbySession.value) {
                // In P2P: If local player is Host and the opponent timed out, host auto-plays for the timed-out turn to prevent freeze
                val isNearbyHost = (repository as? DefaultMultiplayerRepository)?.isNearbyHost == true ||
                                    (repository as? NearbyMultiplayerRepository)?.isNearbyHost == true
                if (isNearbyHost && latest.currentTurn.isNotBlank()) {
                    autoPlayTurn(latest)
                }
            }
        }
    }

    private fun autoPlayTurn(doc: OnlineGameDoc) {
        // Find first available unclimed edge to keep game moving without hanging
        val dummyState = com.kirankotla.connectfour.model.GameState(
            gameId = doc.gameId,
            rows = doc.rows,
            cols = doc.cols,
            mode = com.kirankotla.connectfour.model.GameMode.VS_AI,
            players = listOf(
                com.kirankotla.connectfour.model.Player(id = 1, name = "P1", colorHex = "#FF6B6B", mascotId = "piko"),
                com.kirankotla.connectfour.model.Player(id = 2, name = "P2", colorHex = "#22B8A7", mascotId = "mavi")
            ),
            claimedEdges = doc.claimedEdges.toSet()
        )
        val move = com.kirankotla.connectfour.rules.GameEngine.selectAiMove(
            dummyState,
            com.kirankotla.connectfour.model.AiDifficulty.EASY
        )
        val autoCol = move.removePrefix("col_").toIntOrNull() ?: 0
        if (move.isNotBlank() && doc.claimedEdges.count { it == "col_$autoCol" } < doc.rows) {
            submitMove(move)
        }
    }

    private fun checkAndTriggerBotMove(doc: OnlineGameDoc) {
        if (repository is com.kirankotla.connectfour.multiplayer.repository.SimulatedMultiplayerRepository) return
        val opponent = players.value.find { it.uid != myUid } ?: return
        val isBot = isSimulatedOpponent || opponent.uid.startsWith("user_guest_") || opponent.uid.startsWith("bot_") || opponent.uid.startsWith("opponent_")
        if (!isBot) return
        if (doc.currentTurn != opponent.uid) return

        botMoveJob?.cancel()
        botMoveJob = viewModelScope.launch {
            val cells = doc.boxOwners.mapValues { (_, owner) -> if (owner == myUid) 1 else 2 }
            val dummyState = com.kirankotla.connectfour.model.GameState(
                gameId = doc.gameId,
                rows = doc.rows,
                cols = doc.cols,
                mode = com.kirankotla.connectfour.model.GameMode.VS_AI,
                players = listOf(
                    com.kirankotla.connectfour.model.Player(id = 1, name = "Player", colorHex = "#FF5252", mascotId = "piko"),
                    com.kirankotla.connectfour.model.Player(id = 2, name = opponent.displayName, colorHex = opponent.colorHex, mascotId = opponent.avatarId, isAi = true)
                ),
                currentPlayerIndex = 1,
                boardCells = cells,
                boxOwners = cells,
                claimedEdges = doc.claimedEdges.toSet()
            )

            val scoring = com.kirankotla.connectfour.ai.AiPlayer.getScoringEdges(dummyState, com.kirankotla.connectfour.rules.GameEngine.getLegalEdges(dummyState))
            val humanDelay = if (scoring.isNotEmpty()) {
                kotlin.random.Random.nextLong(900L, 1600L)
            } else {
                kotlin.random.Random.nextLong(1400L, 2400L)
            }
            delay(humanDelay)

            val current = session.value ?: return@launch
            if (current.status != OnlineGameStatus.PLAYING || current.currentTurn != opponent.uid) return@launch

            val move = com.kirankotla.connectfour.rules.GameEngine.selectHumanSimulatedMove(
                dummyState,
                mistakeRate = 0.14f
            )
            val moveCol = move.removePrefix("col_").toIntOrNull() ?: 0
            if (move.isNotBlank() && current.claimedEdges.count { it == "col_$moveCol" } < current.rows) {
                android.util.Log.d("MultiplayerViewModel", "Submitting bot move: $move for game ${current.gameId}")
                val res = repository.submitMove(current.gameId, move)
                if (res.isFailure) {
                    android.util.Log.e("MultiplayerViewModel", "Bot move failed: ${res.exceptionOrNull()?.message}")
                }
            }
        }
    }

    private val _selectedBoard = MutableStateFlow(BoardSpec.CLASSIC_3X3)
    val selectedBoard: StateFlow<BoardSpec> = _selectedBoard.asStateFlow()

    /** Legacy square-size view of [selectedBoard] (rows). */
    private val _selectedBoardSize = MutableStateFlow(3)
    val selectedBoardSize: StateFlow<Int> = _selectedBoardSize.asStateFlow()

    fun setSelectedBoard(board: BoardSpec) {
        _selectedBoard.value = board
        _selectedBoardSize.value = board.rows
    }

    fun setSelectedBoardSize(size: Int) {
        setSelectedBoard(BoardSpec.square(size.coerceIn(3, 8)))
    }

    private val _selectedTurnTimeSeconds = MutableStateFlow(DEFAULT_TURN_TIME_SECONDS)
    val selectedTurnTimeSeconds: StateFlow<Int> = _selectedTurnTimeSeconds.asStateFlow()

    fun setSelectedTurnTimeSeconds(seconds: Int) {
        _selectedTurnTimeSeconds.value = normalizeTurnTimeSeconds(seconds)
    }

    private val _selectedRewardModes = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Per entry point (QUICK_MATCH / PRIVATE_ROOM / OFFLINE_NEARBY) selected mode; defaults to RANKED. */
    val selectedRewardModes: StateFlow<Map<String, String>> = _selectedRewardModes.asStateFlow()

    fun rewardModeFor(gameMode: String): String =
        resolveRewardModeForGame(gameMode, _selectedRewardModes.value[gameMode])

    fun setRewardMode(gameMode: String, mode: String) {
        _selectedRewardModes.value = _selectedRewardModes.value + (gameMode to resolveRewardModeForGame(gameMode, mode))
    }

    private fun startDisconnectCountdown(gameId: String) {
        disconnectTimerJob?.cancel()
        disconnectTimerJob = viewModelScope.launch {
            for (sec in 60 downTo 1) {
                _disconnectCountdown.value = sec
                delay(1000L)
            }
            _disconnectCountdown.value = 0
            // Opponent failed to reconnect within 60s -> award forfeit win to local player
            repository.claimAbandonmentWin(gameId)
        }
    }

    fun startQuickMatch(boardSize: Int) = startQuickMatch(BoardSpec.square(boardSize))

    fun startQuickMatch(board: BoardSpec = _selectedBoard.value, turnTimeSeconds: Int = _selectedTurnTimeSeconds.value) {
        setSelectedBoard(board)
        setSelectedTurnTimeSeconds(turnTimeSeconds)
        val turnTime = _selectedTurnTimeSeconds.value
        val rewardMode = rewardModeFor("QUICK_MATCH")
        _errorMessage.value = null
        _isSearching.value = true
        _searchElapsedSeconds.value = 0
        _onlineSearchPlayers.value = emptyList()
        isSimulatedOpponent = false
        isPracticeBotMatch = false
        _isSimulatedMatch.value = false
        _isNearbySession.value = false
        (repository as? DefaultMultiplayerRepository)?.disableNearbyMode()
        matchStartTimeMs = System.currentTimeMillis()
        val auth = authRepository.authState.value
        val profile = (auth as? AuthState.Authenticated)?.profile
        val playerDoc = OnlinePlayerDoc(
            uid = myUid,
            displayName = profile?.displayName ?: "Player",
            avatarId = profile?.avatarId ?: "piko",
            colorHex = "#FF5252",
            level = profile?.level ?: 1
        )

        searchTimerJob?.cancel()
        searchTimerJob = viewModelScope.launch {
            // Wait up to 10s for a real opponent before offering simulated fallback
            while (_isSearching.value) {
                delay(1000L)
                _searchElapsedSeconds.value += 1

                // Every 2 seconds while searching and waiting, re-poll for real online opponents
                if (_searchElapsedSeconds.value % 2 == 0) {
                    val current = session.value
                    if (_isSearching.value && current != null && current.status == OnlineGameStatus.WAITING) {
                        try {
                            repository.pollQuickMatch(playerDoc, board, turnTime, rewardMode)
                        } catch (e: Exception) {
                            Log.w("MultiplayerVM", "pollQuickMatch failed: ${e.message}")
                        }
                    }
                }

                if (_searchElapsedSeconds.value % 3 == 0) {
                    val activeSince = System.currentTimeMillis() - 120_000L
                    _onlineSearchPlayers.value = repository.getOnlinePlayers()
                        .filter { it.uid != myUid && it.lastActiveAt >= activeSince }
                        .take(6)
                }

                if (_searchElapsedSeconds.value >= 10) {
                    if (_isSearching.value) {
                        _isPreparingMatch.value = true
                        var matchedRealPlayer = false
                        try {
                            matchedRealPlayer = withTimeoutOrNull(3000L) {
                                repository.pollQuickMatch(playerDoc, board, turnTime, rewardMode)
                            } ?: run {
                                Log.w("MultiplayerVM", "final matchmaking poll timed out")
                                false
                            }
                        } catch (e: Exception) {
                            Log.w("MultiplayerVM", "final pollQuickMatch failed: ${e.message}")
                        }

                        val current = session.value
                        Log.i(
                            "MultiplayerVM",
                            "10s fallback: gameId=${activeQuickMatchGameId ?: "none"}, session=${current?.status}"
                        )
                        if (!matchedRealPlayer && _isSearching.value &&
                            (current == null || current.status == OnlineGameStatus.WAITING)
                        ) {
                            matchWithSimulatedOpponent(board)
                        }
                    }
                    break
                }
            }
        }

        viewModelScope.launch {
            // Start matchmaking in repo
            val res = repository.startQuickMatch(playerDoc, board, turnTime, rewardMode)
            if (res.isFailure) {
                _isSearching.value = false
                searchTimerJob?.cancel()
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to find match"
                return@launch
            }
            activeQuickMatchGameId = res.getOrNull()
            Log.i("MultiplayerVM", "startQuickMatch result=${res.isSuccess}, gameId=${activeQuickMatchGameId ?: "none"}")
        }
    }

    fun practiceVsBot(boardSize: Int) = practiceVsBot(BoardSpec.square(boardSize))

    fun practiceVsBot(board: BoardSpec = _selectedBoard.value) {
        setSelectedBoard(board)
        _errorMessage.value = null
        _isSearching.value = true
        _searchElapsedSeconds.value = 0
        _onlineSearchPlayers.value = emptyList()
        isSimulatedOpponent = true
        isPracticeBotMatch = true
        _isSimulatedMatch.value = true
        _isNearbySession.value = false
        (repository as? DefaultMultiplayerRepository)?.disableNearbyMode()
        matchStartTimeMs = System.currentTimeMillis()
        val auth = authRepository.authState.value
        val profile = (auth as? AuthState.Authenticated)?.profile
        val playerDoc = OnlinePlayerDoc(
            uid = myUid,
            displayName = profile?.displayName ?: "Player",
            avatarId = profile?.avatarId ?: "piko",
            colorHex = "#FF5252"
        )

        viewModelScope.launch {
            val res = repository.startQuickMatch(playerDoc, board, _selectedTurnTimeSeconds.value)
            if (res.isSuccess) {
                delay(400L)
                matchWithSimulatedOpponent(board)
            } else {
                _isSearching.value = false
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to start practice"
            }
        }
    }

    fun matchWithSimulatedOpponent(boardSize: Int) = matchWithSimulatedOpponent(BoardSpec.square(boardSize))

    fun matchWithSimulatedOpponent(board: BoardSpec = _selectedBoard.value) {
        val current = session.value
        val gameId = current?.gameId ?: activeQuickMatchGameId ?: return
        if (current != null && current.status != OnlineGameStatus.WAITING) return
        isSimulatedOpponent = true
        _isSimulatedMatch.value = true
        val simulatedPlayer = SimulatedPlayerCatalog.all.random()
        val opponentUid = "user_guest_${UUID.randomUUID().toString().take(6)}"
        val opponent = OnlinePlayerDoc(
            uid = opponentUid,
            displayName = simulatedPlayer.displayName,
            avatarId = simulatedPlayer.avatarId,
            colorHex = "#06B6D4",
            ready = true,
            connected = true
        )

        if (repository is com.kirankotla.connectfour.multiplayer.repository.SimulatedMultiplayerRepository) {
            // Handled internally by simulated repo
        } else {
            viewModelScope.launch {
                try {
                    val db = FirebaseManager.firestore
                    if (db != null && gameId.startsWith("game_")) {
                        val gameRef = db.collection("games").document(gameId)
                        val playerRef = gameRef.collection("players").document(opponentUid)
                        val claimed = db.runTransaction { transaction ->
                            val gameSnap = transaction.get(gameRef)
                            val status = gameSnap.getString("status") ?: OnlineGameStatus.WAITING.name
                            val playerUids = (gameSnap.get("playerUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                            if (!gameSnap.exists() || status != OnlineGameStatus.WAITING.name || playerUids.size >= 2) {
                                return@runTransaction false
                            }
                            transaction.set(playerRef, opponent)
                            transaction.update(
                                gameRef,
                                mapOf(
                                    "playerUids" to (playerUids + opponentUid).distinct(),
                                    "status" to OnlineGameStatus.MATCHED.name,
                                    "rows" to board.rows,
                                    "cols" to board.cols,
                                    "variantId" to board.id,
                                    "version" to (gameSnap.getLong("version") ?: current?.version ?: 1L) + 1
                                )
                            )
                            true
                        }.await()

                        if (claimed) {
                            val lobbySnap = db.collection("lobbies")
                                .whereEqualTo("gameId", gameId)
                                .get()
                                .await()
                            db.runBatch { batch ->
                                lobbySnap.documents.forEach { batch.delete(it.reference) }
                            }.await()
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MultiplayerVM", "Failed to pair simulated opponent: ${e.message}")
                }
            }
        }
    }

    fun cancelMatchmaking() {
        _isSearching.value = false
        _searchElapsedSeconds.value = 0
        searchTimerJob?.cancel()
        _countdown.value = null
        _privateCode.value = null
        _isNearbySession.value = false
        _isSimulatedMatch.value = false
        activeQuickMatchGameId = null
        viewModelScope.launch {
            repository.cancelMatchmaking()
            (repository as? DefaultMultiplayerRepository)?.disableNearbyMode()
        }
    }

    fun createPrivateGame(boardSize: Int) = createPrivateGame(BoardSpec.square(boardSize))

    fun createPrivateGame(board: BoardSpec = _selectedBoard.value, turnTimeSeconds: Int = _selectedTurnTimeSeconds.value) {
        setSelectedBoard(board)
        setSelectedTurnTimeSeconds(turnTimeSeconds)
        val rewardMode = rewardModeFor("PRIVATE_ROOM")
        _errorMessage.value = null
        _isSearching.value = false
        isSimulatedOpponent = false
        _isSimulatedMatch.value = false
        _isNearbySession.value = false
        _privateCode.value = null
        (repository as? DefaultMultiplayerRepository)?.disableNearbyMode()
        val auth = authRepository.authState.value
        val profile = (auth as? AuthState.Authenticated)?.profile
        val playerDoc = OnlinePlayerDoc(
            uid = myUid,
            displayName = profile?.displayName ?: "Player",
            avatarId = profile?.avatarId ?: "piko",
            colorHex = "#FF5252"
        )

        viewModelScope.launch {
            val res = repository.createPrivateGame(playerDoc, board, _selectedTurnTimeSeconds.value, rewardMode)
            if (res.isSuccess) {
                _privateCode.value = res.getOrNull()?.second
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to create private game"
            }
        }
    }

    fun joinPrivateGame(code: String) {
        if (code.isBlank()) {
            _errorMessage.value = "Please enter room code"
            return
        }
        _errorMessage.value = null
        isSimulatedOpponent = false
        if (_isNearbySession.value) _privateCode.value = null
        _isNearbySession.value = false
        (repository as? DefaultMultiplayerRepository)?.disableNearbyMode()
        val auth = authRepository.authState.value
        val profile = (auth as? AuthState.Authenticated)?.profile
        val playerDoc = OnlinePlayerDoc(
            uid = myUid,
            displayName = profile?.displayName ?: "Player",
            avatarId = profile?.avatarId ?: "piko",
            colorHex = "#06B6D4"
        )

        viewModelScope.launch {
            val res = repository.joinPrivateGame(playerDoc, code.trim())
            if (res.isFailure) {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Invalid room code"
            }
        }
    }

    fun inviteFriend(friendUid: String) {
        val activeSession = session.value ?: return
        val roomCode = _privateCode.value ?: return
        viewModelScope.launch {
            val result = repository.inviteFriend(friendUid, activeSession.gameId, roomCode)
            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Unable to send invite"
            }
        }
    }

    private var nearbyRepoInstance: NearbyMultiplayerRepository? = null
    private val _nearbyRepoFlow = MutableStateFlow<NearbyMultiplayerRepository?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val nearbyHosts: StateFlow<List<NearbyMultiplayerRepository.NearbyHost>> =
        _nearbyRepoFlow.flatMapLatest { it?.discoveredHosts ?: flowOf(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val nearbyStatus: StateFlow<String?> =
        _nearbyRepoFlow.flatMapLatest { it?.status ?: flowOf(null) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val nearbyError: StateFlow<String?> =
        _nearbyRepoFlow.flatMapLatest { it?.error ?: flowOf(null) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun obtainNearbyRepo(context: Context): NearbyMultiplayerRepository {
        val existing = nearbyRepoInstance
        if (existing != null) return existing
        return NearbyMultiplayerRepository(context.applicationContext).also {
            nearbyRepoInstance = it
            _nearbyRepoFlow.value = it
        }
    }

    private fun buildLocalPlayerDoc(colorHex: String): OnlinePlayerDoc {
        val auth = authRepository.authState.value
        val profile = (auth as? AuthState.Authenticated)?.profile
        return OnlinePlayerDoc(
            uid = myUid,
            displayName = profile?.displayName ?: "Player",
            avatarId = profile?.avatarId ?: "piko",
            colorHex = colorHex
        )
    }

    fun startNearbyHost(context: Context, boardSize: Int) =
        startNearbyHost(context, BoardSpec.square(boardSize))

    fun startNearbyHost(context: Context, board: BoardSpec = _selectedBoard.value, turnTimeSeconds: Int = _selectedTurnTimeSeconds.value) {
        setSelectedBoard(board)
        setSelectedTurnTimeSeconds(turnTimeSeconds)
        val rewardMode = rewardModeFor("OFFLINE_NEARBY")
        _errorMessage.value = null
        _privateCode.value = null
        _isNearbySession.value = true
        _isSearching.value = true
        isSimulatedOpponent = false
        val nearbyRepo = obtainNearbyRepo(context)
        val defaultRepo = repository as? DefaultMultiplayerRepository
        defaultRepo?.enableNearbyMode(nearbyRepo)
        val playerDoc = buildLocalPlayerDoc("#FF5252")

        viewModelScope.launch {
            val targetRepo: MultiplayerRepository = defaultRepo ?: nearbyRepo
            val res = targetRepo.createPrivateGame(playerDoc, board, _selectedTurnTimeSeconds.value, rewardMode)
            if (res.isSuccess) {
                _privateCode.value = res.getOrNull()?.second
            } else {
                _isSearching.value = false
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to host nearby match"
            }
        }
    }

    fun startNearbyJoin(context: Context, pin: String = "") {
        _errorMessage.value = null
        _privateCode.value = null // joiner never shows a PIN of its own
        _isNearbySession.value = true
        _isSearching.value = true
        isSimulatedOpponent = false
        val nearbyRepo = obtainNearbyRepo(context)
        val defaultRepo = repository as? DefaultMultiplayerRepository
        defaultRepo?.enableNearbyMode(nearbyRepo)
        val playerDoc = buildLocalPlayerDoc("#06B6D4")

        viewModelScope.launch {
            val targetRepo: MultiplayerRepository = defaultRepo ?: nearbyRepo
            val res = targetRepo.joinPrivateGame(playerDoc, pin)
            if (res.isFailure) {
                _isSearching.value = false
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to discover nearby host"
            }
        }
    }

    fun connectToNearbyHost(endpointId: String) {
        nearbyRepoInstance?.connectToHost(endpointId)
    }

    fun reportPermissionDenied() {
        _isSearching.value = false
        _errorMessage.value = "Nearby needs Bluetooth, Nearby devices and Location permissions. Please allow them (Settings > Apps > Connect Four > Permissions)."
    }

    fun submitMove(edgeId: String) {
        val active = session.value ?: return
        if (active.status != OnlineGameStatus.PLAYING) return
        if (active.currentTurn != myUid) return

        viewModelScope.launch {
            val res = repository.submitMove(active.gameId, edgeId)
            if (res.isFailure) {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage
            }
        }
    }

    fun resign() {
        val active = session.value ?: return
        _isResigning.value = true
        viewModelScope.launch {
            try {
                repository.resignGame(active.gameId)
            } finally {
                _isResigning.value = false
            }
        }
    }

    fun requestRematch() {
        val active = session.value ?: return
        viewModelScope.launch {
            val res = repository.requestRematch(active.gameId)
            if (res.isFailure) {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to initiate rematch"
            }
        }
    }

    fun respondRematch(accept: Boolean) {
        val active = session.value ?: return
        viewModelScope.launch {
            val res = repository.respondRematch(active.gameId, accept)
            if (res.isFailure) {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Unable to respond to rematch"
            }
        }
    }

    fun claimAbandonmentWin() {
        val active = session.value ?: return
        viewModelScope.launch {
            disconnectTimerJob?.cancel()
            _disconnectCountdown.value = null
            repository.claimAbandonmentWin(active.gameId)
        }
    }

    fun leaveSession() {
        countdownJob?.cancel()
        botMoveJob?.cancel()
        isSimulatedOpponent = false
        _countdown.value = null
        _isSearching.value = false
        _privateCode.value = null
        _isNearbySession.value = false
        _currentReaction.value = null
        viewModelScope.launch {
            repository.leaveSession()
            (repository as? DefaultMultiplayerRepository)?.disableNearbyMode()
        }
    }

    fun toggleChatMute() {
        _isChatMuted.value = !_isChatMuted.value
    }

    fun sendReaction(reaction: Reaction) {
        val active = session.value ?: return
        val now = System.currentTimeMillis()
        if (!reactionThrottle.trySend(now)) return
        viewModelScope.launch {
            repository.sendReaction(active.gameId, reaction)
        }
    }

    private fun startPreGameCountdown(gameId: String) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            // Signal player ready
            repository.setPlayerReady(gameId, true)
            val hasSimulatedOpponent = isSimulatedOpponent ||
                players.value.any { it.uid.startsWith("opponent_") || it.uid.startsWith("user_guest_") }
            if (hasSimulatedOpponent) {
                _countdown.value = null
                repository.startGame(gameId)
                ConnectFourAnalytics.logGameStarted(gameId, "ONLINE_SIMULATED", true, "SIMULATED")
                return@launch
            }
            for (i in 3 downTo 1) {
                _countdown.value = i
                delay(950L)
            }
            _countdown.value = 0
            delay(400L)
            _countdown.value = null
            
            // Transition game status to PLAYING so that onGameStart() triggers
            try {
                repository.startGame(gameId)
            } catch (e: Exception) {
                Log.w("MultiplayerVM", "Failed to transition game to PLAYING: ${e.message}")
            }
            ConnectFourAnalytics.logGameStarted(gameId, "ONLINE", true, "HUMAN")
        }
    }

    private fun handleGameCompleted(doc: OnlineGameDoc) {
        val myScore = players.value.find { it.uid == myUid }?.score ?: 0
        val opponent = players.value.find { it.uid != myUid }
        val opponentScore = opponent?.score ?: 0
        val isWin = doc.winnerId == myUid
        val result = if (isWin) "WIN" else if (doc.isDraw) "DRAW" else "LOSS"
        val durationMs = if (doc.startedAt != null) System.currentTimeMillis() - doc.startedAt else 60000L
        val isUnrankedPractice = isPracticeBotMatch ||
            repository is com.kirankotla.connectfour.multiplayer.repository.SimulatedMultiplayerRepository

        ConnectFourAnalytics.logGameCompleted(
            gameId = doc.gameId,
            durationMs = durationMs,
            userScore = myScore,
            opponentScore = opponentScore,
            result = result,
            mode = "ONLINE_MULTIPLAYER"
        )

        if (isUnrankedPractice) return

        viewModelScope.launch {
            authRepository.recordGameResult(
                isWin = isWin,
                scoreDelta = (myScore * 10) + if (isWin) 100 else 20,
                achievementsUnlocked = if (isWin) listOf("first_win") else emptyList()
            )
        }
    }
}
