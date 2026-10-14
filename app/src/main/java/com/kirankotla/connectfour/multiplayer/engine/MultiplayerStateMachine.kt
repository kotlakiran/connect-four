package com.kirankotla.connectfour.multiplayer.engine

import com.kirankotla.connectfour.multiplayer.model.OnlineGameStatus

object MultiplayerStateMachine {

    fun isValidTransition(from: OnlineGameStatus, to: OnlineGameStatus): Boolean {
        if (from == to) return true
        return when (from) {
            OnlineGameStatus.WAITING -> to == OnlineGameStatus.MATCHED || to == OnlineGameStatus.CANCELLED
            OnlineGameStatus.MATCHED -> to == OnlineGameStatus.READY || to == OnlineGameStatus.CANCELLED
            OnlineGameStatus.READY -> to == OnlineGameStatus.PLAYING || to == OnlineGameStatus.CANCELLED
            OnlineGameStatus.PLAYING -> to == OnlineGameStatus.PAUSED || to == OnlineGameStatus.COMPLETED
            OnlineGameStatus.PAUSED -> to == OnlineGameStatus.PLAYING || to == OnlineGameStatus.COMPLETED
            OnlineGameStatus.COMPLETED -> to == OnlineGameStatus.RESULTS
            OnlineGameStatus.RESULTS -> false
            OnlineGameStatus.CANCELLED -> false
        }
    }

    fun assertValidTransition(from: OnlineGameStatus, to: OnlineGameStatus) {
        require(isValidTransition(from, to)) {
            "Illegal game state transition from $from to $to"
        }
    }
}
