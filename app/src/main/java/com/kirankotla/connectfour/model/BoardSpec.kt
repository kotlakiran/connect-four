package com.kirankotla.connectfour.model

/** Immutable board definition shared by local, online, and Nearby game flows. */
data class BoardSpec(
    val id: String,
    val rows: Int,
    val cols: Int,
    val displayName: String,
    val winTarget: Int = 4,
    val maxPlayers: Int = 2
) {
    init {
        require(rows >= 3) { "Board rows must be at least 3" }
        require(cols >= 3) { "Board columns must be at least 3" }
        require(maxPlayers in 2..4) { "Board player limit must be between 2 and 4" }
    }

    val totalBoxes: Int get() = rows * cols
    val totalCells: Int get() = rows * cols
    val totalEdges: Int get() = rows * cols
    val dotRows: Int get() = rows
    val dotCols: Int get() = cols
    val label: String get() = "$cols x $rows ($winTarget-in-a-row)"
    val dotLabel: String get() = "$rows rows, $cols cols"

    companion object {
        val CLASSIC_7X6 = BoardSpec("classic_7x6", 6, 7, "Classic 7x6", winTarget = 4)
        val COMPACT_6X5 = BoardSpec("compact_6x5", 5, 6, "Compact 6x5", winTarget = 4)
        val GRAND_8X7 = BoardSpec("grand_8x7", 7, 8, "Grand 8x7", winTarget = 4)
        val MEGA_8X7 = BoardSpec("mega_8x7", 7, 8, "Mega Connect 5", winTarget = 5)
        val MINI_5X4 = BoardSpec("mini_5x4", 4, 5, "Mini 5x4", winTarget = 3)

        // Compatibility aliases for any legacy board references
        val CLASSIC_3X3 = MINI_5X4
        val ARENA_4X4 = COMPACT_6X5
        val TACTICAL_5X5 = CLASSIC_7X6
        val TITAN_6X6 = GRAND_8X7
        val WIDE_3X6 = COMPACT_6X5
        val TALL_6X3 = CLASSIC_7X6
        val GRAND_8X6 = GRAND_8X7
        val EPIC_8X8 = MEGA_8X7

        val multiplayerPresets: List<BoardSpec> = listOf(
            CLASSIC_7X6,
            COMPACT_6X5,
            GRAND_8X7,
            MEGA_8X7,
            MINI_5X4
        )

        /** Resolves a preset by matching dimensions (and id when given); falls back to a custom spec. */
        fun of(rows: Int, cols: Int, variantId: String? = null): BoardSpec {
            val matches = multiplayerPresets.filter { it.rows == rows && it.cols == cols }
            matches.firstOrNull { it.id == variantId }?.let { return it }
            matches.firstOrNull()?.let { return it }
            return BoardSpec(variantId ?: "custom_${cols}x$rows", rows, cols, "Custom $cols x $rows", winTarget = 4)
        }

        fun square(size: Int): BoardSpec = when (size) {
            4 -> MINI_5X4
            5 -> COMPACT_6X5
            6 -> CLASSIC_7X6
            7, 8 -> GRAND_8X7
            else -> CLASSIC_7X6
        }
    }
}
