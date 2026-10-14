package com.kirankotla.connectfour.rules

import com.kirankotla.connectfour.model.GameMode
import com.kirankotla.connectfour.model.GameState
import com.kirankotla.connectfour.model.MoveRecord
import com.kirankotla.connectfour.model.Player
import org.junit.Assert.*
import org.junit.Test

class MoveInsightsTest {

    private fun createInitialState(rows: Int = 6, cols: Int = 7): GameState {
        return GameState(
            gameId = "test_game",
            rows = rows,
            cols = cols,
            mode = GameMode.PASS_AND_PLAY,
            players = listOf(
                Player(1, "Player 1", "#FF5252", mascotId = "piko"),
                Player(2, "Player 2", "#06B6D4", mascotId = "bolt")
            )
        )
    }

    @Test
    fun testTrailingChainCount_isZeroForConnectFour() {
        val history = listOf(
            MoveRecord("col_3", 1, emptyList(), col = 3)
        )
        assertEquals(0, MoveInsights.trailingChainCount(history))
    }

    @Test
    fun testRebuildHistoryAndMatchStats() {
        val rows = 6
        val cols = 7
        val edges = listOf("col_0", "col_1", "col_0", "col_1")
        val reconstructed = MoveInsights.rebuildHistory(rows, cols, edges) { index, _ ->
            if (index % 2 == 0) 1 else 2
        }

        assertEquals(4, reconstructed.size)
        assertEquals("col_0", reconstructed[0].edgeId)
        assertEquals(0, reconstructed[0].col)
        assertEquals(1, reconstructed[0].playerId)

        var state = createInitialState(rows, cols)
        edges.forEach { edge ->
            state = GameEngine.applyMove(state, edge).newState
        }

        val stats = MoveInsights.matchStats(state)
        assertEquals(2, stats.size)
        assertEquals(2, stats[1]?.chipsPlaced)
        assertEquals(2, stats[2]?.chipsPlaced)
    }

    @Test
    fun testCalculateLongestStreak() {
        val cells = mapOf(
            "5_0" to 1,
            "5_1" to 1,
            "5_2" to 1
        )
        val streak = MoveInsights.calculateLongestStreak(cells, playerId = 1, rows = 6, cols = 7)
        assertEquals(3, streak)
    }
}
