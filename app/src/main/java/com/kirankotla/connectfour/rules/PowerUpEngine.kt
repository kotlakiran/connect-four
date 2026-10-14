package com.kirankotla.connectfour.rules

import com.kirankotla.connectfour.model.GameState
import com.kirankotla.connectfour.model.MoveResult
import com.kirankotla.connectfour.model.PowerUpState
import com.kirankotla.connectfour.model.reward.JokerCatalog

/** Wraps [GameEngine] for opt-in local/campaign Power-Up play; classic rules are untouched. */
object PowerUpEngine {

    val PLAYABLE_JOKER_IDS: Set<String> = setOf(JokerCatalog.EXTRA_TURN.id, JokerCatalog.STEAL_BOX.id)

    fun enable(state: GameState): GameState = state.copy(powerUpState = state.powerUpState ?: PowerUpState())

    fun isActive(state: GameState): Boolean = state.powerUpState != null

    fun canUse(state: GameState, jokerId: String): Boolean {
        val power = state.powerUpState ?: return false
        if (state.isGameOver || jokerId !in PLAYABLE_JOKER_IDS) return false
        return when (jokerId) {
            JokerCatalog.EXTRA_TURN.id -> power.extraTurnPlayerId == null
            JokerCatalog.STEAL_BOX.id -> stealableBoxIds(state).isNotEmpty()
            else -> false
        }
    }

    /** Returns the new state, or null if the joker cannot be used now. */
    fun useJoker(state: GameState, jokerId: String, boxId: String? = null): GameState? {
        if (!canUse(state, jokerId)) return null
        val power = state.powerUpState ?: return null
        val updated = when (jokerId) {
            JokerCatalog.EXTRA_TURN.id ->
                state.copy(powerUpState = power.copy(extraTurnPlayerId = state.currentPlayer.id))
            JokerCatalog.STEAL_BOX.id -> stealBox(state, boxId) ?: return null
            else -> return null
        }
        val newPower = updated.powerUpState ?: power
        return updated.copy(
            powerUpState = newPower.copy(usedJokerIds = newPower.usedJokerIds + jokerId),
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun stealableBoxIds(state: GameState): List<String> {
        val me = state.currentPlayer.id
        return state.boxOwners.filterValues { it != me }.keys.sorted()
    }

    private fun stealBox(state: GameState, boxId: String?): GameState? {
        val me = state.currentPlayer.id
        val target = boxId ?: stealableBoxIds(state).firstOrNull() ?: return null
        val previousOwner = state.boxOwners[target] ?: return null
        if (previousOwner == me) return null
        return state.copy(
            boxOwners = state.boxOwners + (target to me),
            players = state.players.map {
                when (it.id) {
                    me -> it.copy(score = it.score + 1)
                    previousOwner -> it.copy(score = it.score - 1)
                    else -> it
                }
            }
        )
    }

    fun applyMove(state: GameState, edgeId: String): MoveResult {
        val result = GameEngine.applyMove(state, edgeId)
        val armedFor = state.powerUpState?.extraTurnPlayerId ?: return result
        val next = result.newState
        if (armedFor != state.currentPlayer.id || result.isGameOver) return result
        if (result.turnRetained) return result
        return result.copy(
            newState = next.copy(
                currentPlayerIndex = state.currentPlayerIndex,
                powerUpState = next.powerUpState?.copy(extraTurnPlayerId = null)
            ),
            turnRetained = true
        )
    }

    /** Removes one copy of [jokerId] from an owned-jokers JSON array, or null if not owned. */
    fun consumeFromOwnedJson(json: String, jokerId: String): String? {
        val values = ownedIds(json).toMutableList()
        if (!values.remove(jokerId)) return null
        return values.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }
    }

    fun ownedIds(json: String): List<String> =
        Regex("\"([^\"]*)\"").findAll(json).map { it.groupValues[1] }.toList()
}
