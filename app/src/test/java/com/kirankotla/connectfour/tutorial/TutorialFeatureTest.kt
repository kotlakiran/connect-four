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
    fun testTutorialScenario_horizontalStrike() {
        val state = buildScenarioState(TutorialDemoScenario.HORIZONTAL)
        assertNull("Not won yet", state.winningLine)
        assertEquals(1, state.currentPlayer.id)

        // Drop into target column 4
        val result = GameEngine.applyMove(state, "col_4")
        assertTrue("Horizontal 4-in-a-row achieved", result.isGameOver)
        assertNotNull(result.winningLine)
        assertEquals(4, result.winningLine?.size)
    }

    @Test
    fun testTutorialScenario_verticalTower() {
        val state = buildScenarioState(TutorialDemoScenario.VERTICAL)
        assertNull("Not won yet", state.winningLine)
        assertEquals(1, state.currentPlayer.id)

        // Drop into target column 3 (stacked to row 2)
        val result = GameEngine.applyMove(state, "col_3")
        assertTrue("Vertical 4-in-a-row achieved", result.isGameOver)
        assertNotNull(result.winningLine)
        assertEquals(4, result.winningLine?.size)
    }

    @Test
    fun testTutorialScenario_diagonalClimb() {
        val state = buildScenarioState(TutorialDemoScenario.DIAGONAL)
        assertNull("Not won yet", state.winningLine)
        assertEquals(1, state.currentPlayer.id)

        // Drop into target column 4
        val result = GameEngine.applyMove(state, "col_4")
        assertTrue("Diagonal 4-in-a-row achieved", result.isGameOver)
        assertNotNull(result.winningLine)
        assertEquals(4, result.winningLine?.size)
    }

    @Test
    fun testTutorialScenario_defensiveBlock() {
        val state = buildScenarioState(TutorialDemoScenario.DEFENSE)
        assertNull("Game in progress", state.winningLine)
        assertEquals(1, state.currentPlayer.id)

        // Player 1 drops into col 4 to block opponent
        val result = GameEngine.applyMove(state, "col_4")
        assertFalse("Move blocks rather than immediately winning", result.isGameOver)
        assertEquals(1, result.newState.boardCells["5_4"])
    }
}
