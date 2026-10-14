package com.kirankotla.connectfour.ui

import com.kirankotla.connectfour.model.AiDifficulty
import com.kirankotla.connectfour.model.GameMode
import com.kirankotla.connectfour.model.TurnRule
import com.kirankotla.connectfour.viewmodel.SetupState
import org.junit.Assert.*
import org.junit.Test

class SetupScreenTest {

    @Test
    fun testSetupState_defaultValues() {
        val state = SetupState()
        assertEquals(6, state.rows)
        assertEquals(7, state.cols)
        assertEquals(GameMode.QUICK_PLAY, state.mode)
        assertEquals(TurnRule.STANDARD, state.turnRule)
        assertEquals(AiDifficulty.MEDIUM, state.aiDifficulty)
        assertFalse(state.firstPlayerIsAi)
        assertEquals("Player 1", state.player1Name)
        assertEquals("Player 2", state.player2Name)
        assertEquals("piko", state.player1AvatarId)
        assertEquals("mavi", state.player2AvatarId)
    }

    @Test
    fun testSetupState_gridSizeSelection() {
        var state = SetupState()
        
        // 3x3 selection
        state = state.copy(rows = 3, cols = 3)
        assertEquals(3, state.rows)
        assertEquals(3, state.cols)

        // 4x4 selection
        state = state.copy(rows = 4, cols = 4)
        assertEquals(4, state.rows)
        assertEquals(4, state.cols)

        // 5x5 selection
        state = state.copy(rows = 5, cols = 5)
        assertEquals(5, state.rows)
        assertEquals(5, state.cols)
    }

    @Test
    fun testSetupState_allGameModes() {
        val modes = listOf(
            GameMode.QUICK_PLAY,
            GameMode.VS_AI,
            GameMode.PASS_AND_PLAY,
            GameMode.CAMPAIGN,
            GameMode.DICE_MODE
        )

        for (mode in modes) {
            val state = SetupState(mode = mode)
            assertEquals(mode, state.mode)
        }
    }

    @Test
    fun testSetupState_aiDifficulties() {
        val difficulties = listOf(
            AiDifficulty.BEGINNER,
            AiDifficulty.EASY,
            AiDifficulty.MEDIUM,
            AiDifficulty.HARD,
            AiDifficulty.EXPERT
        )

        for (diff in difficulties) {
            val state = SetupState(aiDifficulty = diff)
            assertEquals(diff, state.aiDifficulty)
        }
    }

    @Test
    fun testSetupState_passAndPlayCustomization() {
        val state = SetupState(
            mode = GameMode.PASS_AND_PLAY,
            player1Name = "Alice",
            player2Name = "Bob",
            player1AvatarId = "luna",
            player2AvatarId = "bolt",
            player2StartsFirst = true
        )

        assertEquals("Alice", state.player1Name)
        assertEquals("Bob", state.player2Name)
        assertEquals("luna", state.player1AvatarId)
        assertEquals("bolt", state.player2AvatarId)
        assertTrue(state.player2StartsFirst)
    }

    @Test
    fun testSetupState_turnRuleSwitching() {
        var state = SetupState(turnRule = TurnRule.STANDARD)
        assertEquals(TurnRule.STANDARD, state.turnRule)

        state = state.copy(turnRule = TurnRule.MAX_LINES)
        assertEquals(TurnRule.MAX_LINES, state.turnRule)
    }
}
