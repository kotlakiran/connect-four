package com.kirankotla.connectfour.tutorial

import com.kirankotla.connectfour.rules.GameEngine
import com.kirankotla.connectfour.ui.screens.TutorialDemoScenario
import com.kirankotla.connectfour.ui.screens.buildScenarioState
import org.junit.Assert.*
import org.junit.Test

class TutorialFeatureTest {

    @Test
    fun testTutorialPracticeBoard_initialState() {
        val practiceState = GameEngine.createInitialState(rows = 6, cols = 7)

        assertEquals(6, practiceState.rows)
        assertEquals(7, practiceState.cols)
        assertEquals(42, practiceState.totalCells)
        assertTrue(practiceState.boardCells.isEmpty())
        assertNull(practiceState.winningLine)
    }

    @Test
    fun testTutorialScenario_forkDoubleThreat() {
        val state = buildScenarioState(TutorialDemoScenario.FORK)
        assertNull("Not won yet", state.winningLine)
        assertEquals(1, state.currentPlayer.id)
        assertTrue("Real match board has at least 10 chips", state.boardCells.size >= 10)

        // Drop into target column 4
        val result = GameEngine.applyMove(state, "col_4")
        assertNotNull(result.winningLine)
        assertTrue("Winning line has at least 4 connected chips", (result.winningLine?.size ?: 0) >= 4)
    }

    @Test
    fun testTutorialScenario_sevenTrap() {
        val state = buildScenarioState(TutorialDemoScenario.SEVEN_TRAP)
        assertNull("Not won yet", state.winningLine)
        assertEquals(1, state.currentPlayer.id)
        assertTrue("Real match board has at least 10 chips", state.boardCells.size >= 10)

        // Drop into target column 3
        val result = GameEngine.applyMove(state, "col_3")
        assertNotNull(result.winningLine)
        assertEquals(4, result.winningLine?.size)
    }

    @Test
    fun testTutorialScenario_midgameDiagonal() {
        val state = buildScenarioState(TutorialDemoScenario.MIDGAME_DIAGONAL)
        assertNull("Not won yet", state.winningLine)
        assertEquals(1, state.currentPlayer.id)
        assertTrue("Real match board has at least 10 chips", state.boardCells.size >= 10)

        // Drop into target column 4
        val result = GameEngine.applyMove(state, "col_4")
        assertNotNull(result.winningLine)
        assertEquals(4, result.winningLine?.size)
    }

    @Test
    fun testTutorialScenario_verticalStack() {
        val state = buildScenarioState(TutorialDemoScenario.VERTICAL_STACK)
        assertNull("Not won yet", state.winningLine)
        assertEquals(1, state.currentPlayer.id)
        assertTrue("Real match board has at least 10 chips", state.boardCells.size >= 10)

        // Drop into target column 3
        val result = GameEngine.applyMove(state, "col_3")
        assertNotNull(result.winningLine)
        assertEquals(4, result.winningLine?.size)
    }

    @Test
    fun testTutorialScenario_defensiveBlock() {
        val state = buildScenarioState(TutorialDemoScenario.DEFENSIVE_BLOCK)
        assertNull("Game in progress", state.winningLine)
        assertEquals(1, state.currentPlayer.id)
        assertTrue("Real match board has at least 10 chips", state.boardCells.size >= 10)

        // Player 1 drops into target col to block opponent
        val result = GameEngine.applyMove(state, "col_2")
        assertFalse("Move blocks opponent rather than immediately winning", result.isGameOver)
    }
}
