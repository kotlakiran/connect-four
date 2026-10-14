package com.kirankotla.connectfour.rules

import com.kirankotla.connectfour.model.GameMode
import com.kirankotla.connectfour.model.TurnRule
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {

    @Test
    fun testInitialState_boardSlots() {
        val state = GameEngine.createInitialState(rows = 6, cols = 7)
        assertEquals(6, state.rows)
        assertEquals(7, state.cols)
        assertEquals(42, state.totalCells)
        assertEquals(0, state.boardCells.size)
        assertFalse(state.isGameOver)
        assertNull(state.winnerId)
    }

    @Test
    fun testSingleMove_dropsToLowestSlotAndPassesTurn() {
        val state = GameEngine.createInitialState(rows = 6, cols = 7)
        val result = GameEngine.applyMove(state, "col_3")

        assertEquals(1, result.newState.boardCells.size)
        assertEquals(1, result.newState.getCell(5, 3)) // Row 5 is lowest row
        assertEquals(1, result.newState.currentPlayerIndex) // Turn passed to Player 2
        assertFalse(result.isGameOver)
    }

    @Test
    fun testWinningLine_connectFourHorizontal() {
        var state = GameEngine.createInitialState(rows = 6, cols = 7)
        // P1 at col 0, P2 at col 0 (row 4)
        state = GameEngine.applyMove(state, "col_0").newState
        state = GameEngine.applyMove(state, "col_0").newState
        // P1 at col 1, P2 at col 1 (row 4)
        state = GameEngine.applyMove(state, "col_1").newState
        state = GameEngine.applyMove(state, "col_1").newState
        // P1 at col 2, P2 at col 2 (row 4)
        state = GameEngine.applyMove(state, "col_2").newState
        state = GameEngine.applyMove(state, "col_2").newState
        // P1 at col 3 -> Connect 4 (row 5: cols 0, 1, 2, 3)
        val result = GameEngine.applyMove(state, "col_3")

        assertTrue(result.isGameOver)
        assertEquals(1, result.newState.winnerId)
        assertNotNull(result.winningLine)
        assertEquals(4, result.winningLine?.size)
    }

    @Test
    fun testWinningLine_connectFourVertical() {
        var state = GameEngine.createInitialState(rows = 6, cols = 7)
        // P1 drops col 2, P2 drops col 3
        state = GameEngine.applyMove(state, "col_2").newState // P1 at row 5
        state = GameEngine.applyMove(state, "col_3").newState // P2
        state = GameEngine.applyMove(state, "col_2").newState // P1 at row 4
        state = GameEngine.applyMove(state, "col_3").newState // P2
        state = GameEngine.applyMove(state, "col_2").newState // P1 at row 3
        state = GameEngine.applyMove(state, "col_3").newState // P2

        // P1 drops 4th chip at col 2 (row 2) -> Connect 4 vertically!
        val result = GameEngine.applyMove(state, "col_2")

        assertTrue(result.isGameOver)
        assertEquals(1, result.newState.winnerId)
        assertNotNull(result.winningLine)
    }

    @Test
    fun testDiceMode_rollAndAllowance() {
        var state = GameEngine.createInitialState(rows = 6, cols = 7, mode = GameMode.DICE_MODE)
        val rng = Random(42)

        state = GameEngine.rollDice(state, rng)
        assertTrue(state.diceState!!.hasRolledThisTurn)
        assertTrue(state.diceState!!.remainingAllowance in 1..3)

        val allowance = state.diceState!!.remainingAllowance
        val result = GameEngine.applyMove(state, "col_0")

        if (allowance > 1) {
            assertTrue(result.turnRetained)
            assertEquals(allowance - 1, result.newState.diceState?.remainingAllowance)
        } else {
            assertFalse(result.turnRetained)
            assertEquals(1, result.newState.currentPlayerIndex) // passed to P2
        }
    }

    @Test
    fun testPassAndPlayMode_twoHumanPlayersAlternate() {
        val state = GameEngine.createInitialState(
            rows = 6,
            cols = 7,
            mode = GameMode.PASS_AND_PLAY
        )

        assertEquals(2, state.players.size)
        assertFalse("Player 1 should not be AI", state.players[0].isAi)
        assertFalse("Player 2 should not be AI", state.players[1].isAi)

        val res1 = GameEngine.applyMove(state, "col_3")
        assertEquals(1, res1.newState.currentPlayerIndex)
        assertFalse(res1.newState.currentPlayer.isAi)

        val res2 = GameEngine.applyMove(res1.newState, "col_4")
        assertEquals(0, res2.newState.currentPlayerIndex)
        assertFalse(res2.newState.currentPlayer.isAi)
    }
}
