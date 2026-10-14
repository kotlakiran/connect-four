package com.kirankotla.connectfour.multiplayer

import com.kirankotla.connectfour.model.BoardSpec
import com.kirankotla.connectfour.multiplayer.model.OnlinePlayerDoc
import com.kirankotla.connectfour.multiplayer.repository.NearbyMultiplayerRepository
import com.kirankotla.connectfour.multiplayer.repository.SimulatedMultiplayerRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardSpecSelectionTest {

    private val player = OnlinePlayerDoc(uid = "p1", displayName = "P1", colorHex = "#FF5252")

    @Test
    fun boardSpecOf_resolvesRectangularPresets() {
        assertEquals(BoardSpec.CLASSIC_7X6, BoardSpec.of(6, 7))
        assertEquals(BoardSpec.COMPACT_6X5, BoardSpec.of(5, 6))
        assertEquals(BoardSpec.GRAND_8X7, BoardSpec.of(7, 8, "grand_8x7"))
        assertEquals("custom_5x3", BoardSpec.of(3, 5).id)
    }

    @Test
    fun simulatedRepo_rectangularBoardSpec_setsRowsColsVariant() = runBlocking {
        val repo = SimulatedMultiplayerRepository()
        assertTrue(repo.startQuickMatch(player, BoardSpec.CLASSIC_7X6).isSuccess)
        val session = repo.currentSession.value!!
        assertEquals(6, session.rows)
        assertEquals(7, session.cols)
        assertEquals("classic_7x6", session.variantId)
        repo.leaveSession()

        assertTrue(repo.createPrivateGame(player, BoardSpec.COMPACT_6X5).isSuccess)
        val priv = repo.currentSession.value!!
        assertEquals(5, priv.rows)
        assertEquals(6, priv.cols)
        assertEquals("compact_6x5", priv.variantId)
    }

    @Test
    fun legacyIntOverload_staysSquare() = runBlocking {
        val repo = SimulatedMultiplayerRepository()
        assertTrue(repo.startQuickMatch(player, boardSize = 6).isSuccess)
        val session = repo.currentSession.value!!
        assertEquals(6, session.rows)
        assertEquals(7, session.cols)
        assertEquals(BoardSpec.CLASSIC_7X6.id, session.variantId)
    }

    @Test
    fun nearbyEndpointName_roundTripsRectangularAndLegacy() {
        val name = NearbyMultiplayerRepository.encodeEndpointName("Ann", "1234", BoardSpec.CLASSIC_7X6)
        val parts = name.split(NearbyMultiplayerRepository.NAME_SEPARATOR)
        assertEquals(BoardSpec.CLASSIC_7X6, NearbyMultiplayerRepository.parseEndpointBoard(parts))
    }
}
