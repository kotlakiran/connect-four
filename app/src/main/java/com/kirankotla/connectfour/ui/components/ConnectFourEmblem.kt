package com.kirankotla.connectfour.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-fidelity Connect Four Hero Emblem used on Welcome/Auth and Onboarding screens.
 * Displays the iconic vertical 4x4 blue chassis with 3D red and cyan chips,
 * and a glowing celebratory strike connecting 4 chips in a row.
 */
@Composable
fun ConnectFourEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    animated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "emblemPulse")
    val pulse by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height

            // 1. Ambient outer neon glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2563EB).copy(alpha = 0.35f * pulse),
                        Color(0xFF00E5FF).copy(alpha = 0.15f * pulse),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = Offset(w * 0.5f, h * 0.5f)
            )

            // 2. Connect Four Vertical Chassis Frame
            val padX = w * 0.08f
            val padY = h * 0.08f
            val frameW = w - padX * 2f
            val frameH = h - padY * 2f
            val cornerR = w * 0.14f

            // Frame drop shadow
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(padX, padY + h * 0.04f),
                size = Size(frameW, frameH),
                cornerRadius = CornerRadius(cornerR, cornerR)
            )

            // Frame chassis (Deep electric royal blue)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2563EB), // Blue 600
                        Color(0xFF1D4ED8), // Blue 700
                        Color(0xFF1E3A8A)  // Blue 900
                    )
                ),
                topLeft = Offset(padX, padY),
                size = Size(frameW, frameH),
                cornerRadius = CornerRadius(cornerR, cornerR)
            )

            // Frame metallic highlight rim
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.45f),
                        Color.Transparent,
                        Color(0xFF60A5FA).copy(alpha = 0.35f)
                    ),
                    start = Offset(padX, padY),
                    end = Offset(padX + frameW, padY + frameH)
                ),
                topLeft = Offset(padX, padY),
                size = Size(frameW, frameH),
                cornerRadius = CornerRadius(cornerR, cornerR),
                style = Stroke(width = w * 0.022f)
            )

            // 3. Grid of Slots (4 rows x 4 columns)
            val rows = 4
            val cols = 4
            val cellW = frameW / cols.toFloat()
            val cellH = frameH / rows.toFloat()
            val chipRadius = minOf(cellW, cellH) * 0.36f

            // Chip positions:
            // 1 = Red, 2 = Cyan, 0 = Empty
            // Row 0 (top):    [0, 0, 0, 1]  <- Red connects 4 diagonally!
            // Row 1:          [0, 0, 1, 2]
            // Row 2:          [0, 1, 2, 2]
            // Row 3 (bottom): [1, 2, 1, 2]
            val grid = arrayOf(
                intArrayOf(0, 0, 0, 1),
                intArrayOf(0, 0, 1, 2),
                intArrayOf(0, 1, 2, 2),
                intArrayOf(1, 2, 1, 2)
            )

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val cx = padX + c * cellW + cellW * 0.5f
                    val cy = padY + r * cellH + cellH * 0.5f

                    // Deep empty slot cavity
                    drawCircle(
                        color = Color(0xFF090D1A),
                        radius = chipRadius,
                        center = Offset(cx, cy)
                    )

                    // Slot bevel shadow
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.6f),
                        radius = chipRadius,
                        center = Offset(cx, cy - chipRadius * 0.12f),
                        style = Stroke(width = chipRadius * 0.25f)
                    )

                    val chipType = grid[r][c]
                    if (chipType != 0) {
                        drawEmblemChip(
                            cx = cx,
                            cy = cy,
                            radius = chipRadius * 0.94f,
                            isRed = (chipType == 1),
                            isWinning = (r + c == 3 && chipType == 1) // Diagonal 4-in-a-row
                        )
                    }
                }
            }

            // 4. Winning Diagonal 4-in-a-row Strike Line
            val startX = padX + 0 * cellW + cellW * 0.5f
            val startY = padY + 3 * cellH + cellH * 0.5f
            val endX = padX + 3 * cellW + cellW * 0.5f
            val endY = padY + 0 * cellH + cellH * 0.5f

            // Outer strike glow
            drawLine(
                color = Color(0xFFFFD700).copy(alpha = 0.55f * pulse),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = w * 0.05f,
                cap = StrokeCap.Round
            )

            // Inner bright neon strike beam
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFEA00),
                        Color.White,
                        Color(0xFFFFEA00)
                    )
                ),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = w * 0.022f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawEmblemChip(
    cx: Float,
    cy: Float,
    radius: Float,
    isRed: Boolean,
    isWinning: Boolean
) {
    val highlightColor = if (isRed) Color(0xFFFF8A80) else Color(0xFF80D8FF)
    val baseColor = if (isRed) Color(0xFFFF1744) else Color(0xFF00B0FF)
    val shadowColor = if (isRed) Color(0xFF880E4F) else Color(0xFF004D80)

    // Spherical 3D gradient body
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(highlightColor, baseColor, shadowColor),
            center = Offset(cx - radius * 0.28f, cy - radius * 0.28f),
            radius = radius * 1.2f
        ),
        radius = radius,
        center = Offset(cx, cy)
    )

    // Concentric beveled ring
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = radius * 0.72f,
        center = Offset(cx, cy),
        style = Stroke(width = radius * 0.12f)
    )

    // Center circular recess
    drawCircle(
        color = shadowColor.copy(alpha = 0.35f),
        radius = radius * 0.45f,
        center = Offset(cx, cy)
    )

    // Glossy specular highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.75f),
        radius = radius * 0.18f,
        center = Offset(cx - radius * 0.35f, cy - radius * 0.35f)
    )

    // Golden halo if part of the winning 4
    if (isWinning) {
        drawCircle(
            color = Color(0xFFFFD700).copy(alpha = 0.85f),
            radius = radius * 0.98f,
            center = Offset(cx, cy),
            style = Stroke(width = radius * 0.15f)
        )
    }
}
