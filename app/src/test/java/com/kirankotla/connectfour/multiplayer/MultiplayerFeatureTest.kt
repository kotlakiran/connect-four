package com.kirankotla.connectfour.multiplayer

import com.kirankotla.connectfour.model.BoardSpec
import com.kirankotla.connectfour.multiplayer.model.MatchmakingTicket
import com.kirankotla.connectfour.multiplayer.model.OnlineGameDoc
import com.kirankotla.connectfour.multiplayer.model.OnlineGameStatus
import com.kirankotla.connectfour.multiplayer.model.OnlinePlayerDoc
import com.kirankotla.connectfour.multiplayer.model.SUPPORTED_TURN_TIME_SECONDS
import com.kirankotla.connectfour.multiplayer.model.matchmakingTicketPenalty
import com.kirankotla.connectfour.multiplayer.model.normalizeTurnTimeSeconds
import com.kirankotla.connectfour.multiplayer.repository.NearbyMultiplayerRepository
import com.kirankotla.connectfour.multiplayer.repository.SimulatedMultiplayerRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MultiplayerFeatureTest {

    private val player1 = OnlinePlayerDoc(uid = "p1_uid", displayName = "Player 1", colorHex = "#FF5252")
    private val player2 = OnlinePlayerDoc(uid = "p2_uid", displayName = "Player 2", colorHex = "#06B6D4")

    @Test
    fun testSimulatedRepo_quickMatch3x3_4x4_5x5() = runBlocking {
        val repo = SimulatedMultiplayerRepository()

        // Mini 5x4 Match
        val res3 = repo.startQuickMatch(player1, BoardSpec.MINI_5X4)
        assertTrue(res3.isSuccess)
        val session3 = repo.currentSession.value
        assertNotNull(session3)
        assertEquals(4, session3!!.rows)
        assertEquals(5, session3.cols)
        assertEquals(20, session3.totalEdges)
        repo.leaveSession()

        // Compact 6x5 Match
        val res4 = repo.startQuickMatch(player1, BoardSpec.COMPACT_6X5)
        assertTrue(res4.isSuccess)
        val session4 = repo.currentSession.value
        assertNotNull(session4)
        assertEquals(5, session4!!.rows)
        assertEquals(6, session4.cols)
        assertEquals(30, session4.totalEdges)
        repo.leaveSession()

        // Classic 7x6 Match
        val res5 = repo.startQuickMatch(player1, BoardSpec.CLASSIC_7X6)
        assertTrue(res5.isSuccess)
        val session5 = repo.currentSession.value
        assertNotNull(session5)
        assertEquals(6, session5!!.rows)
        assertEquals(7, session5.cols)
        assertEquals(42, session5.totalEdges)
        repo.leaveSession()
    }

    @Test
    fun testSimulatedRepo_privateRoomCreationAndJoin() = runBlocking {
        val repo = SimulatedMultiplayerRepository()

        val createRes = repo.createPrivateGame(player1, BoardSpec.CLASSIC_7X6)
        assertTrue(createRes.isSuccess)
        val (gameId, roomCode) = createRes.getOrThrow()

        assertTrue(gameId.startsWith("sim_priv_"))
        assertEquals(6, roomCode.length)
        assertEquals(roomCode.uppercase(), roomCode)

        val session = repo.currentSession.value
        assertNotNull(session)
        assertEquals(OnlineGameStatus.WAITING, session!!.status)
        assertEquals(roomCode, session.roomCode)
        assertEquals(6, session.rows)
        assertEquals(7, session.cols)

        repo.leaveSession()
        assertNull(repo.currentSession.value)
    }

    @Test
    fun testSimulatedRepo_resignGameAwardsWinner() = runBlocking {
        val repo = SimulatedMultiplayerRepository()
        repo.startQuickMatch(player1, boardSize = 3)

        val activeGameId = repo.currentSession.value!!.gameId
        repo.resignGame(activeGameId)

        val completed = repo.currentSession.value
        assertNotNull(completed)
        assertEquals(OnlineGameStatus.COMPLETED, completed!!.status)
        assertNotNull(completed.winnerId)
        assertNotEquals(player1.uid, completed.winnerId)
    }

    @Test
    fun testSimulatedRepo_claimAbandonmentWinAwardsLocalPlayer() = runBlocking {
        val repo = SimulatedMultiplayerRepository()
        repo.startQuickMatch(player1, boardSize = 3)

        val activeGameId = repo.currentSession.value!!.gameId
        repo.claimAbandonmentWin(activeGameId)

        val completed = repo.currentSession.value
        assertNotNull(completed)
        assertEquals(OnlineGameStatus.COMPLETED, completed!!.status)
        assertEquals(player1.uid, completed.winnerId)
    }

    @Test
    fun testSimulatedRepo_requestRematchSwapsTurnAndResetsScore() = runBlocking {
        val repo = SimulatedMultiplayerRepository()
        repo.startQuickMatch(player1, boardSize = 3)
        val activeGameId = repo.currentSession.value!!.gameId

        val firstTurn = repo.currentSession.value!!.currentTurn

        val rematchRes = repo.requestRematch(activeGameId)
        assertTrue(rematchRes.isSuccess)
        assertEquals("PENDING", repo.currentSession.value!!.rematchStatus)

        val acceptRes = repo.respondRematch(activeGameId, accept = true)
        assertTrue(acceptRes.isSuccess)

        val rematchSession = repo.currentSession.value
        assertNotNull(rematchSession)
        assertEquals(OnlineGameStatus.PLAYING, rematchSession!!.status)
        assertTrue(rematchSession.claimedEdges.isEmpty())
        assertTrue(rematchSession.boxOwners.isEmpty())

        // Turn swapped
        assertNotEquals(firstTurn, rematchSession.currentTurn)

        // Player scores reset
        val players = repo.playersInSession.value
        assertTrue(players.all { it.score == 0 })
    }

    @Test
    fun testNearbyP2P_endpointNameParsingWithBoardSize() {
        val advertisedName = "Alice~ABC123~4"
        val parts = advertisedName.split("~")

        assertEquals(3, parts.size)
        val hostName = parts[0]
        val pin = parts[1]
        val boardSize = parts[2].toIntOrNull() ?: 3

        assertEquals("Alice", hostName)
        assertEquals("ABC123", pin)
        assertEquals(4, boardSize)
    }

    @Test
    fun testSimulatedRepo_pollQuickMatch() = runBlocking {
        val repo = SimulatedMultiplayerRepository()
        val polled = repo.pollQuickMatch(player1, boardSize = 3)
        // Default simulated implementation returns false since it creates instant matches
        assertFalse(polled)
    }

    @Test
    fun turnTime_defaultsAndNormalization() {
        assertEquals(15, OnlineGameDoc().turnTimeSeconds)
        assertEquals(15, MatchmakingTicket().turnTimeSeconds)
        assertEquals(listOf(10, 15, 30), SUPPORTED_TURN_TIME_SECONDS)
        assertEquals(15, normalizeTurnTimeSeconds(null))
        assertEquals(15, normalizeTurnTimeSeconds(7))
        assertEquals(30, normalizeTurnTimeSeconds(30))
    }

    @Test
    fun simulatedRepo_legacyOverloadsUseDefaultTurnTime_explicitUsesSelection() = runBlocking {
        val repo = SimulatedMultiplayerRepository()
        repo.startQuickMatch(player1, boardSize = 3)
        assertEquals(15, repo.currentSession.value!!.turnTimeSeconds)
        repo.leaveSession()

        repo.startQuickMatch(player1, BoardSpec.square(4), 30)
        assertEquals(30, repo.currentSession.value!!.turnTimeSeconds)
        repo.leaveSession()

        val (_, _) = repo.createPrivateGame(player1, BoardSpec.square(3), 10).getOrThrow()
        assertEquals(10, repo.currentSession.value!!.turnTimeSeconds)
    }

    @Test
    fun matchmakingPenalty_prefersExactThenSameBoardThenOthers() {
        val exact = matchmakingTicketPenalty(4, 4, 30, 4, 4, 30)
        val sameBoard = matchmakingTicketPenalty(4, 4, 10, 4, 4, 30)
        val sameTime = matchmakingTicketPenalty(5, 5, 30, 4, 4, 30)
        val neither = matchmakingTicketPenalty(5, 5, 10, 4, 4, 30)
        assertTrue(exact < sameBoard)
        assertTrue(sameBoard < sameTime)
        assertTrue(sameTime < neither)
    }

    @Test
    fun nearbyEndpointName_roundTripsTurnTime_andLegacyDefaults() {
        val name = NearbyMultiplayerRepository.encodeEndpointName("Al", "1234", BoardSpec.square(4), 30)
        val parts = name.split(NearbyMultiplayerRepository.NAME_SEPARATOR)
        assertEquals(30, NearbyMultiplayerRepository.parseEndpointTurnTime(parts))
        assertEquals(15, NearbyMultiplayerRepository.parseEndpointTurnTime(listOf("Al", "1234", "4")))
    }
}
