package com.kirankotla.connectfour.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kirankotla.connectfour.model.GameState
import com.kirankotla.connectfour.model.reward.BoxReward
import com.kirankotla.connectfour.util.HapticManager
import com.kirankotla.connectfour.util.SoundManager
import kotlin.math.min

@Composable
fun ConnectFourBoard(
    gameState: GameState,
    onEdgeTap: (String) -> Unit,
    hintEdgeId: String? = null,
    modifier: Modifier = Modifier,
    highContrast: Boolean = false,
    reduceMotion: Boolean = false,
    warnOnDangerousMoves: Boolean = true,
    themeColor: Color = MaterialTheme.colorScheme.primary,
    reward: BoxReward? = null,
    rewardEventId: Int = 0,
    rewardBoxId: String? = null
) {
    val context = LocalContext.current
    val soundManager = remember(context) { SoundManager(context) }
    val hapticManager = remember(context) { HapticManager(context) }

    // Pulse animation for hints and winning line
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Animated drop tracking for the newest chip
    val lastDrop = gameState.lastDrop
    val dropProgress = remember { Animatable(1f) }

    LaunchedEffect(lastDrop) {
        if (lastDrop != null && !reduceMotion) {
            dropProgress.snapTo(0f)
            dropProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
            )
            // Crisp impact click played strictly once on landing
            soundManager.playLineStroke()
            hapticManager.performLineSnap()
        } else if (lastDrop != null) {
            soundManager.playLineStroke()
        }
    }

    // Determine hint column
    val hintCol = remember(hintEdgeId) {
        hintEdgeId?.removePrefix("col_")?.toIntOrNull()
    }

    val currentGameState by rememberUpdatedState(gameState)
    val currentOnEdgeTap by rememberUpdatedState(onEdgeTap)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val state = currentGameState
                    if (!state.isGameOver && !state.currentPlayer.isAi) {
                        val cols = state.cols
                        val rows = state.rows

                        val availableWidth = size.width
                        val availableHeight = size.height

                        val cellSize = min(
                            (availableWidth * 0.94f) / cols,
                            (availableHeight * 0.88f) / rows
                        )

                        val boardWidth = cols * cellSize
                        val boardHeight = rows * cellSize
                        val boardLeft = (availableWidth - boardWidth) / 2f
                        val boardTop = (availableHeight - boardHeight) / 2f

                        if (offset.x in (boardLeft - cellSize * 0.2f)..(boardLeft + boardWidth + cellSize * 0.2f) &&
                            offset.y in (boardTop - cellSize * 1.5f)..(boardTop + boardHeight + cellSize * 0.5f)
                        ) {
                            val col = ((offset.x - boardLeft) / cellSize).toInt().coerceIn(0, cols - 1)
                            if (!state.isColumnFull(col)) {
                                hapticManager.performUiTap()
                                currentOnEdgeTap("col_$col")
                            }
                        }
                    }
                }
            }
    ) {
        val cols = gameState.cols
        val rows = gameState.rows

        val availableWidth = size.width
        val availableHeight = size.height

        val cellSize = min(
            (availableWidth * 0.94f) / cols,
            (availableHeight * 0.88f) / rows
        )

        val boardWidth = cols * cellSize
        val boardHeight = rows * cellSize
        val boardLeft = (availableWidth - boardWidth) / 2f
        val boardTop = (availableHeight - boardHeight) / 2f

        val chipRadius = cellSize * 0.39f

        // Draw Board Background Plate / Shadow
        drawRoundRect(
            color = Color(0xFF060B18),
            topLeft = Offset(boardLeft - 10.dp.toPx(), boardTop - 10.dp.toPx()),
            size = Size(boardWidth + 20.dp.toPx(), boardHeight + 20.dp.toPx()),
            cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx())
        )

        // Draw Slotted Chassis Base
        val chassisBrush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1E3A8A), // Vibrant sapphire blue
                Color(0xFF142456),
                Color(0xFF0F1B3E)
            ),
            startY = boardTop,
            endY = boardTop + boardHeight
        )

        drawRoundRect(
            brush = chassisBrush,
            topLeft = Offset(boardLeft - 8.dp.toPx(), boardTop - 8.dp.toPx()),
            size = Size(boardWidth + 16.dp.toPx(), boardHeight + 16.dp.toPx()),
            cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
        )

        // Board outer neon border
        drawRoundRect(
            color = Color(0xFF38BDF8).copy(alpha = 0.45f),
            topLeft = Offset(boardLeft - 8.dp.toPx(), boardTop - 8.dp.toPx()),
            size = Size(boardWidth + 16.dp.toPx(), boardHeight + 16.dp.toPx()),
            cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )

        // Render Empty Slot Sockets (the holes inside the chassis)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cx = boardLeft + c * cellSize + cellSize / 2f
                val cy = boardTop + r * cellSize + cellSize / 2f

                // Deep dark socket interior
                drawCircle(
                    color = Color(0xFF080D1A),
                    radius = chipRadius + 1.dp.toPx(),
                    center = Offset(cx, cy)
                )

                // Subtle inner beveled rim for 3D depth
                drawCircle(
                    color = Color(0xFF0B1428),
                    radius = chipRadius,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color(0xFF254B8C).copy(alpha = 0.5f),
                    radius = chipRadius + 1.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // Render Dropped Chips
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val playerId = gameState.getCell(r, c) ?: continue

                val cx = boardLeft + c * cellSize + cellSize / 2f
                val targetCy = boardTop + r * cellSize + cellSize / 2f

                // If this is the active dropping chip, interpolate Y
                val cy = if (lastDrop != null && lastDrop.first == r && lastDrop.second == c && dropProgress.value < 1f) {
                    val startY = boardTop - chipRadius * 2
                    startY + (targetCy - startY) * dropProgress.value
                } else {
                    targetCy
                }

                drawChip(
                    drawScope = this,
                    cx = cx,
                    cy = cy,
                    radius = chipRadius,
                    playerId = playerId,
                    isWinningChip = gameState.winningLine?.contains(Pair(r, c)) == true,
                    pulse = pulseProgress
                )
            }
        }

        // Render Downward Hint Arrow ⬇️
        if (hintCol != null && hintCol in 0 until cols && !gameState.isGameOver) {
            val arrowCx = boardLeft + hintCol * cellSize + cellSize / 2f
            val bounceOffset = pulseProgress * 8.dp.toPx()
            val arrowCy = boardTop - 18.dp.toPx() + bounceOffset

            val arrowPath = Path().apply {
                moveTo(arrowCx - 10.dp.toPx(), arrowCy - 12.dp.toPx())
                lineTo(arrowCx + 10.dp.toPx(), arrowCy - 12.dp.toPx())
                lineTo(arrowCx, arrowCy)
                close()
            }

            drawPath(
                path = arrowPath,
                color = Color(0xFF00E5FF)
            )
            drawPath(
                path = arrowPath,
                color = Color.White,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Render Laser Victory Line ⚡
        val winLine = gameState.winningLine
        if (winLine != null && winLine.size >= 2) {
            val first = winLine.first()
            val last = winLine.last()

            val startX = boardLeft + first.second * cellSize + cellSize / 2f
            val startY = boardTop + first.first * cellSize + cellSize / 2f
            val endX = boardLeft + last.second * cellSize + cellSize / 2f
            val endY = boardTop + last.first * cellSize + cellSize / 2f

            val startOffset = Offset(startX, startY)
            val endOffset = Offset(endX, endY)

            val laserGlowWidth = 14.dp.toPx() + (pulseProgress * 6.dp.toPx())
            val laserColor = if (gameState.winnerId == 1) Color(0xFFFF5252) else Color(0xFF00E5FF)

            // 1. Broad soft neon outer bloom
            drawLine(
                color = laserColor.copy(alpha = 0.35f + pulseProgress * 0.25f),
                start = startOffset,
                end = endOffset,
                strokeWidth = laserGlowWidth,
                cap = StrokeCap.Round
            )

            // 2. Focused vibrant laser beam
            drawLine(
                color = laserColor,
                start = startOffset,
                end = endOffset,
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 3. Core white-hot laser filament
            drawLine(
                color = Color.White,
                start = startOffset,
                end = endOffset,
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun drawChip(
    drawScope: DrawScope,
    cx: Float,
    cy: Float,
    radius: Float,
    playerId: Int,
    isWinningChip: Boolean,
    pulse: Float
) {
    val isP1 = playerId == 1

    // Colors
    val baseColor = if (isP1) Color(0xFFFF334B) else Color(0xFF00E5FF)
    val shadowColor = if (isP1) Color(0xFF99001A) else Color(0xFF0077B6)
    val highlightColor = if (isP1) Color(0xFFFF8594) else Color(0xFFE0F7FA)

    // Outer chip body with 3D spherical gradient
    val chipBrush = Brush.radialGradient(
        colors = listOf(
            highlightColor,
            baseColor,
            shadowColor
        ),
        center = Offset(cx - radius * 0.28f, cy - radius * 0.28f),
        radius = radius * 1.15f
    )

    drawScope.drawCircle(
        brush = chipBrush,
        radius = radius,
        center = Offset(cx, cy)
    )

    // Outer beveled ring
    drawScope.drawCircle(
        color = Color.White.copy(alpha = 0.28f),
        radius = radius * 0.76f,
        center = Offset(cx, cy),
        style = Stroke(width = 2.2f)
    )

    // Center circular badge recess
    drawScope.drawCircle(
        color = shadowColor.copy(alpha = 0.3f),
        radius = radius * 0.52f,
        center = Offset(cx, cy)
    )

    // Specular shine dot
    drawScope.drawCircle(
        color = Color.White.copy(alpha = 0.65f),
        radius = radius * 0.16f,
        center = Offset(cx - radius * 0.38f, cy - radius * 0.38f)
    )

    // Winning chip pulsing neon crown
    if (isWinningChip) {
        val winRingRadius = radius + (pulse * 5.dp.value)
        val glowColor = if (isP1) Color(0xFFFF5252) else Color(0xFF00E5FF)

        drawScope.drawCircle(
            color = glowColor.copy(alpha = 0.7f - pulse * 0.35f),
            radius = winRingRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 3.dp.value)
        )

        drawScope.drawCircle(
            color = Color.White,
            radius = radius + 1.dp.value,
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.value)
        )
    }
}

@Composable
fun ComboChainBanner(
    comboCount: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = comboCount >= 2,
        enter = fadeIn() + scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (comboCount >= 4) Color(0xFFEF4444) else if (comboCount == 3) Color(0xFFF59E0B) else Color(0xFF8B5CF6),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(if (comboCount >= 4) "🔥" else "⚡", fontSize = 16.sp)
                Text(
                    text = "${comboCount}x CHAIN COMBO!",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }
    }
}
