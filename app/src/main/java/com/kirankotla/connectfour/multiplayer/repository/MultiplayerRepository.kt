package com.kirankotla.connectfour.multiplayer.repository

import com.kirankotla.connectfour.auth.PublicPlayerProfile
import com.kirankotla.connectfour.model.BoardSpec
import com.kirankotla.connectfour.multiplayer.model.DEFAULT_TURN_TIME_SECONDS
import com.kirankotla.connectfour.multiplayer.model.FriendDoc
import com.kirankotla.connectfour.multiplayer.model.GameInvite
import com.kirankotla.connectfour.multiplayer.model.ONLINE_REWARD_MODE_RANKED
import com.kirankotla.connectfour.multiplayer.model.OnlineGameDoc
import com.kirankotla.connectfour.multiplayer.model.OnlineGameEvent
import com.kirankotla.connectfour.multiplayer.model.OnlinePlayerDoc
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface MultiplayerRepository {
    val currentSession: StateFlow<OnlineGameDoc?>
    val playersInSession: StateFlow<List<OnlinePlayerDoc>>
    val gameEvents: SharedFlow<OnlineGameEvent>
    val incomingInvites: StateFlow<List<GameInvite>>

    suspend fun startQuickMatch(player: OnlinePlayerDoc, boardSize: Int = 3): Result<String> =
        startQuickMatch(player, BoardSpec.square(boardSize))
    suspend fun startQuickMatch(player: OnlinePlayerDoc, board: BoardSpec): Result<String> =
        startQuickMatch(player, board, DEFAULT_TURN_TIME_SECONDS)
    suspend fun startQuickMatch(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int): Result<String> =
        startQuickMatch(player, board, turnTimeSeconds, ONLINE_REWARD_MODE_RANKED)
    suspend fun startQuickMatch(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int, rewardMode: String): Result<String>
    suspend fun pollQuickMatch(player: OnlinePlayerDoc, boardSize: Int = 3): Boolean =
        pollQuickMatch(player, BoardSpec.square(boardSize))
    suspend fun pollQuickMatch(player: OnlinePlayerDoc, board: BoardSpec): Boolean =
        pollQuickMatch(player, board, DEFAULT_TURN_TIME_SECONDS)
    suspend fun pollQuickMatch(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int): Boolean =
        pollQuickMatch(player, board, turnTimeSeconds, ONLINE_REWARD_MODE_RANKED)
    suspend fun pollQuickMatch(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int, rewardMode: String): Boolean = false
    suspend fun cancelMatchmaking()
    suspend fun resumeActiveGame(): Boolean = false
    suspend fun createPrivateGame(player: OnlinePlayerDoc, boardSize: Int = 3): Result<Pair<String, String>> =
        createPrivateGame(player, BoardSpec.square(boardSize))
    suspend fun createPrivateGame(player: OnlinePlayerDoc, board: BoardSpec): Result<Pair<String, String>> =
        createPrivateGame(player, board, DEFAULT_TURN_TIME_SECONDS)
    suspend fun createPrivateGame(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int): Result<Pair<String, String>> =
        createPrivateGame(player, board, turnTimeSeconds, ONLINE_REWARD_MODE_RANKED)
    suspend fun createPrivateGame(player: OnlinePlayerDoc, board: BoardSpec, turnTimeSeconds: Int, rewardMode: String): Result<Pair<String, String>>
    suspend fun joinPrivateGame(player: OnlinePlayerDoc, roomCode: String): Result<String>
    suspend fun setPlayerReady(gameId: String, isReady: Boolean)
    suspend fun startGame(gameId: String)
    suspend fun submitMove(gameId: String, edgeId: String): Result<Unit>
    suspend fun resignGame(gameId: String)
    suspend fun claimAbandonmentWin(gameId: String)
    suspend fun leaveSession()
    suspend fun sendPresencePing(gameId: String)
    suspend fun searchPlayers(query: String): List<PublicPlayerProfile>
    suspend fun getOnlinePlayers(): List<PublicPlayerProfile> = emptyList()
    suspend fun getFriends(): List<FriendDoc>
    suspend fun addFriend(targetUid: String, displayName: String, avatarId: String): Result<Unit>
    suspend fun inviteFriend(targetUid: String, gameId: String, roomCode: String): Result<Unit>
    suspend fun acceptInvite(invite: GameInvite, player: OnlinePlayerDoc): Result<String>
    suspend fun requestRematch(gameId: String): Result<String>
    suspend fun respondRematch(gameId: String, accept: Boolean): Result<String>
    suspend fun sendReaction(gameId: String, reaction: com.kirankotla.connectfour.multiplayer.model.Reaction): Result<Unit>
}
