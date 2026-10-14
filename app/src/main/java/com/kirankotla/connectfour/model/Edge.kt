package com.kirankotla.connectfour.model

sealed class Edge {
    abstract val row: Int
    abstract val col: Int
    abstract val id: String

    data class Horizontal(override val row: Int, override val col: Int) : Edge() {
        override val id: String = "H:$row:$col"
    }

    data class Vertical(override val row: Int, override val col: Int) : Edge() {
        override val id: String = "V:$row:$col"
    }

    companion object {
        fun fromId(id: String): Edge {
            val parts = id.split(":")
            require(parts.size == 3) { "Invalid Edge ID format: $id" }
            val type = parts[0]
            val row = parts[1].toInt()
            val col = parts[2].toInt()
            return when (type) {
                "H" -> Horizontal(row, col)
                "V" -> Vertical(row, col)
                else -> throw IllegalArgumentException("Unknown edge type: $type")
            }
        }
    }
}
