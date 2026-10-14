package com.kirankotla.connectfour.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kirankotla.connectfour.model.GameState
import com.kirankotla.connectfour.rules.GameEngine
import com.kirankotla.connectfour.ui.theme.*

/**
 * Interactive Step-by-Step Onboarding Tutorial for Connect Four:
 * - Step 1: Gravity Column Drop (Tap a column to drop a chip)
 * - Step 2: 4-in-a-Row Victory (Horizontal line completion)
 * - Step 3: Diagonal & Stacking Strategy
 * - Step 4: Blocking Opponent Winning Threats
 * - Step 5: Ready to Play & Master
 */
@Composable
fun InteractiveTutorialOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableIntStateOf(0) }

    // Step 0: Clean board for practicing column drop
    var step0State by remember { mutableStateOf(GameEngine.createInitialState(rows = 6, cols = 7)) }
    var step0Tapped by remember { mutableStateOf(false) }

    // Step 1: 3 chips in bottom row (cols 1, 2, 3), drop into col 4 to win
    var step1State by remember {
        val initial = GameEngine.createInitialState(rows = 6, cols = 7)
        var s = GameEngine.applyMove(initial, "col_1").newState // P1 (r=5, c=1)
        s = GameEngine.applyMove(s, "col_0").newState // P2
        s = GameEngine.applyMove(s, "col_2").newState // P1 (r=5, c=2)
        s = GameEngine.applyMove(s, "col_0").newState // P2
        s = GameEngine.applyMove(s, "col_3").newState // P1 (r=5, c=3)
        s = GameEngine.applyMove(s, "col_6").newState // P2
        mutableStateOf(s)
    }
    var step1Completed by remember { mutableStateOf(false) }

    // Step 2: Stacking & diagonal practice
    var step2State by remember {
        val initial = GameEngine.createInitialState(rows = 6, cols = 7)
        var s = GameEngine.applyMove(initial, "col_3").newState // P1
        s = GameEngine.applyMove(s, "col_4").newState // P2
        s = GameEngine.applyMove(s, "col_3").newState // P1
        mutableStateOf(s)
    }
    var step2Tapped by remember { mutableStateOf(false) }

    // Step 3: Blocking decision
    var step3SelectedBlock by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(16.dp)
                .testTag("interactive_tutorial_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .testTag("tutorial_overlay_card"),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header: Mascot, Title, and Skip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VectorAvatar(
                                avatar = getAvatarById("piko"),
                                size = 32.dp
                            )
                            Column {
                                Text(
                                    text = "Connect Four Academy",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Step ${currentStep + 1} of 5",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("tutorial_skip_button")
                        ) {
                            Text("Skip", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Step Progress Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0..4) {
                            Box(
                                modifier = Modifier
                                    .height(6.dp)
                                    .width(if (i == currentStep) 24.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == currentStep) BrandIndigo else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }

                    // Dynamic Interactive Content per Step
                    when (currentStep) {
                        0 -> TutorialStep1DropChip(
                            gameState = step0State,
                            tapped = step0Tapped,
                            onTapEdge = { edgeId ->
                                if (!step0State.isEdgeClaimed(edgeId)) {
                                    val res = GameEngine.applyMove(step0State, edgeId)
                                    step0State = res.newState
                                    step0Tapped = true
                                }
                            }
                        )
                        1 -> TutorialStep2ConnectFour(
                            gameState = step1State,
                            completed = step1Completed,
                            onTapEdge = { edgeId ->
                                if (!step1State.isEdgeClaimed(edgeId)) {
                                    val res = GameEngine.applyMove(step1State, edgeId)
                                    step1State = res.newState
                                    if (res.newState.winningLine != null || res.newState.isGameOver) {
                                        step1Completed = true
                                    }
                                }
                            }
                        )
                        2 -> TutorialStep3Stacking(
                            gameState = step2State,
                            tapped = step2Tapped,
                            onTapEdge = { edgeId ->
                                if (!step2State.isEdgeClaimed(edgeId)) {
                                    val res = GameEngine.applyMove(step2State, edgeId)
                                    step2State = res.newState
                                    step2Tapped = true
                                }
                            }
                        )
                        3 -> TutorialStep4TacticalDefense(
                            selectedBlock = step3SelectedBlock,
                            onSelectBlock = { step3SelectedBlock = true }
                        )
                        4 -> TutorialStep5ReadyToPlay()
                    }

                    // Navigation Actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStep > 0) {
                            OutlinedButton(
                                onClick = { currentStep -= 1 },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Back")
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = {
                                if (currentStep < 4) {
                                    currentStep += 1
                                } else {
                                    onDismiss()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                            modifier = Modifier.testTag("tutorial_next_button")
                        ) {
                            Text(if (currentStep == 4) "Start Playing!" else "Next")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialStep1DropChip(
    gameState: GameState,
    tapped: Boolean,
    onTapEdge: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = BrandIndigo.copy(alpha = 0.10f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Rule 1: Gravity Column Drop",
                    fontWeight = FontWeight.Bold,
                    color = BrandIndigo,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Take turns dropping chips into columns. Each chip falls straight to the lowest available slot!",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0A0F1E)),
            contentAlignment = Alignment.Center
        ) {
            ConnectFourBoard(
                gameState = gameState,
                highContrast = false,
                reduceMotion = false,
                hintEdgeId = if (!tapped) "col_3" else null,
                onEdgeTap = onTapEdge,
                modifier = Modifier.fillMaxSize()
            )
        }

        Surface(
            color = if (tapped) Color(0xFF10B981).copy(alpha = 0.15f) else BrandAmber.copy(alpha = 0.15f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(if (tapped) "✅" else "👉", fontSize = 14.sp)
                Text(
                    text = if (tapped) "Chip dropped! Gravity pulled it to the bottom row." else "Tap column 3 (marked with ⬇️) to drop your chip!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (tapped) Color(0xFF10B981) else Color(0xFFD97706)
                )
            }
        }
    }
}

@Composable
private fun TutorialStep2ConnectFour(
    gameState: GameState,
    completed: Boolean,
    onTapEdge: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = BrandCoral.copy(alpha = 0.10f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Rule 2: Connect 4 to Win!",
                    fontWeight = FontWeight.Bold,
                    color = BrandCoral,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Be the first player to form a line of 4 consecutive chips (horizontal, vertical, or diagonal).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0A0F1E)),
            contentAlignment = Alignment.Center
        ) {
            ConnectFourBoard(
                gameState = gameState,
                highContrast = false,
                reduceMotion = false,
                hintEdgeId = if (!completed) "col_4" else null,
                onEdgeTap = onTapEdge,
                modifier = Modifier.fillMaxSize()
            )
        }

        Surface(
            color = if (completed) Color(0xFF10B981).copy(alpha = 0.15f) else BrandCoral.copy(alpha = 0.15f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(if (completed) "⚡" else "🎯", fontSize = 14.sp)
                Text(
                    text = if (completed) "VICTORY! Notice the 2-second neon laser highlight!" else "Tap column 4 (⬇️) to complete 4-in-a-row!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (completed) Color(0xFF10B981) else BrandCoral
                )
            }
        }
    }
}

@Composable
private fun TutorialStep3Stacking(
    gameState: GameState,
    tapped: Boolean,
    onTapEdge: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = BrandAmber.copy(alpha = 0.10f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Strategy: Stacking & Height Advantage",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97706),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Chips stack on top of each other. Control center columns early to maximize your horizontal and diagonal branching options!",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0A0F1E)),
            contentAlignment = Alignment.Center
        ) {
            ConnectFourBoard(
                gameState = gameState,
                highContrast = false,
                reduceMotion = false,
                hintEdgeId = "col_3",
                onEdgeTap = onTapEdge,
                modifier = Modifier.fillMaxSize()
            )
        }

        Surface(
            color = if (tapped) Color(0xFF10B981).copy(alpha = 0.15f) else BrandIndigo.copy(alpha = 0.15f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(if (tapped) "🗼" else "⚡", fontSize = 14.sp)
                Text(
                    text = if (tapped) "Great! You stacked a chip higher in column 3." else "Tap column 3 to stack on top of the existing chips!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (tapped) Color(0xFF10B981) else BrandIndigo
                )
            }
        }
    }
}

@Composable
private fun TutorialStep4TacticalDefense(
    selectedBlock: Boolean,
    onSelectBlock: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = BrandPurple.copy(alpha = 0.10f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Tactics: Defense & Blocking",
                    fontWeight = FontWeight.Bold,
                    color = BrandPurple,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Always monitor your opponent's line! If they reach 3 in a row, block them before they drop the winning chip.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Opponent has 3 chips aligned with an open slot. What is the right move?",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )

                // Correct Block Option
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectBlock() }
                        .border(
                            width = if (selectedBlock) 2.dp else 1.dp,
                            color = if (selectedBlock) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp)
                        ),
                    color = if (selectedBlock) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(if (selectedBlock) "🛡️" else "⚪", fontSize = 16.sp)
                        Column {
                            Text("Drop into that column to BLOCK their 4th chip!", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Stops their victory cold and keeps you in the game.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialStep5ReadyToPlay() {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        Text("🏆", fontSize = 48.sp)
        Text(
            text = "You're Ready for Battle!",
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "You know the fundamentals: gravity drops, 4-in-a-row connections, stacking physics, and blocking. Compete against multiple AI bots, local pass & play, or online!",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
