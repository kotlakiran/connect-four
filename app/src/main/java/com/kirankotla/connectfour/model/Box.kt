package com.kirankotla.connectfour.model

data class Box(val row: Int, val col: Int) {
    val id: String = "B:$row:$col"

    val topEdgeId: String get() = "H:$row:$col"
    val bottomEdgeId: String get() = "H:${row + 1}:$col"
    val leftEdgeId: String get() = "V:$row:$col"
    val rightEdgeId: String get() = "V:$row:${col + 1}"

    val boundingEdgeIds: List<String>
        get() = listOf(topEdgeId, bottomEdgeId, leftEdgeId, rightEdgeId)
}
