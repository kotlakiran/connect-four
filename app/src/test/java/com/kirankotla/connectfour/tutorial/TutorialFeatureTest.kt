package com.kirankotla.connectfour.tutorial

import com.kirankotla.connectfour.rules.GameEngine
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
    fun testTutorialPracticeBoard_completionCondition() {
        var practiceState = GameEngine.createInitialState(rows = 6, cols = 7)
        assertNull("Tutorial is not completed initially", practiceState.winningLine)

        // Drop chips to form 4-in-a-row
        practiceState = GameEngine.applyMove(practiceState, "col_0").newState // P1 (r5, c0)
        practiceState = GameEngine.applyMove(practiceState, "col_0").newState // P2 (r4, c0)
        practiceState = GameEngine.applyMove(practiceState, "col_1").newState // P1 (r5, c1)
        practiceState = GameEngine.applyMove(practiceState, "col_1").newState // P2 (r4, c1)
        practiceState = GameEngine.applyMove(practiceState, "col_2").newState // P1 (r5, c2)
        practiceState = GameEngine.applyMove(practiceState, "col_2").newState // P2 (r4, c2)
        val result = GameEngine.applyMove(practiceState, "col_3") // P1 (r5, c3) -> 4 in a row!

        assertTrue("Tutorial completion triggers when 4-in-a-row is achieved", result.isGameOver)
        assertNotNull(result.winningLine)
        assertEquals(4, result.winningLine?.size)
    }
}
