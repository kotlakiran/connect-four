package com.kirankotla.connectfour.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
    FORK(
        title = "The Fork (Double Threat)",
        icon = "🔱",
        subtitle = "Unstoppable double 4-in-a-row combination",
        targetColumn = 4,
        badgeColor = BrandCoral
    ),
    SEVEN_TRAP(
        title = "The 7-Trap Combination",
        icon = "7️⃣",
        subtitle = "Intersecting horizontal & diagonal threats",
        targetColumn = 3,
        badgeColor = BrandIndigo
    ),
    MIDGAME_DIAGONAL(
        title = "Crowded Board Diagonal",
        icon = "↗️",
        subtitle = "Ascend diagonally through mid-game defense",
        targetColumn = 4,
        badgeColor = BrandAmber
    ),
    VERTICAL_STACK(
        title = "Pillar Overpower (Vertical)",
        icon = "↕️",
        subtitle = "Column depth pressure in contested match",
        targetColumn = 3,
        badgeColor = BrandTeal
    ),
    DEFENSIVE_BLOCK(
        title = "Mid-Game Threat Block",
        icon = "🛡️",
        subtitle = "Spot & shutdown opponent's hidden win",
        targetColumn = 2,
        badgeColor = CyberCyan
    ),
    FREE_PLAY(
        title = "Free Practice",
        icon = "🎮",
        subtitle = "Experiment freely with combinations",
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

    var selectedScenario by remember { mutableStateOf(TutorialDemoScenario.FORK) }
    var currentGameState by remember { mutableStateOf(buildScenarioState(TutorialDemoScenario.FORK)) }
    var isDemoPlaying by remember { mutableStateOf(false) }
    var scenarioCompleted by remember { mutableStateOf(false) }
    var coachFeedback by remember { mutableStateOf(initialCoachMessage(TutorialDemoScenario.FORK)) }

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

            // ====================================================
            // WINNING COMBINATIONS REFERENCE CARD (69 TOTAL WINS)
            // ====================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("combinations_overview_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = BrandIndigo.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("📐", fontSize = 14.sp)
                            }
                        }
                        Text(
                            text = "Connect Four Math: 69 Total Winning Lines",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Every match has 69 possible ways to connect 4 on a standard 6x7 board:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ComboStatPill(icon = "↔️", count = "24", label = "Horizontal")
                        ComboStatPill(icon = "↕️", count = "21", label = "Vertical")
                        ComboStatPill(icon = "↗️", count = "24", label = "Diagonals")
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("💡", fontSize = 16.sp)
                            Text(
                                text = "Pro Strategy: Column 3 (Center) belongs to 24 winning lines — more than any other column! Real matches are decided by who controls the center.",
                                fontSize = 11.sp,
                                color = BrandAmber,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
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
        TutorialDemoScenario.FORK -> {
            // Real mid-game match (14 moves). Both players contested center and flanks.
            // Red has established (5,1), (5,2), (5,3) and (5,5). Dropping into Column 4
            // completes (5,1)-(5,4) through the crowded corridor!
            var s = GameEngine.applyMove(initial, "col_3").newState // P1 (5,3)
            s = GameEngine.applyMove(s, "col_3").newState // P2 (4,3)
            s = GameEngine.applyMove(s, "col_2").newState // P1 (5,2)
            s = GameEngine.applyMove(s, "col_2").newState // P2 (4,2)
            s = GameEngine.applyMove(s, "col_1").newState // P1 (5,1)
            s = GameEngine.applyMove(s, "col_0").newState // P2 (5,0)
            s = GameEngine.applyMove(s, "col_1").newState // P1 (4,1)
            s = GameEngine.applyMove(s, "col_0").newState // P2 (4,0)
            s = GameEngine.applyMove(s, "col_2").newState // P1 (3,2)
            s = GameEngine.applyMove(s, "col_3").newState // P2 (3,3)
            s = GameEngine.applyMove(s, "col_5").newState // P1 (5,5)
            s = GameEngine.applyMove(s, "col_6").newState // P2 (5,6)
            s = GameEngine.applyMove(s, "col_5").newState // P1 (4,5)
            s = GameEngine.applyMove(s, "col_6").newState // P2 (4,6)
            s // Next is P1 (Red): Drop into Column 4 to strike!
        }
        TutorialDemoScenario.SEVEN_TRAP -> {
            // Real 12-move contest. Red built horizontal threats at (5,1), (5,2), (5,4)
            // with surrounding action in cols 0, 5, 6. Dropping into center Col 3 drops the keystone chip!
            var s = GameEngine.applyMove(initial, "col_1").newState // P1 (5,1)
            s = GameEngine.applyMove(s, "col_0").newState // P2 (5,0)
            s = GameEngine.applyMove(s, "col_2").newState // P1 (5,2)
            s = GameEngine.applyMove(s, "col_0").newState // P2 (4,0)
            s = GameEngine.applyMove(s, "col_4").newState // P1 (5,4)
            s = GameEngine.applyMove(s, "col_5").newState // P2 (5,5)
            s = GameEngine.applyMove(s, "col_2").newState // P1 (4,2)
            s = GameEngine.applyMove(s, "col_6").newState // P2 (5,6)
            s = GameEngine.applyMove(s, "col_4").newState // P1 (4,4)
            s = GameEngine.applyMove(s, "col_6").newState // P2 (4,6)
            s = GameEngine.applyMove(s, "col_1").newState // P1 (4,1)
            s = GameEngine.applyMove(s, "col_5").newState // P2 (4,5)
            s // Next is P1: Drop into Col 3 to win!
        }
        TutorialDemoScenario.MIDGAME_DIAGONAL -> {
            // Real 14-move crowded match with chips stacked across columns at varying heights.
            // Red built diagonal steps at (5,1), (4,2), (3,3). Col 4 is filled up to row 3 (5,4),(4,4),(3,4).
            // Dropping into Col 4 lands at (2,4) to connect the 4-chip ascending diagonal!
            var s = GameEngine.applyMove(initial, "col_1").newState // P1 (5,1)
            s = GameEngine.applyMove(s, "col_2").newState // P2 (5,2)
            s = GameEngine.applyMove(s, "col_2").newState // P1 (4,2)
            s = GameEngine.applyMove(s, "col_3").newState // P2 (5,3)
            s = GameEngine.applyMove(s, "col_0").newState // P1 (5,0)
            s = GameEngine.applyMove(s, "col_3").newState // P2 (4,3)
            s = GameEngine.applyMove(s, "col_3").newState // P1 (3,3)
            s = GameEngine.applyMove(s, "col_4").newState // P2 (5,4)
            s = GameEngine.applyMove(s, "col_5").newState // P1 (5,5)
            s = GameEngine.applyMove(s, "col_4").newState // P2 (4,4)
            s = GameEngine.applyMove(s, "col_6").newState // P1 (5,6)
            s = GameEngine.applyMove(s, "col_4").newState // P2 (3,4)
            s = GameEngine.applyMove(s, "col_5").newState // P1 (4,5)
            s = GameEngine.applyMove(s, "col_0").newState // P2 (4,0)
            s // Next is P1: Drop into Col 4 to complete diagonal at (2,4)!
        }
        TutorialDemoScenario.VERTICAL_STACK -> {
            // Real 12-move contest. Red stacked Col 3 at (5,3), (4,3), (3,3)
            // with active flanking play in cols 0, 1, 2, 4, 5. Dropping in Col 3 lands at (2,3) to win!
            var s = GameEngine.applyMove(initial, "col_3").newState // P1 (5,3)
            s = GameEngine.applyMove(s, "col_2").newState // P2 (5,2)
            s = GameEngine.applyMove(s, "col_3").newState // P1 (4,3)
            s = GameEngine.applyMove(s, "col_4").newState // P2 (5,4)
            s = GameEngine.applyMove(s, "col_3").newState // P1 (3,3)
            s = GameEngine.applyMove(s, "col_2").newState // P2 (4,2)
            s = GameEngine.applyMove(s, "col_1").newState // P1 (5,1)
            s = GameEngine.applyMove(s, "col_4").newState // P2 (4,4)
            s = GameEngine.applyMove(s, "col_5").newState // P1 (5,5)
            s = GameEngine.applyMove(s, "col_2").newState // P2 (3,2)
            s = GameEngine.applyMove(s, "col_0").newState // P1 (5,0)
            s = GameEngine.applyMove(s, "col_5").newState // P2 (4,5)
            s // Next is P1: Drop into Col 3 for vertical win!
        }
        TutorialDemoScenario.DEFENSIVE_BLOCK -> {
            // Real 12-move match. Cyan (Player 2) has established 3-in-a-row at (5,3), (5,4), (5,5).
            // If Red doesn't drop into Col 2 at (5,2), Cyan wins on the very next turn!
            var s = GameEngine.applyMove(initial, "col_0").newState // P1 (5,0)
            s = GameEngine.applyMove(s, "col_3").newState // P2 (5,3)
            s = GameEngine.applyMove(s, "col_6").newState // P1 (5,6)
            s = GameEngine.applyMove(s, "col_4").newState // P2 (5,4)
            s = GameEngine.applyMove(s, "col_0").newState // P1 (4,0)
            s = GameEngine.applyMove(s, "col_5").newState // P2 (5,5)
            s = GameEngine.applyMove(s, "col_6").newState // P1 (4,6)
            s = GameEngine.applyMove(s, "col_3").newState // P2 (4,3)
            s = GameEngine.applyMove(s, "col_1").newState // P1 (5,1)
            s = GameEngine.applyMove(s, "col_4").newState // P2 (4,4)
            s = GameEngine.applyMove(s, "col_1").newState // P1 (4,1)
            s = GameEngine.applyMove(s, "col_5").newState // P2 (4,5)
            s // Next is P1: MUST drop into Col 2 to block!
        }
        TutorialDemoScenario.FREE_PLAY -> {
            initial
        }
    }
}

