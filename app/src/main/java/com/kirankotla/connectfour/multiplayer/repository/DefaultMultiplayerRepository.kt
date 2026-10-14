package com.kirankotla.connectfour.multiplayer.repository

import com.kirankotla.connectfour.auth.PublicPlayerProfile
import com.kirankotla.connectfour.config.FirebaseManager
import com.kirankotla.connectfour.model.BoardSpec
import com.kirankotla.connectfour.multiplayer.model.FriendDoc
import com.kirankotla.connectfour.multiplayer.model.GameInvite
import com.kirankotla.connectfour.multiplayer.model.OnlineGameDoc
import com.kirankotla.connectfour.multiplayer.model.OnlineGameEvent
import com.kirankotla.connectfour.multiplayer.model.OnlinePlayerDoc
import com.kirankotla.connectfour.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/**
 * Routes multiplayer calls to Firebase (online), Nearby (offline P2P) or the
 * simulated backend.
 *
 * The backend is pinned when a session starts (quick match / create / join), and
 * the exposed flows dynamically follow the pinned backend. Previously the flows
 * were resolved once at construction time, so the UI kept observing a backend
 * that was no longer receiving updates (breaking Private Rooms and Nearby P2P).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultMultiplayerRepository(
    private val networkMonitor: NetworkMonitor,
    private val firebaseRepo: FirebaseMultiplayerRepository = FirebaseMultiplayerRepository(),
    private val simulatedRepo: SimulatedMultiplayerRepository = SimulatedMultiplayerRepository(),
    private var nearbyRepo: NearbyMultiplayerRepository? = null
) : MultiplayerRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isNearbyMode = false

    private val selectedRepo = MutableStateFlow<MultiplayerRepository>(resolveOnlineRepo())

    fun enableNearbyMode(repo: NearbyMultiplayerRepository? = null) {
        if (repo != null) this.nearbyRepo = repo
        isNearbyMode = true
        nearbyRepo?.let { selectedRepo.value = it }
    }

    fun disableNearbyMode() {
        isNearbyMode = false
        selectedRepo.value = resolveOnlineRepo()
    }

    val isNearbyHost: Boolean
        get() = isNearbyMode && nearbyRepo?.isNearbyHost == true

    private val isFirebaseAvailable: Boolean
        get() = FirebaseManager.isInitialized && FirebaseManager.firestore != null

    private fun resolveOnlineRepo(): MultiplayerRepository =
        if (isFirebaseAvailable) firebaseRepo else simulatedRepo

    /** Picks the backend for a brand-new session and pins it until the next session starts. */
    private fun pinRepoForNewSession(): MultiplayerRepository {
        val nearby = nearbyRepo
        val repo = if (isNearbyMode && nearby != null) nearby else resolveOnlineRepo()
        selectedRepo.value = repo
        return repo
    }

    private val activeRepo: MultiplayerRepository
        get() = selectedRepo.value

    override val currentSession: StateFlow<OnlineGameDoc?> =
        selectedRepo.flatMapLatest { it.currentSession }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override val playersInSession: StateFlow<List<OnlinePlayerDoc>> =
        selectedRepo.flatMapLatest { it.playersInSession }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val gameEvents: SharedFlow<OnlineGameEvent> =
        selectedRepo.flatMapLatest { it.gameEvents }
            .shareIn(scope, SharingStarted.Eagerly)

    override val incomingInvites: StateFlow<List<GameInvite>> =
        selectedRepo.flatMapLatest { it.incomingInvites }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun startQuickMatch(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int, rewardMode: String): Result<String> =
        pinRepoForNewSession().startQuickMatch(player, board, turnTimeSeconds, rewardMode)

    override suspend fun pollQuickMatch(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int, rewardMode: String): Boolean =
        activeRepo.pollQuickMatch(player, board, turnTimeSeconds, rewardMode)

    override suspend fun cancelMatchmaking() =
        activeRepo.cancelMatchmaking()

    override suspend fun createPrivateGame(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int, rewardMode: String): Result<Pair<String, String>> {
        val repo = pinRepoForNewSession()
        if (repo === simulatedRepo) {
            return Result.failure(IllegalStateException(PRIVATE_ROOM_OFFLINE_MSG))
        }
        return repo.createPrivateGame(player, board, turnTimeSeconds, rewardMode)
    }

    override suspend fun joinPrivateGame(player: OnlinePlayerDoc, roomCode: String): Result<String> {
        val repo = pinRepoForNewSession()
        if (repo === simulatedRepo) {
            return Result.failure(IllegalStateException(PRIVATE_ROOM_OFFLINE_MSG))
        }
        return repo.joinPrivateGame(player, roomCode)
    }

    override suspend fun setPlayerReady(gameId: String, isReady: Boolean) =
        activeRepo.setPlayerReady(gameId, isReady)

    override suspend fun startGame(gameId: String) =
        activeRepo.startGame(gameId)

    override suspend fun submitMove(gameId: String, edgeId: String): Result<Unit> =
        activeRepo.submitMove(gameId, edgeId)

    override suspend fun resignGame(gameId: String) =
        activeRepo.resignGame(gameId)

    override suspend fun claimAbandonmentWin(gameId: String) =
        activeRepo.claimAbandonmentWin(gameId)

    override suspend fun leaveSession() =
        activeRepo.leaveSession()

    override suspend fun sendPresencePing(gameId: String) =
        activeRepo.sendPresencePing(gameId)

    override suspend fun searchPlayers(query: String): List<PublicPlayerProfile> =
        resolveOnlineRepo().searchPlayers(query)

    override suspend fun getOnlinePlayers(): List<PublicPlayerProfile> =
        resolveOnlineRepo().getOnlinePlayers()

    override suspend fun getFriends(): List<FriendDoc> =
        resolveOnlineRepo().getFriends()

    override suspend fun addFriend(targetUid: String, displayName: String, avatarId: String): Result<Unit> =
        resolveOnlineRepo().addFriend(targetUid, displayName, avatarId)

    override suspend fun inviteFriend(targetUid: String, gameId: String, roomCode: String): Result<Unit> =
        activeRepo.inviteFriend(targetUid, gameId, roomCode)

    override suspend fun acceptInvite(invite: GameInvite, player: OnlinePlayerDoc): Result<String> =
        pinRepoForNewSession().acceptInvite(invite, player)

    override suspend fun requestRematch(gameId: String): Result<String> =
        activeRepo.requestRematch(gameId)

    override suspend fun respondRematch(gameId: String, accept: Boolean): Result<String> =
        activeRepo.respondRematch(gameId, accept)

    override suspend fun sendReaction(gameId: String, reaction: com.kirankotla.connectfour.multiplayer.model.Reaction): Result<Unit> =
        activeRepo.sendReaction(gameId, reaction)

    companion object {
        const val PRIVATE_ROOM_OFFLINE_MSG =
            "Private rooms need internet. To play offline, use the Nearby P2P tab."
    }
}
