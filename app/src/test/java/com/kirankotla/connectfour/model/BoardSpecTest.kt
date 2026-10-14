package com.kirankotla.connectfour.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardSpecTest {
    @Test
    fun rectangularBoardCalculatesBoxesEdgesAndDots() {
        val board = BoardSpec.GRAND_8X7

        assertEquals(56, board.totalBoxes)
        assertEquals(56, board.totalCells)
        assertEquals(7, board.dotRows)
        assertEquals(8, board.dotCols)
        assertEquals("8 x 7 (4-in-a-row)", board.label)
        assertEquals("7 rows, 8 cols", board.dotLabel)
    }

    @Test
    fun boardPresetsIntegrity() {
        assertEquals(2, BoardSpec.CLASSIC_7X6.maxPlayers)
        assertEquals(5, BoardSpec.multiplayerPresets.size)
        assertTrue(BoardSpec.multiplayerPresets.any { it.id == "classic_7x6" })
        assertTrue(BoardSpec.multiplayerPresets.any { it.id == "compact_6x5" })
    }
}
