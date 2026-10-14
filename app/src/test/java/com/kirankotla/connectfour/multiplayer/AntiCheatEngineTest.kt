package com.kirankotla.connectfour.multiplayer

import com.kirankotla.connectfour.multiplayer.engine.AntiCheatEngine
import com.kirankotla.connectfour.multiplayer.engine.ValidationResult
import com.kirankotla.connectfour.multiplayer.model.OnlineGameDoc
import com.kirankotla.connectfour.multiplayer.model.OnlineGameStatus
import com.kirankotla.connectfour.multiplayer.model.OnlinePlayerDoc
import org.junit.Assert.*
import org.junit.Test

class AntiCheatEngineTest {

    private val player1 = OnlinePlayerDoc(uid = "uid_p1", displayName = "Player 1", colorHex = "#FF6B6B")
    private val player2 = OnlinePlayerDoc(uid = "uid_p2", displayName = "Player 2", colorHex = "#22B8A7")

    private val baseGame = OnlineGameDoc(
        gameId = "test_game",
        status = OnlineGameStatus.PLAYING,
        rows = 6,
        cols = 7,
        currentTurn = "uid_p1",
        playerUids = listOf("uid_p1", "uid_p2"),
        version = 1L
    )

    @Test
    fun testValidMove_succeeds() {
        val validation = AntiCheatEngine.validateMove(baseGame, "uid_p1", "col_3", 1L)
        assertTrue(validation is ValidationResult.Valid)
    }

    @Test
    fun testWrongTurn_rejected() {
        val validation = AntiCheatEngine.validateMove(baseGame, "uid_p2", "col_3", 1L)
        assertTrue(validation is ValidationResult.Invalid)
        assertTrue((validation as ValidationResult.Invalid).reason.contains("Not this player's turn"))
    }

    @Test
    fun testOutOfBoundsMove_rejected() {
        // cols=7, so col 9 is out of bounds
        val validation = AntiCheatEngine.validateMove(baseGame, "uid_p1", "col_9", 1L)
        assertTrue(validation is ValidationResult.Invalid)
        assertTrue((validation as ValidationResult.Invalid).reason.contains("out of bounds"))
    }

    @Test
    fun testFullColumn_rejected() {
        // 6 chips already in col 2 (rows=6)
        val fullColGame = baseGame.copy(claimedEdges = List(6) { "col_2" })
        val validation = AntiCheatEngine.validateMove(fullColGame, "uid_p1", "col_2", 1L)
        assertTrue(validation is ValidationResult.Invalid)
        assertTrue((validation as ValidationResult.Invalid).reason.contains("already full"))
    }

    @Test
    fun testAuthoritativeMove_advancesTurnAndVersion() {
        val res = AntiCheatEngine.applyAuthoritativeMove(
            game = baseGame,
            players = listOf(player1, player2),
            playerId = "uid_p1",
            edgeId = "col_3"
        )

        assertEquals(2L, res.updatedGame.version)
        assertEquals("uid_p2", res.updatedGame.currentTurn)
        assertEquals(listOf("col_3"), res.updatedGame.claimedEdges)
        assertFalse(res.isGameOver)
    }

    @Test
    fun testWinningMove_awardsWinner() {
        var g = baseGame
        val moves = listOf("col_0", "col_1", "col_0", "col_1", "col_0", "col_1")
        for ((idx, edge) in moves.withIndex()) {
            val pid = if (idx % 2 == 0) "uid_p1" else "uid_p2"
            g = AntiCheatEngine.applyAuthoritativeMove(
                game = g,
                players = listOf(player1, player2),
                playerId = pid,
                edgeId = edge
            ).updatedGame
        }

        // Player 1 drops the 4th chip in col 0 for the win
        val res = AntiCheatEngine.applyAuthoritativeMove(
            game = g,
            players = listOf(player1, player2),
            playerId = "uid_p1",
            edgeId = "col_0"
        )

        assertTrue(res.isGameOver)
        assertEquals("uid_p1", res.winnerUid)
        assertEquals(OnlineGameStatus.COMPLETED, res.updatedGame.status)
    }
}
