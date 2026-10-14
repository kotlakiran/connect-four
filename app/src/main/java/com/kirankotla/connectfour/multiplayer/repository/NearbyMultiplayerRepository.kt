package com.kirankotla.connectfour.multiplayer.repository

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.kirankotla.connectfour.auth.PublicPlayerProfile
import com.kirankotla.connectfour.model.BoardSpec
import com.kirankotla.connectfour.multiplayer.engine.AntiCheatEngine
import com.kirankotla.connectfour.multiplayer.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Peer-to-Peer Offline Multiplayer over Bluetooth & Local Wi-Fi (LAN / Wi-Fi Direct).
 * Uses Google Nearby Connections API with Strategy.P2P_POINT_TO_POINT.
 * Fully playable offline with zero internet access.
 */
class NearbyMultiplayerRepository(
    private val context: Context
) : MultiplayerRepository {

    private val TAG = "NearbyMultiplayer"
    private val SERVICE_ID = "com.kirankotla.connectfour.nearby"
    private val STRATEGY = Strategy.P2P_STAR

    companion object {
        /** Separates display name and room PIN inside the advertised endpoint name. */
        const val NAME_SEPARATOR = "|"

        /** Endpoint name: name|pin|rows|cols|variantId|turnSeconds|rewardMode (legacy: name|pin|size). */
        fun encodeEndpointName(
            name: String,
            pin: String,
            board: BoardSpec,
            turnTimeSeconds: Int = DEFAULT_TURN_TIME_SECONDS,
            rewardMode: String = ONLINE_REWARD_MODE_RANKED
        ): String =
            listOf(
                name, pin, board.rows, board.cols, board.id, normalizeTurnTimeSeconds(turnTimeSeconds),
                resolveRewardModeForGame(NEARBY_GAME_MODE, rewardMode)
            ).joinToString(NAME_SEPARATOR)

        fun parseEndpointTurnTime(parts: List<String>): Int =
            normalizeTurnTimeSeconds(parts.getOrNull(5)?.toIntOrNull())

        /** Missing (legacy) advertisements default to RANKED. */
        fun parseEndpointRewardMode(parts: List<String>): String =
            resolveRewardModeForGame(NEARBY_GAME_MODE, parts.getOrNull(6))

        private const val NEARBY_GAME_MODE = "OFFLINE_NEARBY"

        fun parseEndpointBoard(parts: List<String>): BoardSpec {
            val rows = parts.getOrNull(2)?.toIntOrNull() ?: 3
            val cols = parts.getOrNull(3)?.toIntOrNull() ?: rows
            return BoardSpec.of(rows, cols, parts.getOrNull(4))
        }
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _currentSession = MutableStateFlow<OnlineGameDoc?>(null)
    override val currentSession: StateFlow<OnlineGameDoc?> = _currentSession

    private val _playersInSession = MutableStateFlow<List<OnlinePlayerDoc>>(emptyList())
    override val playersInSession: StateFlow<List<OnlinePlayerDoc>> = _playersInSession

    private val _gameEvents = MutableSharedFlow<OnlineGameEvent>()
    override val gameEvents: SharedFlow<OnlineGameEvent> = _gameEvents

    private val _incomingInvites = MutableStateFlow<List<GameInvite>>(emptyList())
    override val incomingInvites: StateFlow<List<GameInvite>> = _incomingInvites

    private var connectedEndpointId: String? = null
    private var isHost = false
    val isNearbyHost: Boolean get() = isHost
    private var localPlayer: OnlinePlayerDoc? = null
    private var hostPin: String? = null
    private var targetPin: String? = null
    private var pendingEndpointId: String? = null
    private var hostBoard: BoardSpec = BoardSpec.CLASSIC_3X3
    private var hostTurnTimeSeconds: Int = DEFAULT_TURN_TIME_SECONDS
    private var hostRewardMode: String = ONLINE_REWARD_MODE_RANKED

    /** A host phone found during discovery. */
    data class NearbyHost(
        val endpointId: String,
        val hostName: String,
        val pin: String,
        val boardSize: Int = 3,
        val board: BoardSpec = BoardSpec.square(boardSize),
        val turnTimeSeconds: Int = DEFAULT_TURN_TIME_SECONDS,
        val rewardMode: String = ONLINE_REWARD_MODE_RANKED
    )

    private val _discoveredHosts = MutableStateFlow<List<NearbyHost>>(emptyList())
    val discoveredHosts: StateFlow<List<NearbyHost>> = _discoveredHosts

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with endpoint: $endpointId (${connectionInfo.endpointName})")
            if (connectedEndpointId != null && connectedEndpointId != endpointId) {
                // Already in a match with someone else
                connectionsClient.rejectConnection(endpointId)
                return
            }
            _status.value = "Connecting to ${connectionInfo.endpointName.substringBefore(NAME_SEPARATOR)}..."
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e -> reportError("Could not accept connection", e) }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pendingEndpointId = null
            if (result.status.isSuccess) {
                Log.d(TAG, "Connection successful to endpoint: $endpointId")
                connectedEndpointId = endpointId
                connectionsClient.stopAdvertising()
                connectionsClient.stopDiscovery()
                _status.value = "Connected! Starting match..."
                _error.value = null

                // Send local player profile handshake
                localPlayer?.let { sendHandshake(it) }
            } else {
                Log.w(TAG, "Connection rejected or failed: ${result.status.statusCode} ${result.status.statusMessage}")
                _error.value = "Connection failed (code ${result.status.statusCode}). Try again."
                _status.value = if (isHost) "Waiting for a nearby phone to join..." else "Searching for nearby hosts..."
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Endpoint disconnected: $endpointId")
            if (connectedEndpointId == endpointId) {
                connectedEndpointId = null
                val active = _currentSession.value
                if (active != null && !active.isFinished) {
                    // Mark opponent as disconnected
                    val opponentUid = _playersInSession.value.find { it.uid != localPlayer?.uid }?.uid
                    _playersInSession.value = _playersInSession.value.map {
                        if (it.uid == opponentUid) it.copy(connected = false, lastPingAt = 0L) else it
                    }
                }
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            val jsonStr = String(bytes, StandardCharsets.UTF_8)
            markOpponentAlive()
            handleIncomingPayload(jsonStr)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun handleIncomingPayload(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val type = json.optString("type")

            when (type) {
                "HANDSHAKE" -> {
                    val peerUid = json.getString("uid")
                    val peerName = json.getString("name")
                    val peerAvatar = json.getString("avatar")
                    val peerColor = json.getString("color")

                    val guestDoc = OnlinePlayerDoc(
                        uid = peerUid,
                        displayName = peerName,
                        avatarId = peerAvatar,
                        colorHex = peerColor,
                        ready = true
                    )

                    val myDoc = localPlayer ?: return
                    // Host is always P1 (coral), guest is always P2 (teal) on both phones
                    val hostDoc: OnlinePlayerDoc
                    val guestDocFinal: OnlinePlayerDoc
                    if (isHost) {
                        hostDoc = myDoc.copy(colorHex = "#FF5252", ready = true, connected = true)
                        guestDocFinal = guestDoc.copy(colorHex = "#06B6D4", ready = true, connected = true)
                    } else {
                        hostDoc = guestDoc.copy(colorHex = "#FF5252", ready = true, connected = true)
                        guestDocFinal = myDoc.copy(colorHex = "#06B6D4", ready = true, connected = true)
                    }
                    val updatedGuest = guestDocFinal

                    _playersInSession.value = listOf(hostDoc, guestDocFinal)

                    if (isHost) {
                        sendHandshake(hostDoc)
                        val gameId = "offline_p2p_${System.currentTimeMillis()}"
                        val initialGame = OnlineGameDoc(
                            gameId = gameId,
                            status = OnlineGameStatus.PLAYING,
                            gameMode = "OFFLINE_NEARBY",
                            roomCode = "LOCAL",
                            createdBy = hostDoc.uid,
                            currentTurn = hostDoc.uid,
                            playerUids = listOf(hostDoc.uid, updatedGuest.uid),
                            rows = hostBoard.rows,
                            cols = hostBoard.cols,
                            variantId = hostBoard.id,
                            maxPlayers = hostBoard.maxPlayers,
                            turnTimeSeconds = hostTurnTimeSeconds,
                            rewardMode = hostRewardMode,
                            startedAt = System.currentTimeMillis()
                        )
                        _currentSession.value = initialGame
                        broadcastState(initialGame)
                    }
                }
                "STATE_UPDATE" -> {
                    // Guest receives authoritative state from Host
                    val stateJson = json.getJSONObject("game")
                    val claimedEdgesList = mutableListOf<String>()
                    val claimedArr = stateJson.getJSONArray("claimedEdges")
                    for (i in 0 until claimedArr.length()) claimedEdgesList.add(claimedArr.getString(i))

                    val boxOwnersMap = mutableMapOf<String, String>()
                    val boxOwnersJson = stateJson.optJSONObject("boxOwners")
                    boxOwnersJson?.keys()?.forEach { k -> boxOwnersMap[k] = boxOwnersJson.getString(k) }

                    val edgeOwnersMap = mutableMapOf<String, String>()
                    val edgeOwnersJson = stateJson.optJSONObject("edgeOwners")
                    edgeOwnersJson?.keys()?.forEach { k -> edgeOwnersMap[k] = edgeOwnersJson.getString(k) }

                    val updatedGame = OnlineGameDoc(
                        gameId = stateJson.getString("gameId"),
                        status = OnlineGameStatus.valueOf(stateJson.getString("status")),
                        gameMode = stateJson.optString("gameMode", "OFFLINE_NEARBY"),
                        roomCode = stateJson.optString("roomCode", "LOCAL"),
                        createdBy = stateJson.getString("createdBy"),
                        currentTurn = stateJson.optString("currentTurn", ""),
                        playerUids = listOf(stateJson.getJSONArray("playerUids").getString(0), stateJson.getJSONArray("playerUids").getString(1)),
                        rows = stateJson.getInt("rows"),
                        cols = stateJson.getInt("cols"),
                        variantId = stateJson.optString("variantId", "square_${stateJson.getInt("rows")}x${stateJson.getInt("cols")}"),
                        maxPlayers = stateJson.optInt("maxPlayers", 2),
                        rulesVersion = stateJson.optInt("rulesVersion", 1),
                        turnTimeSeconds = normalizeTurnTimeSeconds(stateJson.optInt("turnTimeSeconds", DEFAULT_TURN_TIME_SECONDS)),
                        rewardMode = resolveRewardModeForGame(NEARBY_GAME_MODE, stateJson.optString("rewardMode", ONLINE_REWARD_MODE_RANKED)),
                        claimedEdges = claimedEdgesList,
                        boxOwners = boxOwnersMap,
                        edgeOwners = edgeOwnersMap,
                        version = stateJson.getLong("version"),
                        winnerId = if (stateJson.has("winnerId") && !stateJson.isNull("winnerId")) stateJson.getString("winnerId") else null,
                        isDraw = stateJson.optBoolean("isDraw", false)
                    )
                    _currentSession.value = updatedGame

                    // Update player scores
                    val p1Score = stateJson.optInt("p1Score", 0)
                    val p2Score = stateJson.optInt("p2Score", 0)
                    _playersInSession.value = _playersInSession.value.mapIndexed { idx, p ->
                        p.copy(score = if (idx == 0) p1Score else p2Score)
                    }
                }
                "MOVE_INTENT" -> {
                    // Host processes guest's move intent authoritatively
                    if (isHost) {
                        val edgeId = json.getString("edgeId")
                        val senderUid = json.getString("senderUid")
                        val active = _currentSession.value ?: return

                        val res = AntiCheatEngine.applyAuthoritativeMove(
                            game = active,
                            players = _playersInSession.value,
                            playerId = senderUid,
                            edgeId = edgeId
                        )
                        _currentSession.value = res.updatedGame
                        _playersInSession.value = res.updatedPlayers
                        broadcastState(res.updatedGame)
                    }
                }
                "RESIGN" -> {
                    val resignUid = json.getString("senderUid")
                    val current = _currentSession.value ?: return
                    val winnerUid = _playersInSession.value.find { it.uid != resignUid }?.uid
                    val completed = current.copy(
                        status = OnlineGameStatus.COMPLETED,
                        winnerId = winnerUid,
                        completedAt = System.currentTimeMillis()
                    )
                    _currentSession.value = completed
                    if (isHost) broadcastState(completed)
                }
                "REMATCH" -> {
                    scope.launch {
                        requestRematch(_currentSession.value?.gameId ?: "")
                    }
                }
                "REACTION" -> {
                    val senderUid = json.getString("senderUid")
                    val reactionId = json.getString("reactionId")
                    val event = OnlineGameEvent(
                        eventId = "nearby_ev_${System.currentTimeMillis()}",
                        gameId = _currentSession.value?.gameId ?: "",
                        playerId = senderUid,
                        type = Reaction.EVENT_TYPE,
                        payload = mapOf(Reaction.PAYLOAD_KEY to reactionId),
                        timestamp = json.optLong("timestamp", System.currentTimeMillis())
                    )
                    scope.launch { _gameEvents.emit(event) }
                }
                "PING" -> { /* liveness handled by markOpponentAlive() */ }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Payload parse error: ${e.message}")
        }
    }

    private fun sendHandshake(player: OnlinePlayerDoc) {
        val json = JSONObject().apply {
            put("type", "HANDSHAKE")
            put("uid", player.uid)
            put("name", player.displayName)
            put("avatar", player.avatarId)
            put("color", player.colorHex)
        }
        sendPayload(json.toString())
    }

    private fun broadcastState(game: OnlineGameDoc) {
        val players = _playersInSession.value
        val json = JSONObject().apply {
            put("type", "STATE_UPDATE")
            val gameJson = JSONObject().apply {
                put("gameId", game.gameId)
                put("status", game.status.name)
                put("gameMode", game.gameMode)
                put("roomCode", game.roomCode)
                put("createdBy", game.createdBy)
                put("currentTurn", game.currentTurn)
                put("playerUids", org.json.JSONArray(game.playerUids))
                put("rows", game.rows)
                put("cols", game.cols)
                put("variantId", game.variantId)
                put("maxPlayers", game.maxPlayers)
                put("rulesVersion", game.rulesVersion)
                put("turnTimeSeconds", game.turnTimeSeconds)
                put("rewardMode", game.rewardMode)
                put("totalEdges", game.totalEdges)
                put("claimedEdges", org.json.JSONArray(game.claimedEdges))
                put("boxOwners", JSONObject(game.boxOwners))
                put("edgeOwners", JSONObject(game.edgeOwners))
                put("version", game.version)
                game.winnerId?.let { put("winnerId", it) }
                put("isDraw", game.isDraw)
                put("p1Score", players.getOrNull(0)?.score ?: 0)
                put("p2Score", players.getOrNull(1)?.score ?: 0)
            }
            put("game", gameJson)
        }
        sendPayload(json.toString())
    }

    private fun sendPayload(payloadStr: String) {
        val endpoint = connectedEndpointId ?: return
        val payload = Payload.fromBytes(payloadStr.toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(endpoint, payload)
            .addOnFailureListener { e -> Log.w(TAG, "sendPayload failed: ${e.message}") }
    }

    private fun markOpponentAlive() {
        val myUid = localPlayer?.uid ?: return
        val now = System.currentTimeMillis()
        _playersInSession.value = _playersInSession.value.map {
            if (it.uid != myUid) it.copy(connected = true, lastPingAt = now) else it
        }
    }

    private fun reportError(prefix: String, e: Exception) {
        val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
        val hint = when (code) {
            ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING,
            ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING -> null // harmless, we restart cleanly
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_COARSE_LOCATION,
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_FINE_LOCATION,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADMIN,
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_WIFI_STATE,
            ConnectionsStatusCodes.MISSING_PERMISSION_CHANGE_WIFI_STATE ->
                "Permission missing. Allow Nearby devices + Location for Connect Four in Settings."
            ConnectionsStatusCodes.STATUS_RADIO_ERROR ->
                "Turn on Bluetooth, Wi-Fi and Location on both phones."
            else -> "$prefix: ${e.localizedMessage ?: "unknown error"}"
        }
        Log.e(TAG, "$prefix (code=$code): ${e.message}")
        if (hint != null) _error.value = hint
    }

    private fun resetConnections() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpointId = null
        pendingEndpointId = null
        _discoveredHosts.value = emptyList()
        _currentSession.value = null
        _playersInSession.value = emptyList()
        _error.value = null
        _status.value = null
    }

    override suspend fun startQuickMatch(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int, rewardMode: String): Result<String> {
        return createPrivateGame(player, board, turnTimeSeconds, rewardMode).map { it.first }
    }

    override suspend fun cancelMatchmaking() {
        resetConnections()
    }

    override suspend fun createPrivateGame(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int, rewardMode: String): Result<Pair<String, String>> {
        resetConnections()
        isHost = true
        localPlayer = player
        hostBoard = board
        hostTurnTimeSeconds = normalizeTurnTimeSeconds(turnTimeSeconds)
        hostRewardMode = resolveRewardModeForGame(NEARBY_GAME_MODE, rewardMode)
        val roomCode = (1000..9999).random().toString()
        hostPin = roomCode

        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        return try {
            connectionsClient.startAdvertising(
                encodeEndpointName(player.displayName, roomCode, board, hostTurnTimeSeconds, hostRewardMode),
                SERVICE_ID,
                connectionLifecycleCallback,
                options
            ).await()
            _status.value = "Waiting for a nearby phone to join..."
            Result.success("offline_p2p_host" to roomCode)
        } catch (e: Exception) {
            reportError("Could not start hosting", e)
            Result.failure(IllegalStateException(_error.value ?: "Could not start hosting"))
        }
    }

    /**
     * Starts discovering nearby hosts. Found hosts appear in [discoveredHosts].
     * If [roomCode] is a 4-digit PIN, auto-connects to the host advertising it.
     */
    override suspend fun joinPrivateGame(player: OnlinePlayerDoc, roomCode: String): Result<String> {
        resetConnections()
        isHost = false
        localPlayer = player
        targetPin = roomCode.trim().takeIf { it.length == 4 && it.all(Char::isDigit) }

        val discoveryCallback = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                if (info.serviceId != SERVICE_ID) return
                val parts = info.endpointName.split(NAME_SEPARATOR)
                val name = parts.getOrNull(0) ?: info.endpointName
                val pin = parts.getOrNull(1) ?: ""
                val board = parseEndpointBoard(parts)
                Log.d(TAG, "Found host $name (PIN $pin, ${board.rows}x${board.cols}) at $endpointId")
                val host = NearbyHost(endpointId, name, pin, board.rows, board, parseEndpointTurnTime(parts), parseEndpointRewardMode(parts))
                _discoveredHosts.value = _discoveredHosts.value.filter { it.endpointId != endpointId } + host
                if (targetPin != null && targetPin == pin) {
                    connectToHost(endpointId)
                }
            }

            override fun onEndpointLost(endpointId: String) {
                _discoveredHosts.value = _discoveredHosts.value.filter { it.endpointId != endpointId }
            }
        }

        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        return try {
            connectionsClient.startDiscovery(SERVICE_ID, discoveryCallback, options).await()
            _status.value = "Searching for nearby hosts..."
            Result.success("offline_p2p_guest")
        } catch (e: Exception) {
            reportError("Could not start searching", e)
            Result.failure(IllegalStateException(_error.value ?: "Could not start searching"))
        }
    }

    /** Guest: request a connection to the selected host. */
    fun connectToHost(endpointId: String) {
        val player = localPlayer ?: return
        if (connectedEndpointId != null || pendingEndpointId != null) return
        pendingEndpointId = endpointId
        _error.value = null
        _status.value = "Requesting to join..."
        connectionsClient.requestConnection(player.displayName, endpointId, connectionLifecycleCallback)
            .addOnFailureListener { e ->
                pendingEndpointId = null
                reportError("Could not connect to host", e)
            }
    }

    override suspend fun setPlayerReady(gameId: String, isReady: Boolean) {}

    override suspend fun startGame(gameId: String) {
        val current = _currentSession.value ?: return
        if (current.status != OnlineGameStatus.PLAYING) {
            val started = current.copy(
                status = OnlineGameStatus.PLAYING,
                startedAt = System.currentTimeMillis()
            )
            _currentSession.value = started
            if (isHost) {
                broadcastState(started)
            }
        }
    }

    override suspend fun submitMove(gameId: String, edgeId: String): Result<Unit> {
        val current = _currentSession.value ?: return Result.failure(IllegalStateException("No session"))
        val myUid = localPlayer?.uid ?: return Result.failure(IllegalStateException("No local player"))

        if (isHost) {
            val movingPlayerId = if (current.currentTurn.isNotBlank()) current.currentTurn else myUid
            val res = AntiCheatEngine.applyAuthoritativeMove(
                game = current,
                players = _playersInSession.value,
                playerId = movingPlayerId,
                edgeId = edgeId
            )
            _currentSession.value = res.updatedGame
            _playersInSession.value = res.updatedPlayers
            broadcastState(res.updatedGame)
        } else {
            val intentJson = JSONObject().apply {
                put("type", "MOVE_INTENT")
                put("edgeId", edgeId)
                put("senderUid", myUid)
            }
            sendPayload(intentJson.toString())
        }
        return Result.success(Unit)
    }

    override suspend fun resignGame(gameId: String) {
        val myUid = localPlayer?.uid ?: return
        val current = _currentSession.value ?: return
        val opponentUid = _playersInSession.value.find { it.uid != myUid }?.uid
        val completed = current.copy(
            status = OnlineGameStatus.COMPLETED,
            winnerId = opponentUid,
            completedAt = System.currentTimeMillis()
        )
        _currentSession.value = completed

        val json = JSONObject().apply {
            put("type", "RESIGN")
            put("senderUid", myUid)
        }
        sendPayload(json.toString())
    }

    override suspend fun claimAbandonmentWin(gameId: String) {
        val myUid = localPlayer?.uid ?: return
        val current = _currentSession.value ?: return
        val completed = current.copy(
            status = OnlineGameStatus.COMPLETED,
            winnerId = myUid,
            completedAt = System.currentTimeMillis()
        )
        _currentSession.value = completed

        if (isHost) {
            broadcastState(completed)
        }
    }

    override suspend fun leaveSession() {
        resetConnections()
    }

    override suspend fun sendPresencePing(gameId: String) {
        sendPayload(JSONObject().apply { put("type", "PING") }.toString())
    }

    override suspend fun searchPlayers(query: String): List<PublicPlayerProfile> = emptyList()

    override suspend fun getFriends(): List<FriendDoc> = emptyList()

    override suspend fun addFriend(targetUid: String, displayName: String, avatarId: String): Result<Unit> = Result.success(Unit)

    override suspend fun inviteFriend(targetUid: String, gameId: String, roomCode: String): Result<Unit> = Result.success(Unit)

    override suspend fun acceptInvite(invite: GameInvite, player: OnlinePlayerDoc): Result<String> = Result.success("")

    override suspend fun requestRematch(gameId: String): Result<String> {
        val current = _currentSession.value ?: return Result.failure(IllegalStateException("No session"))
        val players = _playersInSession.value
        val hostUid = current.createdBy
        val opponentUid = players.find { it.uid != hostUid }?.uid ?: "guest"
        val nextFirstTurn = if (current.currentTurn == hostUid) opponentUid else hostUid

        val newGameId = "offline_rematch_${System.currentTimeMillis()}"
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
            turnTimeSeconds = current.turnTimeSeconds,
            rewardMode = current.rewardMode,
            startedAt = System.currentTimeMillis()
        )

        _playersInSession.value = players.map { it.copy(score = 0, ready = true) }
        _currentSession.value = newGame

        if (isHost) {
            broadcastState(newGame)
        } else {
            val json = JSONObject().apply { put("type", "REMATCH") }
            sendPayload(json.toString())
        }

        return Result.success(newGameId)
    }

    override suspend fun respondRematch(gameId: String, accept: Boolean): Result<String> =
        if (accept) requestRematch(gameId) else Result.success("DECLINED")

    override suspend fun sendReaction(gameId: String, reaction: Reaction): Result<Unit> {
        val myUid = localPlayer?.uid ?: return Result.failure(IllegalStateException("No player"))
        val json = JSONObject().apply {
            put("type", "REACTION")
            put("senderUid", myUid)
            put("reactionId", reaction.id)
            put("timestamp", System.currentTimeMillis())
        }
        sendPayload(json.toString())
        val event = OnlineGameEvent(
            eventId = "nearby_ev_${System.currentTimeMillis()}",
            gameId = gameId,
            playerId = myUid,
            type = Reaction.EVENT_TYPE,
            payload = mapOf(Reaction.PAYLOAD_KEY to reaction.id),
            timestamp = System.currentTimeMillis()
        )
        _gameEvents.emit(event)
        return Result.success(Unit)
    }
}
