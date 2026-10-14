package com.kirankotla.connectfour.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirankotla.connectfour.model.Edge
import com.kirankotla.connectfour.model.GameState
import com.kirankotla.connectfour.model.MoveRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveHistorySheet(
    gameState: GameState,
    onDismissRequest: () -> Unit,
    highContrast: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // Last 10 moves, newest first, paired with 1-based original move index
    val recentMovesWithIndex = remember(gameState.moveHistory) {
        gameState.moveHistory
            .mapIndexed { index, record -> (index + 1) to record }
            .takeLast(10)
            .reversed()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier.testTag("move_history_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Move History",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (recentMovesWithIndex.isEmpty()) "No moves yet"
                            else "Showing last ${recentMovesWithIndex.size} of ${gameState.moveHistory.size} moves",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.testTag("close_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Move History"
                    )
                }
            }

            // Summary Stats Pill Bar
            if (gameState.moveHistory.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Moves: ${gameState.moveHistory.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        val totalCaptured = gameState.boxOwners.size
                        Text(
                            text = "Boxes Claimed: $totalCaptured / ${gameState.totalBoxes}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Empty State
            if (recentMovesWithIndex.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CropSquare,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "No moves played yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Connect dots on the board to see turn history and square captures recorded here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                // Scrollable List of Moves
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        recentMovesWithIndex,
                        key = { (moveNumber, move) -> "${moveNumber}_${move.edgeId}_${move.timestampMs}" }
                    ) { (moveNumber, move) ->
                        val isLatest = moveNumber == gameState.moveHistory.size
                        MoveItemCard(
                            moveNumber = moveNumber,
                            move = move,
                            gameState = gameState,
                            isLatest = isLatest,
                            highContrast = highContrast
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveItemCard(
    moveNumber: Int,
    move: MoveRecord,
    gameState: GameState,
    isLatest: Boolean,
    highContrast: Boolean
) {
    val player = gameState.players.find { it.id == move.playerId }
    val isP1 = player?.id == gameState.players.firstOrNull()?.id
    val playerColor = if (highContrast) {
        if (isP1) Color(0xFF00E676) else Color(0xFFFFD600)
    } else {
        if (isP1) Color(0xFF2563EB) else Color(0xFFDC2626)
    }

    val edge = try {
        Edge.fromId(move.edgeId)
    } catch (_: Exception) {
        null
    }

    val edgeDesc = when (edge) {
        is Edge.Horizontal -> "Horizontal Line (Row ${edge.row + 1}, Col ${edge.col + 1})"
        is Edge.Vertical -> "Vertical Line (Row ${edge.row + 1}, Col ${edge.col + 1})"
        null -> move.edgeId
    }

    val claimedCount = move.claimedBoxIds.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("move_item_$moveNumber"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLatest) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        border = if (isLatest) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Move Number Pill
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(playerColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$moveNumber",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = playerColor
                )
            }

            // Move Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = player?.name ?: "Player ${move.playerId}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isLatest) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "LATEST",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = edgeDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Capture Badge
                if (claimedCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = playerColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (claimedCount == 1) "Completed 1 square (+1 pt)" else "Completed $claimedCount squares (+${claimedCount} pts)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = playerColor
                        )
                    }
                }
            }

            // Player visual indicator dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(playerColor)
            )
        }
    }
}
