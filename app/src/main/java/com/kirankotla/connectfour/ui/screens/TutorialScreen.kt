package com.kirankotla.connectfour.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirankotla.connectfour.model.GameMode
import com.kirankotla.connectfour.model.GameState
import com.kirankotla.connectfour.model.Player
import com.kirankotla.connectfour.rules.GameEngine
import com.kirankotla.connectfour.ui.components.ConnectFourBoard
import com.kirankotla.connectfour.ui.theme.*
import com.kirankotla.connectfour.util.HapticManager
import com.kirankotla.connectfour.util.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TutorialDemoScenario(
    val title: String,
    val icon: String,
    val subtitle: String,
    val targetColumn: Int,
    val badgeColor: Color
) {
    HORIZONTAL(
        title = "Horizontal Strike",
        icon = "↔️",
        subtitle = "Connect 4 in a horizontal row",
        targetColumn = 4,
        badgeColor = BrandCoral
    ),
    VERTICAL(
        title = "Vertical Tower",
        icon = "↕️",
        subtitle = "Stack 4 chips straight up",
        targetColumn = 3,
        badgeColor = BrandIndigo
    ),
    DIAGONAL(
        title = "Diagonal Climb",
        icon = "↗️",
        subtitle = "Form a 4-chip diagonal slant",
        targetColumn = 4,
        badgeColor = BrandAmber
    ),
    DEFENSE(
        title = "Defensive Block",
        icon = "🛡️",
        subtitle = "Block opponent's winning threat",
        targetColumn = 4,
        badgeColor = BrandTeal
    ),
    FREE_PLAY(
        title = "Free Practice",
        icon = "🎮",
        subtitle = "Experiment freely on the grid",
        targetColumn = -1,
        badgeColor = BrandPurple
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(
    onBack: () -> Unit,
    onCompleteTutorial: () -> Unit
) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager(context) }
    val hapticManager = remember { HapticManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var selectedScenario by remember { mutableStateOf(TutorialDemoScenario.HORIZONTAL) }
    var currentGameState by remember { mutableStateOf(buildScenarioState(TutorialDemoScenario.HORIZONTAL)) }
    var isDemoPlaying by remember { mutableStateOf(false) }
    var scenarioCompleted by remember { mutableStateOf(false) }
    var coachFeedback by remember { mutableStateOf(initialCoachMessage(TutorialDemoScenario.HORIZONTAL)) }

    fun resetScenario(scenario: TutorialDemoScenario) {
        selectedScenario = scenario
        currentGameState = buildScenarioState(scenario)
        scenarioCompleted = false
        isDemoPlaying = false
        coachFeedback = initialCoachMessage(scenario)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = BrandIndigo.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "ACADEMY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = BrandIndigo,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "Interactive Game Guide",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("tutorial_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { resetScenario(selectedScenario) }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Scenario", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Scenario Selection Carousel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TutorialDemoScenario.values().forEach { scenario ->
                    val isSelected = scenario == selectedScenario
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { resetScenario(scenario) }
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) scenario.badgeColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        color = if (isSelected) scenario.badgeColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(scenario.icon, fontSize = 16.sp)
                            Column {
                                Text(
                                    text = scenario.title,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) scenario.badgeColor else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = scenario.subtitle,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Real Demo Explanation Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = selectedScenario.badgeColor.copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(selectedScenario.icon, fontSize = 16.sp)
                                }
                            }
                            Column {
                                Text(
                                    text = selectedScenario.title,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Interactive Practice Scenario",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Auto Play Demo Button
                        if (selectedScenario != TutorialDemoScenario.FREE_PLAY) {
                            Button(
                                onClick = {
                                    if (!isDemoPlaying && !scenarioCompleted) {
                                        isDemoPlaying = true
                                        coroutineScope.launch {
                                            coachFeedback = "Dropping chip into target column ${selectedScenario.targetColumn}..."
                                            delay(600)
                                            val moveId = "col_${selectedScenario.targetColumn}"
                                            if (!currentGameState.isEdgeClaimed(moveId)) {
                                                val res = GameEngine.applyMove(currentGameState, moveId)
                                                currentGameState = res.newState
                                                scenarioCompleted = true
                                                soundManager.playLineStroke()
                                                hapticManager.performLineStroke()
                                                if (res.newState.winningLine != null) {
                                                    soundManager.playGameWin()
                                                    hapticManager.performVictory()
                                                }
                                                coachFeedback = completionCoachMessage(selectedScenario)
                                            }
                                            isDemoPlaying = false
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = selectedScenario.badgeColor),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                enabled = !scenarioCompleted && !isDemoPlaying
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isDemoPlaying) "Dropping..." else "▶ Auto Demo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Tactical explanation text
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = scenarioExplanation(selectedScenario),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    // Interactive Coach Feedback Banner
                    Surface(
                        color = if (scenarioCompleted) Color(0xFF10B981).copy(alpha = 0.15f) else BrandIndigo.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (scenarioCompleted) Color(0xFF10B981) else BrandIndigo.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(if (scenarioCompleted) "🎉" else "💡", fontSize = 16.sp)
                            Text(
                                text = coachFeedback,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (scenarioCompleted) Color(0xFF047857) else BrandIndigo
                            )
                        }
                    }
                }
            }

            // Real Connect Four Playable Board
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0B132B)),
                        contentAlignment = Alignment.Center
                    ) {
                        ConnectFourBoard(
                            gameState = currentGameState,
                            highContrast = false,
                            reduceMotion = false,
                            hintEdgeId = if (!scenarioCompleted && selectedScenario.targetColumn >= 0) "col_${selectedScenario.targetColumn}" else null,
                            onEdgeTap = { edgeId ->
                                if (!currentGameState.isEdgeClaimed(edgeId) && !isDemoPlaying) {
                                    val col = edgeId.removePrefix("col_").toIntOrNull() ?: 0
                                    val res = GameEngine.applyMove(currentGameState, edgeId)
                                    currentGameState = res.newState
                                    soundManager.playLineStroke()
                                    hapticManager.performLineStroke()

                                    if (selectedScenario != TutorialDemoScenario.FREE_PLAY) {
                                        if (col == selectedScenario.targetColumn) {
                                            scenarioCompleted = true
                                            if (res.newState.winningLine != null) {
                                                soundManager.playGameWin()
                                                hapticManager.performVictory()
                                            }
                                            coachFeedback = completionCoachMessage(selectedScenario)
                                        } else {
                                            coachFeedback = "You played column $col. Try column ${selectedScenario.targetColumn} to see the exact lesson move!"
                                        }
                                    } else {
                                        if (res.newState.winningLine != null) {
                                            soundManager.playGameWin()
                                            hapticManager.performVictory()
                                            coachFeedback = "Victory! You connected 4 in a row!"
                                        } else {
                                            coachFeedback = "Chip dropped in column $col. Keep playing to form 4-in-a-row!"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Bottom Board Action Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (scenarioCompleted) "Lesson Completed! ✨" else "👉 Tap column ${if (selectedScenario.targetColumn >= 0) selectedScenario.targetColumn else ""} to play",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (scenarioCompleted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        TextButton(
                            onClick = { resetScenario(selectedScenario) }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Replay Example", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Finish Academy Button
            Button(
                onClick = {
                    onCompleteTutorial()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("finish_tutorial_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "READY TO PLAY LIVE MATCHES!",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

internal fun buildScenarioState(scenario: TutorialDemoScenario): GameState {
    val initial = GameEngine.createInitialState(rows = 6, cols = 7, mode = GameMode.PASS_AND_PLAY)
    return when (scenario) {
        TutorialDemoScenario.HORIZONTAL -> {
            // Player 1 (Red) has 3 chips in bottom row (cols 1, 2, 3). Dropping into col 4 completes 4-in-a-row.
            var s = GameEngine.applyMove(initial, "col_1").newState // P1 at (5,1)
            s = GameEngine.applyMove(s, "col_0").newState // P2 at (5,0)
            s = GameEngine.applyMove(s, "col_2").newState // P1 at (5,2)
            s = GameEngine.applyMove(s, "col_6").newState // P2 at (5,6)
            s = GameEngine.applyMove(s, "col_3").newState // P1 at (5,3)
            s = GameEngine.applyMove(s, "col_0").newState // P2 at (4,0)
            s // Next is P1's turn
        }
        TutorialDemoScenario.VERTICAL -> {
            // Player 1 has 3 stacked chips in col 3: rows 5, 4, 3. Dropping into col 3 lands at (2,3) to win!
            var s = GameEngine.applyMove(initial, "col_3").newState // P1 at (5,3)
            s = GameEngine.applyMove(s, "col_2").newState // P2 at (5,2)
            s = GameEngine.applyMove(s, "col_3").newState // P1 at (4,3)
            s = GameEngine.applyMove(s, "col_4").newState // P2 at (5,4)
            s = GameEngine.applyMove(s, "col_3").newState // P1 at (3,3)
            s = GameEngine.applyMove(s, "col_2").newState // P2 at (4,2)
            s // Next is P1's turn
        }
        TutorialDemoScenario.DIAGONAL -> {
            // Diagonal: (5,1), (4,2), (3,3), and target (2,4)
            var s = GameEngine.applyMove(initial, "col_1").newState // P1 at (5,1)
            s = GameEngine.applyMove(s, "col_2").newState // P2 at (5,2)
            s = GameEngine.applyMove(s, "col_2").newState // P1 at (4,2)
            s = GameEngine.applyMove(s, "col_3").newState // P2 at (5,3)
            s = GameEngine.applyMove(s, "col_4").newState // P1 at (5,4)
            s = GameEngine.applyMove(s, "col_3").newState // P2 at (4,3)
            s = GameEngine.applyMove(s, "col_3").newState // P1 at (3,3)
            s = GameEngine.applyMove(s, "col_4").newState // P2 at (4,4)
            s = GameEngine.applyMove(s, "col_0").newState // P1 at (5,0)
            s = GameEngine.applyMove(s, "col_4").newState // P2 at (3,4)
            s // Next is P1's turn to drop into col 4 at (2,4)!
        }
        TutorialDemoScenario.DEFENSE -> {
            // Opponent (Player 2) has 3 chips in a row at (5,1), (5,2), (5,3). Player 1 must drop into col 4 to block!
            var s = GameEngine.applyMove(initial, "col_0").newState // P1 at (5,0)
            s = GameEngine.applyMove(s, "col_1").newState // P2 at (5,1)
            s = GameEngine.applyMove(s, "col_6").newState // P1 at (5,6)
            s = GameEngine.applyMove(s, "col_2").newState // P2 at (5,2)
            s = GameEngine.applyMove(s, "col_0").newState // P1 at (4,0)
            s = GameEngine.applyMove(s, "col_3").newState // P2 at (5,3)
            s // Next is P1's turn: must block col 4!
        }
        TutorialDemoScenario.FREE_PLAY -> {
            initial
        }
    }
}

private fun scenarioExplanation(scenario: TutorialDemoScenario): String = when (scenario) {
    TutorialDemoScenario.HORIZONTAL ->
        "1. Horizontal Alignment: Chips naturally line up across the columns. Red has 3 chips in columns 1, 2, and 3. Dropping into column 4 completes the 4-in-a-row to win the game!"
    TutorialDemoScenario.VERTICAL ->
        "2. Vertical Stacking: Every chip falls to the lowest available slot. When you stack 4 chips directly on top of each other in the same column, you score an instant vertical victory!"
    TutorialDemoScenario.DIAGONAL ->
        "3. Diagonal Mastery: Diagonal lines can ascend or descend. By stacking support chips underneath, you position your 4th chip at the exact height needed to connect the diagonal!"
    TutorialDemoScenario.DEFENSE ->
        "4. Tactical Defense: Watch your opponent's moves closely! Your opponent (Blue) has 3 chips aligned. If you don't drop into column 4 right now, they will win on their very next turn. Drop into column 4 to BLOCK them!"
    TutorialDemoScenario.FREE_PLAY ->
        "5. Free Practice: Experiment with drops, double-threat traps, and center control. When you're confident, jump into Solo vs AI, Campaign chapters, or Live Arena matches!"
}

private fun initialCoachMessage(scenario: TutorialDemoScenario): String = when (scenario) {
    TutorialDemoScenario.HORIZONTAL -> "Tap Column 4 (or hit '▶ Auto Demo') to complete the 4-in-a-row win!"
    TutorialDemoScenario.VERTICAL -> "Tap Column 3 to stack the 4th chip on top for a vertical win!"
    TutorialDemoScenario.DIAGONAL -> "Tap Column 4 to climb into the diagonal strike!"
    TutorialDemoScenario.DEFENSE -> "Danger! Opponent has 3 in a row. Tap Column 4 to block their victory!"
    TutorialDemoScenario.FREE_PLAY -> "Tap any column to practice dropping chips and forming lines!"
}

private fun completionCoachMessage(scenario: TutorialDemoScenario): String = when (scenario) {
    TutorialDemoScenario.HORIZONTAL -> "BRILLIANT! 4 chips connected horizontally. Notice the celebratory line glow!"
    TutorialDemoScenario.VERTICAL -> "TOWER SECURED! 4 chips stacked vertically. Pure gravity power!"
    TutorialDemoScenario.DIAGONAL -> "DIAGONAL MASTER! Ascending line completed. Diagonals are the hardest to defend against!"
    TutorialDemoScenario.DEFENSE -> "THREAT BLOCKED! 🛡️ You successfully shut down the opponent's winning chance!"
    TutorialDemoScenario.FREE_PLAY -> "Well played! You've got the hang of Connect Four!"
}
