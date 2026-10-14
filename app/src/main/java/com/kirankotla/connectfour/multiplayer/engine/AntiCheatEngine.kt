package com.kirankotla.connectfour.multiplayer.engine

import com.kirankotla.connectfour.multiplayer.model.OnlineGameDoc
import com.kirankotla.connectfour.multiplayer.model.OnlineGameStatus
import com.kirankotla.connectfour.multiplayer.model.OnlinePlayerDoc
import com.kirankotla.connectfour.rules.GameEngine

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

data class AuthoritativeMoveResult(
    val updatedGame: OnlineGameDoc,
    val updatedPlayers: List<OnlinePlayerDoc>,
    val completedBoxIds: List<String>,
    val retainedTurn: Boolean,
    val isGameOver: Boolean,
    val winnerUid: String?
)

object AntiCheatEngine {

    fun validateMove(
        game: OnlineGameDoc,
        playerId: String,
        edgeId: String,
        incomingVersion: Long
    ): ValidationResult {
        if (game.status != OnlineGameStatus.PLAYING) {
            return ValidationResult.Invalid("Game is not in PLAYING state (current: ${game.status})")
        }
        if (game.currentTurn != playerId) {
            return ValidationResult.Invalid("Not this player's turn. Current turn: ${game.currentTurn}, attempting player: $playerId")
        }
        if (incomingVersion != game.version) {
            return ValidationResult.Invalid("Out-of-order move version $incomingVersion (expected ${game.version})")
        }

        val col = edgeId.removePrefix("col_").toIntOrNull()
            ?: return ValidationResult.Invalid("Malformed column drop identifier: $edgeId")

        if (col !in 0 until game.cols) {
            return ValidationResult.Invalid("Column $col out of bounds for grid with ${game.cols} columns")
        }

        val chipsInCol = game.claimedEdges.count { it == "col_$col" }
        if (chipsInCol >= game.rows) {
            return ValidationResult.Invalid("Column $col is already full")
        }

        return ValidationResult.Valid
    }

    fun applyAuthoritativeMove(
        game: OnlineGameDoc,
        players: List<OnlinePlayerDoc>,
        playerId: String,
        edgeId: String
    ): AuthoritativeMoveResult {
        val validation = validateMove(game, playerId, edgeId, game.version)
        if (validation is ValidationResult.Invalid) {
            throw IllegalArgumentException("Anti-cheat rejected move: ${validation.reason}")
        }

        val col = edgeId.removePrefix("col_").toIntOrNull() ?: 0
        val chipsInCol = game.claimedEdges.count { it == "col_$col" }
        val row = (game.rows - 1) - chipsInCol

        val cellKey = "${row}_$col"
        val newBoxOwners = game.boxOwners + (cellKey to playerId)
        val newClaimedEdges = game.claimedEdges + "col_$col"

        // Check 4-in-a-row victory
        val boardForCheck = newBoxOwners.mapValues { (_, owner) -> if (owner == playerId) 1 else 2 }
        val winLine = GameEngine.checkWinningLine(
            boardCells = boardForCheck,
            row = row,
            col = col,
            playerId = 1,
            rows = game.rows,
            cols = game.cols,
            winTarget = 4
        )

        val isWin = winLine != null
        val totalCells = game.rows * game.cols
        val isDraw = !isWin && newBoxOwners.size >= totalCells
        val isGameOver = isWin || isDraw
        val winnerUid = if (isWin) playerId else null

        val updatedPlayers = players.map { p ->
            if (p.uid == playerId && isWin) p.copy(score = p.score + 100) else p
        }

        val opponentUid = game.playerUids.firstOrNull { it != playerId } ?: playerId
        val nextTurnUid = if (isGameOver) "" else opponentUid

        val updatedGame = game.copy(
            claimedEdges = newClaimedEdges,
            boxOwners = newBoxOwners,
            edgeOwners = game.edgeOwners + (edgeId to playerId),
            currentTurn = nextTurnUid,
            version = game.version + 1,
            status = if (isGameOver) OnlineGameStatus.COMPLETED else OnlineGameStatus.PLAYING,
            winnerId = winnerUid,
            isDraw = isDraw,
            completedAt = if (isGameOver) System.currentTimeMillis() else null
        )

        return AuthoritativeMoveResult(
            updatedGame = updatedGame,
            updatedPlayers = updatedPlayers,
            completedBoxIds = listOf(cellKey),
            retainedTurn = false,
            isGameOver = isGameOver,
            winnerUid = winnerUid
        )
    }
}
