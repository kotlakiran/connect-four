package com.kirankotla.connectfour.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kirankotla.connectfour.ai.AiPlayer
import com.kirankotla.connectfour.data.ConnectFourDatabase
import com.kirankotla.connectfour.data.PlayerPreferences
import com.kirankotla.connectfour.data.ProgressionProcessor
import com.kirankotla.connectfour.data.SavedGameEntity
import com.kirankotla.connectfour.model.*
import com.kirankotla.connectfour.rules.GameEngine
import com.kirankotla.connectfour.util.HapticManager
import com.kirankotla.connectfour.util.SoundManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ConnectFourDatabase.getDatabase(application)
    private val dao = db.connectFourDao()
    private val preferences = PlayerPreferences(application)
    private val processor = ProgressionProcessor(dao, preferences)
    private val soundManager = SoundManager(application)
    private val hapticManager = HapticManager(application)

    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private val _hintEdgeId = MutableStateFlow<String?>(null)
    val hintEdgeId: StateFlow<String?> = _hintEdgeId.asStateFlow()

    private var aiJob: Job? = null
    private var hintsUsed = 0
    private var undosUsed = 0
    private var matchStartTimeMs = System.currentTimeMillis()

    fun startNewGame(
        rows: Int,
        cols: Int,
        mode: GameMode,
        turnRule: TurnRule = TurnRule.STANDARD,
        aiDifficulty: AiDifficulty = AiDifficulty.MEDIUM,
        firstPlayerIsAi: Boolean = false
    ) {
        val initial = GameEngine.createInitialState(
            rows = rows,
            cols = cols,
            mode = mode,
            turnRule = turnRule,
            aiDifficulty = aiDifficulty
        )

        val updatedState = if (firstPlayerIsAi && initial.players.size >= 2) {
            initial.copy(currentPlayerIndex = 1)
        } else initial

        _gameState.value = updatedState
        hintsUsed = 0
        undosUsed = 0
        matchStartTimeMs = System.currentTimeMillis()

        saveSnapshot(updatedState)
        checkAndTriggerAiMove(updatedState)
    }

    fun onEdgeTap(edgeId: String) {
        val current = _gameState.value ?: return
        if (current.isGameOver || current.isEdgeClaimed(edgeId)) return
        if (current.currentPlayer.isAi) return

        executeMove(current, edgeId)
    }

    fun onDiceRollTap() {
        val current = _gameState.value ?: return
        if (current.mode != GameMode.DICE_MODE || current.isGameOver) return

        soundManager.playDiceRoll()
        hapticManager.performDiceRoll()

        val rolledState = GameEngine.rollDice(current)
        _gameState.value = rolledState
        saveSnapshot(rolledState)

        checkAndTriggerAiMove(rolledState)
    }

    fun onEndTurnEarlyTap() {
        val current = _gameState.value ?: return
        if (current.mode != GameMode.DICE_MODE) return

        soundManager.playTurnChange()
        val nextState = GameEngine.endTurnEarlyInDiceMode(current)
        _gameState.value = nextState
        saveSnapshot(nextState)
        checkAndTriggerAiMove(nextState)
    }

    private fun executeMove(state: GameState, edgeId: String) {
        val previousPlayerIndex = state.currentPlayerIndex
        val moveResult = GameEngine.applyMove(state, edgeId)
        val nextState = moveResult.newState
        val nextPlayerIndex = nextState.currentPlayerIndex

        soundManager.playLineStroke()
        hapticManager.performLineStroke()

        if (moveResult.claimedBoxes.isNotEmpty()) {
            if (moveResult.claimedBoxes.size >= 2) {
                soundManager.playDoubleBoxFlourish()
            } else {
                soundManager.playGridCapture()
            }
            hapticManager.performBoxClaim()
        }

        _gameState.value = nextState
        _hintEdgeId.value = null
        saveSnapshot(nextState)

        if (moveResult.isGameOver) {
            soundManager.playGameWin()
            val durationMs = System.currentTimeMillis() - matchStartTimeMs
            viewModelScope.launch {
                processor.processMatchCompletion(
                    state = nextState,
                    humanPlayerId = 1,
                    durationMs = durationMs,
                    hintsUsed = hintsUsed,
                    undosUsed = undosUsed
                )
            }
        } else {
            if (previousPlayerIndex != nextPlayerIndex && moveResult.claimedBoxes.isEmpty()) {
                soundManager.playTurnChange()
            }
            checkAndTriggerAiMove(nextState)
        }
    }

    private fun checkAndTriggerAiMove(state: GameState) {
        if (state.isGameOver) return
        val currentP = state.currentPlayer
        if (!currentP.isAi) return

        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            delay(500)

            var activeState = _gameState.value ?: return@launch
            if (activeState.mode == GameMode.DICE_MODE && activeState.diceState?.hasRolledThisTurn == false) {
                activeState = GameEngine.rollDice(activeState)
                _gameState.value = activeState
                delay(400)
            }

            val aiChoice = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                AiPlayer.chooseMove(activeState)
            }
            executeMove(activeState, aiChoice)
        }
    }

    fun requestHint() {
        val current = _gameState.value ?: return
        if (current.isGameOver) return

        viewModelScope.launch {
            val hint = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                AiPlayer.chooseMove(current, difficulty = AiDifficulty.MEDIUM)
            }
            _hintEdgeId.value = hint
            hintsUsed++
        }
    }

    fun undoMove() {
        val current = _gameState.value ?: return
        if (current.moveHistory.isEmpty()) return

        var newHistory = current.moveHistory.dropLast(1)
        if (newHistory.isNotEmpty() && current.mode == GameMode.VS_AI) {
            newHistory = newHistory.dropLast(1)
        }

        var reconstructed = GameEngine.createInitialState(
            gameId = current.gameId,
            rows = current.rows,
            cols = current.cols,
            mode = current.mode,
            turnRule = current.turnRule,
            aiDifficulty = current.aiDifficulty,
            players = current.players.map { it.copy(score = 0) }
        )

        for (record in newHistory) {
            reconstructed = GameEngine.applyMove(reconstructed, record.edgeId).newState
        }

        _gameState.value = reconstructed
        undosUsed++
        saveSnapshot(reconstructed)
    }

    private fun saveSnapshot(state: GameState) {
        viewModelScope.launch {
            val entity = SavedGameEntity(
                gameId = state.gameId,
                rows = state.rows,
                cols = state.cols,
                mode = state.mode.name,
                turnRule = state.turnRule.name,
                aiDifficulty = state.aiDifficulty?.name,
                stateJson = ""
            )
            dao.saveGame(entity)
        }
    }

    override fun onCleared() {
        super.onCleared()
        aiJob?.cancel()
        soundManager.release()
    }
}
