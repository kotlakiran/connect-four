package com.kirankotla.connectfour.rules

import com.kirankotla.connectfour.model.GameMode
import com.kirankotla.connectfour.model.reward.JokerCatalog
import org.junit.Assert.*
import org.junit.Test

class PowerUpEngineTest {

    private fun base() = PowerUpEngine.enable(
        GameEngine.createInitialState(rows = 6, cols = 7, mode = GameMode.PASS_AND_PLAY)
    )

    @Test
    fun classicStateHasNoPowerUps() {
        val state = GameEngine.createInitialState(rows = 6, cols = 7, mode = GameMode.PASS_AND_PLAY)
        assertNull(state.powerUpState)
        assertNull(PowerUpEngine.useJoker(state, JokerCatalog.EXTRA_TURN.id))
        val classic = GameEngine.applyMove(state, "col_0")
        val power = PowerUpEngine.applyMove(state, "col_0")
        assertEquals(classic.turnRetained, power.turnRetained)
        assertEquals(classic.newState.currentPlayerIndex, power.newState.currentPlayerIndex)
        assertEquals(classic.newState.boardCells, power.newState.boardCells)
    }

    @Test
    fun extraTurnKeepsTurnOnceWithoutBox() {
        val armed = PowerUpEngine.useJoker(base(), JokerCatalog.EXTRA_TURN.id)!!
        val r1 = PowerUpEngine.applyMove(armed, "col_0")
        assertTrue(r1.turnRetained)
        assertEquals(0, r1.newState.currentPlayerIndex)
        assertNull(r1.newState.powerUpState?.extraTurnPlayerId)
        val r2 = PowerUpEngine.applyMove(r1.newState, "col_1")
        assertEquals(1, r2.newState.currentPlayerIndex)
    }

    @Test
    fun cannotArmExtraTurnTwice() {
        val armed = PowerUpEngine.useJoker(base(), JokerCatalog.EXTRA_TURN.id)!!
        assertFalse(PowerUpEngine.canUse(armed, JokerCatalog.EXTRA_TURN.id))
    }

    @Test
    fun stealBoxReassignsOpponentBoxAndScores() {
        var s = base()
        s = s.copy(
            boxOwners = mapOf("cell_5_0" to 2),
            players = s.players.map { if (it.id == 2) it.copy(score = 1) else it },
            currentPlayerIndex = 0
        )
        val stolen = PowerUpEngine.useJoker(s, JokerCatalog.STEAL_BOX.id)!!
        assertEquals(1, stolen.boxOwners["cell_5_0"])
        assertEquals(1, stolen.players[0].score)
        assertEquals(0, stolen.players[1].score)
        assertEquals(listOf(JokerCatalog.STEAL_BOX.id), stolen.powerUpState!!.usedJokerIds)
    }

    @Test
    fun stealBoxUnavailableWithoutOpponentBox() {
        assertNull(PowerUpEngine.useJoker(base(), JokerCatalog.STEAL_BOX.id))
    }

    @Test
    fun unsupportedJokerRejected() {
        assertFalse(PowerUpEngine.canUse(base(), "non_existent_joker"))
        assertNull(PowerUpEngine.useJoker(base(), "non_existent_joker"))
    }
}