private fun scenarioExplanation(scenario: TutorialDemoScenario): String = when (scenario) {
    TutorialDemoScenario.FORK ->
        "1. The Fork (Double Threat): In real matches, elite players engineer a 'Fork' — creating multiple win threats at once so the opponent cannot block both. In this 14-chip match, Red has contested both sides and center. Dropping into Column 4 completes the 4-in-a-row through the bottom corridor!"
    TutorialDemoScenario.SEVEN_TRAP ->
        "2. The 7-Trap Combination: Real matches often feature intersecting lines that form a '7' shape. Here, Red laid groundwork across columns 1, 2, and 4 amidst active defense. Dropping into Column 3 places the central keystone piece, joining both flanks into an unstoppable 4-in-a-row!"
    TutorialDemoScenario.MIDGAME_DIAGONAL ->
        "3. Crowded Board Diagonal: Real matches are packed with 14+ chips. Diagonals are the stealthiest win combination because chips sit at different column heights. Red built stepping stones at (5,1), (4,2), and (3,3). Dropping into Column 4 stacks the final chip onto (2,4) to slice diagonally through the defense!"
    TutorialDemoScenario.VERTICAL_STACK ->
        "4. Pillar Overpower (Vertical Tower): When both players clash over center control, vertical towers punish an opponent who neglects column depth. Red has stacked 3 chips in Column 3 amidst heavy side skirmishes. Dropping into Column 3 lands on row 2 for an instant tower victory!"
    TutorialDemoScenario.DEFENSIVE_BLOCK ->
        "5. Mid-Game Threat Shutdown: In real matches, spotting your opponent's combinations before they strike is critical! Your opponent (Cyan) has 3 chips lined up at columns 3, 4, 5. If you don't drop into Column 2 right now, they win on their next move. Drop into Column 2 to shut them down!"
    TutorialDemoScenario.FREE_PLAY ->
        "6. Free Match Sandbox: Practice setting up forks, building diagonals, and controlling the center column on an open board. Reset or undo anytime!"
}

