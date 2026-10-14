package com.kirankotla.connectfour.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kirankotla.connectfour.model.AiDifficulty
import com.kirankotla.connectfour.model.GameMode
import com.kirankotla.connectfour.model.TurnRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SetupState(
    val rows: Int = 6,
    val cols: Int = 7,
    val mode: GameMode = GameMode.QUICK_PLAY,
    val turnRule: TurnRule = TurnRule.STANDARD,
    val aiDifficulty: AiDifficulty = AiDifficulty.MEDIUM,
    val firstPlayerIsAi: Boolean = false,
    val themeId: String = "paper_workshop",
    val player1Name: String = "Player 1",
    val player2Name: String = "Player 2",
    val player2StartsFirst: Boolean = false,
    val player1AvatarId: String = "piko",
    val player2AvatarId: String = "mavi",
    val turnTimeSeconds: Int = 15,
    val powerUpMode: Boolean = false
)

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val _setupState = MutableStateFlow(SetupState())
    val setupState: StateFlow<SetupState> = _setupState.asStateFlow()

    fun updateMode(mode: GameMode) {
        _setupState.value = _setupState.value.copy(mode = mode)
    }

    fun updateGridSize(size: Int) {
        _setupState.value = _setupState.value.copy(rows = size, cols = size)
    }

    fun updateDifficulty(difficulty: AiDifficulty) {
        _setupState.value = _setupState.value.copy(aiDifficulty = difficulty)
    }

    fun updateTurnRule(turnRule: TurnRule) {
        _setupState.value = _setupState.value.copy(turnRule = turnRule)
    }

    fun toggleFirstPlayer(isAiFirst: Boolean) {
        _setupState.value = _setupState.value.copy(firstPlayerIsAi = isAiFirst)
    }

    fun updateTheme(themeId: String) {
        _setupState.value = _setupState.value.copy(themeId = themeId)
    }
}
