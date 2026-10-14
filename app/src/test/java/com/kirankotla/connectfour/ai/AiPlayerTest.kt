package com.kirankotla.connectfour.ai

import com.kirankotla.connectfour.model.AiDifficulty
import com.kirankotla.connectfour.model.Player
import com.kirankotla.connectfour.rules.GameEngine
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class AiPlayerTest {

    @Test
    fun testAllDifficulties_returnLegalEdge() {
        val state = GameEngine.createInitialState(rows = 6, cols = 7)
        val rng = Random(12345)

        AiDifficulty.values().forEach { diff ->
            val edge = AiPlayer.chooseMove(state, difficulty = diff, seedRandom = rng)
            assertTrue("AI $diff returned invalid or claimed edge", !state.isEdgeClaimed(edge))
            assertTrue("Move starts with col_", edge.startsWith("col_"))
        }
    }

    @Test
    fun testEasyAndAbove_takeImmediateWinningMove() {
        // Setup 3 chips for Player 1 in col 0: rows 5, 4, 3 filled. Col 1 has Player 2 chips.
        var state = GameEngine.createInitialState(rows = 6, cols = 7)
        // Alternating moves: P1 in col 0, P2 in col 1
        state = GameEngine.applyMove(state, "col_0").newState // P1 at (5,0)
        state = GameEngine.applyMove(state, "col_1").newState // P2 at (5,1)
        state = GameEngine.applyMove(state, "col_0").newState // P1 at (4,0)
        state = GameEngine.applyMove(state, "col_1").newState // P2 at (4,1)
        state = GameEngine.applyMove(state, "col_0").newState // P1 at (3,0)
        state = GameEngine.applyMove(state, "col_1").newState // P2 at (3,1)

        // It is Player 1's turn. Col 0 has 3 chips in a row (rows 5, 4, 3). Dropping in col 0 completes 4-in-a-row!
        assertEquals(1, state.currentPlayer.id)
        val move = AiPlayer.chooseMove(state, difficulty = AiDifficulty.EASY)
        assertEquals("col_0", move)
    }

    @Test
    fun testGameEngineSelectAiMove_returnsLegalMoveForEasyMediumHard() {
        val state = GameEngine.createInitialState(rows = 6, cols = 7)
        listOf(AiDifficulty.EASY, AiDifficulty.MEDIUM, AiDifficulty.HARD).forEach { diff ->
            val move = GameEngine.selectAiMove(state, difficulty = diff)
            assertTrue("Legal move for $diff", !state.isEdgeClaimed(move))
        }
    }

    @Test
    fun testMediumAi_blocksOpponentWinningThreat() {
        // Setup 3 chips for Player 1 in col 3. It becomes Player 2's turn.
        var state = GameEngine.createInitialState(rows = 6, cols = 7)
        state = GameEngine.applyMove(state, "col_3").newState // P1
        state = GameEngine.applyMove(state, "col_0").newState // P2
        state = GameEngine.applyMove(state, "col_3").newState // P1
        state = GameEngine.applyMove(state, "col_0").newState // P2
        state = GameEngine.applyMove(state, "col_3").newState // P1
        // Now it's P2's turn. P1 will win if they play col_3 next. P2 must block col_3!
        assertEquals(2, state.currentPlayer.id)
        val move = AiPlayer.chooseMove(state, difficulty = AiDifficulty.MEDIUM)
        assertEquals("col_3", move)
    }
}
