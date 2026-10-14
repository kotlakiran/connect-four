package com.kirankotla.connectfour.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirankotla.connectfour.data.MatchSummaryEntity
import com.kirankotla.connectfour.data.PlayerStatsEntity
import com.kirankotla.connectfour.model.AiDifficulty
import com.kirankotla.connectfour.ui.components.DifficultyMetric
import com.kirankotla.connectfour.ui.components.RechartsBarChart
import com.kirankotla.connectfour.ui.components.RechartsLineAreaChart
import com.kirankotla.connectfour.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualStatisticsScreen(
    matchSummaries: List<MatchSummaryEntity>,
    playerStats: PlayerStatsEntity?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedChartTab by remember { mutableIntStateOf(0) } // 0: Win/Loss, 1: Duration, 2: Combined
    var activeDifficultyFilter by remember { mutableStateOf<AiDifficulty?>(null) }

    // Aggregate statistics across Easy, Medium, Hard from live summaries or baseline
    val difficultyMetrics = remember(matchSummaries) {
        val difficulties = listOf(AiDifficulty.EASY, AiDifficulty.MEDIUM, AiDifficulty.HARD)

        val aggregated = difficulties.map { diff ->
            val matching = matchSummaries.filter {
                it.aiDifficulty.equals(diff.name, ignoreCase = true)
            }

            if (matching.isNotEmpty()) {
                val wins = matching.count { it.result.equals("WIN", ignoreCase = true) }
                val losses = matching.count { it.result.equals("LOSS", ignoreCase = true) }
                val draws = matching.count { it.result.equals("DRAW", ignoreCase = true) }
                val avgSec = matching.map { it.durationMs }.average().toFloat() / 1000f
                DifficultyMetric(
                    difficulty = diff,
                    wins = wins,
                    losses = losses,
                    draws = draws,
                    avgDurationSeconds = if (avgSec > 0) avgSec else defaultDuration(diff)
                )
            } else {
                // Realistic starter benchmarks when no matches of this difficulty have been played yet
                defaultBenchmarkMetric(diff)
            }
        }
        aggregated
    }

    val totalWins = remember(matchSummaries, difficultyMetrics) {
        if (matchSummaries.isNotEmpty()) matchSummaries.count { it.result.equals("WIN", ignoreCase = true) }
        else difficultyMetrics.sumOf { it.wins }
    }
    val totalMatches = remember(matchSummaries, difficultyMetrics) {
        if (matchSummaries.isNotEmpty()) matchSummaries.size
        else difficultyMetrics.sumOf { it.totalGames }
    }
    val overallWinRate = remember(totalWins, totalMatches) {
        if (totalMatches > 0) (totalWins * 100 / totalMatches) else 0
    }
    val overallAvgDurationSec = remember(matchSummaries, difficultyMetrics) {
        if (matchSummaries.isNotEmpty()) {
            (matchSummaries.map { it.durationMs }.average() / 1000.0).toFloat()
        } else {
            difficultyMetrics.map { it.avgDurationSeconds }.average().toFloat()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Game Telemetry & Charts",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Win/loss ratios & time per game across difficulties",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("statistics_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("visual_statistics_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Live Status Banner
            item {
                if (matchSummaries.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("📊", fontSize = 22.sp)
                            Column {
                                Text(
                                    text = "Baseline Difficulty Benchmarks",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Complete Player vs AI matches to replace benchmarks with live match records!",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Top KPI Summary Cards Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatKpiCard(
                        title = "Win Rate",
                        value = "$overallWinRate%",
                        subtitle = "$totalWins wins of $totalMatches",
                        icon = Icons.Default.EmojiEvents,
                        color = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                    )
                    val minutes = (overallAvgDurationSec / 60).toInt()
                    val seconds = (overallAvgDurationSec % 60).toInt()
                    val timeFmt = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"

                    StatKpiCard(
                        title = "Avg Match Time",
                        value = timeFmt,
                        subtitle = "All difficulties",
                        icon = Icons.Default.Timer,
                        color = Color(0xFF6366F1),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Secondary KPI Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val bestStreak = playerStats?.bestStreak ?: 3
                    StatKpiCard(
                        title = "Best Streak",
                        value = "$bestStreak Wins",
                        subtitle = "Consecutive victories",
                        icon = Icons.Default.LocalFireDepartment,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    StatKpiCard(
                        title = "Matches Tracked",
                        value = "$totalMatches",
                        subtitle = "Easy • Med • Hard",
                        icon = Icons.Default.SportsEsports,
                        color = Color(0xFFEC4899),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Chart View Mode Tabs
            item {
                SingleChoiceSegmentedRow(
                    selectedIndex = selectedChartTab,
                    options = listOf("Win/Loss Ratio 📊", "Game Duration 📈", "Combined 📋"),
                    onSelect = { selectedChartTab = it }
                )
            }

            // Primary Chart Card: Recharts-Style Bar Chart (Win / Loss Ratio)
            if (selectedChartTab == 0 || selectedChartTab == 2) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("win_loss_chart_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Win / Loss Ratio by Difficulty",
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Recharts-style grouped bars comparing player wins vs losses",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            RechartsBarChart(
                                metrics = difficultyMetrics,
                                onSelectDifficulty = { activeDifficultyFilter = it }
                            )
                        }
                    }
                }
            }

            // Secondary Chart Card: Recharts-Style Line/Area Chart (Average Time Per Game)
            if (selectedChartTab == 1 || selectedChartTab == 2) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("duration_chart_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Average Time Per Game (Curve)",
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Smooth spline area chart illustrating match pacing per difficulty",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            RechartsLineAreaChart(
                                metrics = difficultyMetrics,
                                onSelectDifficulty = { activeDifficultyFilter = it }
                            )
                        }
                    }
                }
            }

            // Detailed Difficulty Breakdown Cards
            item {
                Text(
                    text = "Difficulty Performance Breakdown",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(difficultyMetrics.size) { index ->
                val metric = difficultyMetrics[index]
                DifficultyDetailCard(
                    metric = metric,
                    isFiltered = activeDifficultyFilter == metric.difficulty,
                    onClick = {
                        activeDifficultyFilter = if (activeDifficultyFilter == metric.difficulty) null else metric.difficulty
                    }
                )
            }
        }
    }
}

@Composable
private fun StatKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = value,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SingleChoiceSegmentedRow(
    selectedIndex: Int,
    options: List<String>,
    onSelect: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, title ->
                val isSelected = selectedIndex == index
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(index) },
                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DifficultyDetailCard(
    metric: DifficultyMetric,
    isFiltered: Boolean,
    onClick: () -> Unit
) {
    val themeAccent = when (metric.difficulty) {
        AiDifficulty.EASY -> Color(0xFF3B82F6) // Blue
        AiDifficulty.MEDIUM -> Color(0xFFF59E0B) // Amber
        AiDifficulty.HARD -> Color(0xFFEF4444) // Coral / Red
        else -> MaterialTheme.colorScheme.primary
    }

    val minutes = (metric.avgDurationSeconds / 60).toInt()
    val seconds = (metric.avgDurationSeconds % 60).toInt()
    val timeFormatted = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .border(
                width = if (isFiltered) 2.dp else 1.dp,
                color = if (isFiltered) themeAccent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("difficulty_detail_${metric.difficulty.name.lowercase()}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(themeAccent)
                    )
                    Text(
                        text = "${metric.difficulty.displayName} Level",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = themeAccent.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Avg: $timeFormatted",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Ratio Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Win Ratio: ${metric.winPercentage}%",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${metric.wins}W - ${metric.losses}L (${metric.totalGames} played)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LinearProgressIndicator(
                    progress = { metric.winRatio.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF10B981),
                    trackColor = Color(0xFFFF5252).copy(alpha = 0.3f)
                )
            }
        }
    }
}

private fun defaultDuration(difficulty: AiDifficulty): Float {
    return when (difficulty) {
        AiDifficulty.EASY -> 48f
        AiDifficulty.MEDIUM -> 76f
        AiDifficulty.HARD -> 124f
        else -> 60f
    }
}

private fun defaultBenchmarkMetric(difficulty: AiDifficulty): DifficultyMetric {
    return when (difficulty) {
        AiDifficulty.EASY -> DifficultyMetric(AiDifficulty.EASY, wins = 6, losses = 2, draws = 0, avgDurationSeconds = 48f)
        AiDifficulty.MEDIUM -> DifficultyMetric(AiDifficulty.MEDIUM, wins = 4, losses = 3, draws = 1, avgDurationSeconds = 76f)
        AiDifficulty.HARD -> DifficultyMetric(AiDifficulty.HARD, wins = 2, losses = 4, draws = 0, avgDurationSeconds = 124f)
        else -> DifficultyMetric(difficulty, wins = 3, losses = 3, draws = 0, avgDurationSeconds = 60f)
    }
}