private fun initialCoachMessage(scenario: TutorialDemoScenario): String = when (scenario) {
    TutorialDemoScenario.FORK -> "Realistic Mid-Game: Red has dual corridors open. Tap Column 4 (or hit '▶ Auto Demo') to strike!"
    TutorialDemoScenario.SEVEN_TRAP -> "Center Pillar Key: Tap Column 3 to drop the keystone chip and complete the 4-in-a-row!"
    TutorialDemoScenario.MIDGAME_DIAGONAL -> "Stealth Diagonal: Columns 1-3 have the steps. Tap Column 4 to climb into the diagonal win!"
    TutorialDemoScenario.VERTICAL_STACK -> "Depth Attack: Column 3 is stacked 3 high. Tap Column 3 to drop the winning 4th chip!"
    TutorialDemoScenario.DEFENSIVE_BLOCK -> "🚨 CRITICAL DEFENSE: Opponent has 3 in a row! Tap Column 2 to block their win!"
    TutorialDemoScenario.FREE_PLAY -> "Sandbox Mode: Tap columns to drop chips and test custom tactical combinations!"
}

private fun completionCoachMessage(scenario: TutorialDemoScenario): String = when (scenario) {
    TutorialDemoScenario.FORK -> "🏆 FORK EXECUTED! 4 chips connected across the bottom corridor. Cyan was helpless to stop it!"
    TutorialDemoScenario.SEVEN_TRAP -> "🎯 7-TRAP COMPLETED! The center pillar connected both wings into a decisive 4-in-a-row victory!"
    TutorialDemoScenario.MIDGAME_DIAGONAL -> "⚡ DIAGONAL MASTER! The chip landed at (2,4) through a crowded 15-chip board. Incredible tactical vision!"
    TutorialDemoScenario.VERTICAL_STACK -> "💥 TOWER SECURED! Column 3 stacked 4 high. Gravity and column depth delivered the win!"
    TutorialDemoScenario.DEFENSIVE_BLOCK -> "🛡️ THREAT SHUTDOWN! You blocked Column 2 just in time, neutralizing Cyan's winning streak!"
    TutorialDemoScenario.FREE_PLAY -> "Great practice! You're ready to dominate live matches!"
}

@Composable
private fun ComboStatPill(icon: String, count: String, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(icon, fontSize = 13.sp)
                Text(count, fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
    }
}

