package com.kirankotla.connectfour.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kirankotla.connectfour.model.Edge
import com.kirankotla.connectfour.model.GameState

@Composable
fun AccessibleMovePicker(
    gameState: GameState,
    onConfirmMove: (String) -> Unit
) {
    var isHorizontal by remember { mutableStateOf(true) }
    var selectedRow by remember { mutableIntStateOf(0) }
    var selectedCol by remember { mutableIntStateOf(0) }

    val maxRow = if (isHorizontal) gameState.rows else gameState.rows - 1
    val maxCol = if (isHorizontal) gameState.cols - 1 else gameState.cols

    val currentEdgeId = if (isHorizontal) "H:$selectedRow:$selectedCol" else "V:$selectedRow:$selectedCol"
    val isAlreadyClaimed = gameState.isEdgeClaimed(currentEdgeId)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics { contentDescription = "Accessible Move Picker Control Panel" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Accessible Line Picker",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Orientation Selection
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = isHorizontal,
                    onClick = {
                        isHorizontal = true
                        selectedRow = 0
                        selectedCol = 0
                    },
                    label = { Text("Horizontal Line") }
                )
                FilterChip(
                    selected = !isHorizontal,
                    onClick = {
                        isHorizontal = false
                        selectedRow = 0
                        selectedCol = 0
                    },
                    label = { Text("Vertical Line") }
                )
            }

            // Row / Col Steppers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Row: $selectedRow / $maxRow")
                Row {
                    IconButton(
                        onClick = { if (selectedRow > 0) selectedRow-- },
                        enabled = selectedRow > 0
                    ) { Text("-") }
                    IconButton(
                        onClick = { if (selectedRow < maxRow) selectedRow++ },
                        enabled = selectedRow < maxRow
                    ) { Text("+") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Column: $selectedCol / $maxCol")
                Row {
                    IconButton(
                        onClick = { if (selectedCol > 0) selectedCol-- },
                        enabled = selectedCol > 0
                    ) { Text("-") }
                    IconButton(
                        onClick = { if (selectedCol < maxCol) selectedCol++ },
                        enabled = selectedCol < maxCol
                    ) { Text("+") }
                }
            }

            Button(
                onClick = { onConfirmMove(currentEdgeId) },
                enabled = !isAlreadyClaimed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isAlreadyClaimed) "Line Already Drawn" else "Draw $currentEdgeId")
            }
        }
    }
}
