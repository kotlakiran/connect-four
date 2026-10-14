package com.kirankotla.connectfour.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirankotla.connectfour.model.AiDifficulty
import com.kirankotla.connectfour.ui.theme.*

data class DifficultyMetric(
    val difficulty: AiDifficulty,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val avgDurationSeconds: Float
) {
    val totalGames: Int get() = wins + losses + draws
    val winRatio: Float get() = if (totalGames > 0) (wins.toFloat() / totalGames) else 0f
    val winPercentage: Int get() = (winRatio * 100).toInt()
}

/**
 * Recharts-style Grouped Bar Chart displaying Wins vs Losses
 * and Win/Loss Ratios across Easy, Medium, and Hard difficulties.
 */
@Composable
fun RechartsBarChart(
    metrics: List<DifficultyMetric>,
    modifier: Modifier = Modifier,
    winColor: Color = Color(0xFF10B981), // Emerald
    lossColor: Color = Color(0xFFFF5252), // Sunset Coral
    gridLineColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    onSelectDifficulty: ((AiDifficulty) -> Unit)? = null
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(metrics) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    val maxGames = remember(metrics) {
        val highest = metrics.maxOfOrNull { maxOf(it.wins, it.losses) } ?: 1
        maxOf(highest, 5)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recharts_bar_chart"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(winColor)
                )
                Text(
                    text = "Wins",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(lossColor)
                )
                Text(
                    text = "Losses",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Canvas Chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(metrics) {
                        detectTapGestures { offset ->
                            val width = size.width
                            val colWidth = width / metrics.size.coerceAtLeast(1)
                            val idx = (offset.x / colWidth).toInt().coerceIn(0, metrics.size - 1)
                            selectedIndex = idx
                            onSelectDifficulty?.invoke(metrics[idx].difficulty)
                        }
                    }
            ) {
                val chartWidth = size.width
                val chartHeight = size.height - 30f // Leave space for bottom X labels
                val animVal = progress.value

                // 1. Draw horizontal grid lines (0%, 25%, 50%, 75%, 100%)
                val gridLines = 4
                for (i in 0..gridLines) {
                    val y = chartHeight * (i.toFloat() / gridLines)
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )
                }

                // 2. Draw Grouped Bars
                val groupCount = metrics.size
                val groupSlotWidth = chartWidth / groupCount
                val barWidth = groupSlotWidth * 0.22f
                val barSpacing = 6f

                metrics.forEachIndexed { index, item ->
                    val centerX = (index + 0.5f) * groupSlotWidth
                    val isHovered = selectedIndex == index

                    // Highlight column background if selected
                    if (isHovered) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.06f),
                            topLeft = Offset(centerX - groupSlotWidth * 0.45f, 0f),
                            size = Size(groupSlotWidth * 0.9f, chartHeight),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                    }

                    // Win Bar
                    val winBarHeight = (item.wins.toFloat() / maxGames) * chartHeight * animVal
                    val winLeft = centerX - barWidth - (barSpacing / 2f)
                    val winTop = chartHeight - winBarHeight

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(winColor, winColor.copy(alpha = 0.8f))
                        ),
                        topLeft = Offset(winLeft, winTop),
                        size = Size(barWidth, maxOf(winBarHeight, 4f)),
                        cornerRadius = CornerRadius(6f, 6f)
                    )

                    // Loss Bar
                    val lossBarHeight = (item.losses.toFloat() / maxGames) * chartHeight * animVal
                    val lossLeft = centerX + (barSpacing / 2f)
                    val lossTop = chartHeight - lossBarHeight

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(lossColor, lossColor.copy(alpha = 0.8f))
                        ),
                        topLeft = Offset(lossLeft, lossTop),
                        size = Size(barWidth, maxOf(lossBarHeight, 4f)),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }

            // X-Axis Difficulty Labels Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                metrics.forEachIndexed { idx, item ->
                    val isSelected = selectedIndex == idx
                    Text(
                        text = item.difficulty.displayName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Recharts Floating Interactive Tooltip
        val currentSelected = selectedIndex?.let { metrics.getOrNull(it) }
        if (currentSelected != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recharts_tooltip_pill"),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${currentSelected.difficulty.displayName} Difficulty",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Win Ratio: ${currentSelected.winPercentage}% (${currentSelected.wins}W / ${currentSelected.losses}L)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (currentSelected.winPercentage >= 50) winColor.copy(alpha = 0.15f) else lossColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (currentSelected.winPercentage >= 50) "Dominant" else "Challenging",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentSelected.winPercentage >= 50) winColor else lossColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recharts-style Smooth Line/Area Chart displaying
 * Average Time Per Game across Easy, Medium, and Hard difficulties.
 */
@Composable
fun RechartsLineAreaChart(
    metrics: List<DifficultyMetric>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF6366F1), // Electric Indigo
    fillColor: Color = Color(0xFF818CF8),
    gridLineColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    onSelectDifficulty: ((AiDifficulty) -> Unit)? = null
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(metrics) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }

    val maxDuration = remember(metrics) {
        val peak = metrics.maxOfOrNull { it.avgDurationSeconds } ?: 60f
        maxOf(peak * 1.25f, 60f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recharts_line_chart"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Avg Match Duration Curve",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(lineColor)
                )
                Text(
                    text = "Seconds per Game",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Canvas Chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(metrics) {
                        detectTapGestures { offset ->
                            val width = size.width
                            val colWidth = width / metrics.size.coerceAtLeast(1)
                            val idx = (offset.x / colWidth).toInt().coerceIn(0, metrics.size - 1)
                            selectedIndex = idx
                            onSelectDifficulty?.invoke(metrics[idx].difficulty)
                        }
                    }
            ) {
                val chartWidth = size.width
                val chartHeight = size.height - 30f // Leave space for X labels
                val animVal = progress.value

                // 1. Draw horizontal grid lines (3 tiers)
                val gridLines = 3
                for (i in 0..gridLines) {
                    val y = chartHeight * (i.toFloat() / gridLines)
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                }

                if (metrics.isEmpty()) return@Canvas

                val count = metrics.size
                val stepX = chartWidth / (count + 1)
                val points = metrics.mapIndexed { idx, item ->
                    val x = (idx + 1) * stepX
                    val normalizedY = (item.avgDurationSeconds / maxDuration).coerceIn(0f, 1f)
                    val y = chartHeight - (normalizedY * chartHeight * animVal)
                    Offset(x, y)
                }

                // 2. Build Smooth Cubic Bezier Curves
                val linePath = Path()
                val areaPath = Path()

                if (points.isNotEmpty()) {
                    linePath.moveTo(points.first().x, points.first().y)
                    areaPath.moveTo(points.first().x, chartHeight)
                    areaPath.lineTo(points.first().x, points.first().y)

                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val cpX1 = p0.x + (p1.x - p0.x) / 2f
                        val cpY1 = p0.y
                        val cpX2 = p0.x + (p1.x - p0.x) / 2f
                        val cpY2 = p1.y

                        linePath.cubicTo(cpX1, cpY1, cpX2, cpY2, p1.x, p1.y)
                        areaPath.cubicTo(cpX1, cpY1, cpX2, cpY2, p1.x, p1.y)
                    }

                    areaPath.lineTo(points.last().x, chartHeight)
                    areaPath.close()

                    // Draw under-curve gradient wash
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                fillColor.copy(alpha = 0.38f),
                                fillColor.copy(alpha = 0.03f)
                            ),
                            startY = 0f,
                            endY = chartHeight
                        )
                    )

                    // Draw smooth stroke line
                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(
                            width = 3.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // 3. Draw Nodes / Vertices
                    points.forEachIndexed { idx, pt ->
                        val isHovered = selectedIndex == idx
                        val outerRadius = if (isHovered) 8.dp.toPx() else 5.dp.toPx()

                        // Outer ring glow
                        drawCircle(
                            color = lineColor.copy(alpha = 0.35f),
                            radius = outerRadius + 4f,
                            center = pt
                        )
                        // Solid core
                        drawCircle(
                            color = if (isHovered) Color.White else lineColor,
                            radius = outerRadius,
                            center = pt
                        )
                        // Inner dot
                        drawCircle(
                            color = if (isHovered) lineColor else Color.White,
                            radius = outerRadius * 0.45f,
                            center = pt
                        )
                    }
                }
            }

            // X-Axis Difficulty Labels Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                metrics.forEachIndexed { idx, item ->
                    val isSelected = selectedIndex == idx
                    Text(
                        text = item.difficulty.displayName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) lineColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Recharts Duration Tooltip
        val currentSelected = selectedIndex?.let { metrics.getOrNull(it) }
        if (currentSelected != null) {
            val minutes = (currentSelected.avgDurationSeconds / 60).toInt()
            val seconds = (currentSelected.avgDurationSeconds % 60).toInt()
            val timeString = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recharts_time_tooltip_pill"),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, lineColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${currentSelected.difficulty.displayName} Match Pacing",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Average Duration: $timeString per game",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = lineColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = timeString,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = lineColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
