package com.kirankotla.connectfour.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirankotla.connectfour.model.AiDifficulty
import com.kirankotla.connectfour.model.GameMode
import com.kirankotla.connectfour.model.TurnRule
import com.kirankotla.connectfour.ui.components.AvatarSelectionDialog
import com.kirankotla.connectfour.ui.components.MiniBoardDiagram
import com.kirankotla.connectfour.ui.components.PlayerAvatarSelectorCard
import com.kirankotla.connectfour.ui.components.VectorAvatar
import com.kirankotla.connectfour.ui.components.getAvatarById
import com.kirankotla.connectfour.ui.theme.*
import com.kirankotla.connectfour.util.HapticManager
import com.kirankotla.connectfour.util.SoundManager
import com.kirankotla.connectfour.viewmodel.SetupState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    setupState: SetupState,
    onGridSizeChanged: (Int) -> Unit,
    onBoardDimensionsChanged: ((Int, Int) -> Unit)? = null,
    onDifficultyChanged: (AiDifficulty) -> Unit,
    onTurnRuleChanged: (TurnRule) -> Unit,
    onToggleFirstPlayer: (Boolean) -> Unit,
    onPlayer1NameChanged: ((String) -> Unit)? = null,
    onPlayer2NameChanged: ((String) -> Unit)? = null,
    onTogglePlayer2StartsFirst: ((Boolean) -> Unit)? = null,
    onPlayer1AvatarChanged: ((String) -> Unit)? = null,
    onPlayer2AvatarChanged: ((String) -> Unit)? = null,
    onTurnTimeChanged: (Int) -> Unit = {},
    onPowerUpModeChanged: (Boolean) -> Unit = {},
    onStartMatch: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val soundManager = remember(context) { SoundManager(context) }
    val hapticManager = remember(context) { HapticManager(context) }

    var showP1AvatarDialog by remember { mutableStateOf(false) }
    var showP2AvatarDialog by remember { mutableStateOf(false) }

    if (showP1AvatarDialog) {
        AvatarSelectionDialog(
            currentAvatarId = setupState.player1AvatarId,
            title = if (setupState.mode == GameMode.PASS_AND_PLAY) "Player 1 Avatar" else "Your Avatar",
            onAvatarSelected = { onPlayer1AvatarChanged?.invoke(it) },
            onDismiss = { showP1AvatarDialog = false }
        )
    }

    if (showP2AvatarDialog) {
        AvatarSelectionDialog(
            currentAvatarId = setupState.player2AvatarId,
            title = "Player 2 Avatar",
            onAvatarSelected = { onPlayer2AvatarChanged?.invoke(it) },
            onDismiss = { showP2AvatarDialog = false }
        )
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
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "LOBBY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "${setupState.mode.displayName} Staging",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            soundManager.playUiTap()
                            onBack()
                        },
                        modifier = Modifier.testTag("setup_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = Color.Transparent,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Surface(
                        onClick = {
                            soundManager.playUiTap()
                            hapticManager.performUiTap()
                            onStartMatch()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("start_match_button"),
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 6.dp,
                        color = Color.Transparent
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.horizontalGradient(listOf(BrandIndigo, CyberCyan)))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "START BATTLE",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ====================================================
            // 1. PASS & PLAY / MODE HERO BANNER
            // ====================================================
            if (setupState.mode == GameMode.PASS_AND_PLAY) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = BrandCoral.copy(alpha = 0.10f),
                    border = BorderStroke(1.5.dp, BrandCoral.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(PassPlayGradient)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Column {
                            Text(
                                text = "Local Pass & Play Arena",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleMedium,
                                color = BrandCoral
                            )
                            Text(
                                text = "Two players battle on this same device with tactile handover turns.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Player Customization Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BrandCoral.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = BrandCoral, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Player Names & Mascots", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("Personalize both players before starting", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        OutlinedTextField(
                            value = setupState.player1Name,
                            onValueChange = { onPlayer1NameChanged?.invoke(it) },
                            label = { Text("Player 1 Name (Blue)") },
                            singleLine = true,
                            leadingIcon = { Text("🟦", fontSize = 16.sp) },
                            trailingIcon = {
                                if (setupState.player1Name.isNotEmpty()) {
                                    IconButton(onClick = { onPlayer1NameChanged?.invoke("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_1_name_input"),
                            shape = RoundedCornerShape(14.dp)
                        )

                        PlayerAvatarSelectorCard(
                            avatarId = setupState.player1AvatarId,
                            playerName = setupState.player1Name,
                            label = "PLAYER 1 VECTOR AVATAR",
                            onOpenPicker = { showP1AvatarDialog = true }
                        )

                        OutlinedTextField(
                            value = setupState.player2Name,
                            onValueChange = { onPlayer2NameChanged?.invoke(it) },
                            label = { Text("Player 2 Name (Coral)") },
                            singleLine = true,
                            leadingIcon = { Text("🟥", fontSize = 16.sp) },
                            trailingIcon = {
                                if (setupState.player2Name.isNotEmpty()) {
                                    IconButton(onClick = { onPlayer2NameChanged?.invoke("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_2_name_input"),
                            shape = RoundedCornerShape(14.dp)
                        )

                        PlayerAvatarSelectorCard(
                            avatarId = setupState.player2AvatarId,
                            playerName = setupState.player2Name,
                            label = "PLAYER 2 VECTOR AVATAR",
                            onOpenPicker = { showP2AvatarDialog = true }
                        )
                    }
                }
            }

            // VS AI & Dice Mode Avatar Card
            if (setupState.mode == GameMode.VS_AI || setupState.mode == GameMode.DICE_MODE) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Your Match Avatar", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        PlayerAvatarSelectorCard(
                            avatarId = setupState.player1AvatarId,
                            playerName = setupState.player1Name,
                            label = "CHOSEN AVATAR VS AI",
                            onOpenPicker = { showP1AvatarDialog = true }
                        )
                    }
                }
            }

            // ====================================================
            // 2. BOARD SIZE VISUAL SELECTION (WITH MINI DIAGRAMS)
            // ====================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrandIndigo.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.GridOn, contentDescription = null, tint = BrandIndigo, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Board Dimensions", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                            Text("Selected: ${setupState.cols}x${setupState.rows} Grid (${setupState.cols * setupState.rows} Slots)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        data class ConnectFourPreset(val rows: Int, val cols: Int, val label: String, val chipText: String)

                        val gridSizes = listOf(
                            ConnectFourPreset(6, 7, "Classic", "7x6 (4)"),
                            ConnectFourPreset(5, 6, "Compact", "6x5 (4)"),
                            ConnectFourPreset(7, 8, "Grand", "8x7 (4)"),
                            ConnectFourPreset(4, 5, "Mini", "5x4 (3)")
                        )

                        gridSizes.forEach { preset ->
                            val isSelected = setupState.rows == preset.rows && setupState.cols == preset.cols
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        soundManager.playUiTap()
                                        if (onBoardDimensionsChanged != null) {
                                            onBoardDimensionsChanged(preset.rows, preset.cols)
                                        } else {
                                            onGridSizeChanged(preset.rows)
                                        }
                                    }
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) BrandIndigo else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(16.dp)
                                    ),
                                color = if (isSelected) BrandIndigo.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Mini Board Canvas
                                    MiniBoardDiagram(
                                        rows = preset.rows,
                                        cols = preset.cols,
                                        accentColor = if (isSelected) BrandIndigo else Color.Gray,
                                        isHighlighted = isSelected,
                                        modifier = Modifier.size(36.dp)
                                    )

                                    Text(
                                        text = "${preset.cols}x${preset.rows}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = if (isSelected) BrandIndigo else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = preset.label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) BrandIndigo else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = preset.chipText,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    Text("Turn Timer", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 15, 30).forEach { seconds ->
                            val isSelected = setupState.turnTimeSeconds == seconds
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    soundManager.playUiTap()
                                    onTurnTimeChanged(seconds)
                                },
                                label = { Text("${seconds}s", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("turn_timer_chip_$seconds")
                            )
                        }
                    }
                }
            }

            // ====================================================
            // 3. AI OPPONENT PERSONALITY & SKILL
            // ====================================================
            if (setupState.mode == GameMode.VS_AI || setupState.mode == GameMode.QUICK_PLAY || setupState.mode == GameMode.DICE_MODE) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BrandPurple.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SmartToy, contentDescription = null, tint = BrandPurple, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("AI Opponent Skill", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                                Text("Select the AI tactical personality engine", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        val difficultyOptions = listOf(
                            Triple(AiDifficulty.EASY, BrandIndigo, "Casual & relaxed — takes open lines, plays for fun."),
                            Triple(AiDifficulty.MEDIUM, BrandAmber, "Tactical & balanced — blocks threats, builds 3-in-a-row traps."),
                            Triple(AiDifficulty.HARD, BrandCoral, "Master strategist — Minimax depth search, center control & endgame forced wins.")
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            difficultyOptions.forEach { (diff, diffColor, description) ->
                                val isSelected = setupState.aiDifficulty == diff

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            soundManager.playUiTap()
                                            onDifficultyChanged(diff)
                                        }
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) diffColor else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .testTag("difficulty_${diff.name.lowercase()}"),
                                    color = if (isSelected) diffColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(diffColor)
                                            )
                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = diff.displayName,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = diffColor.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = when (diff) {
                                                                AiDifficulty.EASY -> "CASUAL"
                                                                AiDifficulty.MEDIUM -> "TACTICAL"
                                                                else -> "HARDCORE"
                                                            },
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = diffColor,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = description,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (isSelected) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = diffColor, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ====================================================
            // 4. TURN RULE SELECTION CARD
            // ====================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrandTeal.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Turn Rule", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                            Text("Victory condition and match scoring format", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    TurnRule.values().forEach { rule ->
                        val isSelected = setupState.turnRule == rule
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    soundManager.playUiTap()
                                    onTurnRuleChanged(rule)
                                }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) BrandIndigo else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            color = if (isSelected) BrandIndigo.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = rule.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = if (rule == TurnRule.STANDARD) "First player to connect 4 in a row wins immediately"
                                        else "Play until board is full! Most 4-in-a-row connections wins!",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandIndigo)
                                }
                            }
                        }
                    }
                }
            }

            // ====================================================
            // 4b. POWER-UP MODE (local only, opt-in)
            // ====================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Power-Up Mode", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        Text("Use jokers you own (Extra Turn, Steal Box). Local play only.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = setupState.powerUpMode,
                        onCheckedChange = {
                            soundManager.playUiTap()
                            onPowerUpModeChanged(it)
                        },
                        modifier = Modifier.testTag("power_up_mode_switch")
                    )
                }
            }

            // ====================================================
            // 5. FIRST PLAYER SELECTION
            // ====================================================
            if (setupState.mode == GameMode.VS_AI || setupState.mode == GameMode.PASS_AND_PLAY) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Who Moves First?", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)

                        if (setupState.mode == GameMode.PASS_AND_PLAY) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val isP1First = !setupState.player2StartsFirst
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            soundManager.playUiTap()
                                            onTogglePlayer2StartsFirst?.invoke(false)
                                        }
                                        .border(
                                            width = if (isP1First) 2.dp else 1.dp,
                                            color = if (isP1First) Color(0xFF2563EB) else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .testTag("select_p1_first"),
                                    color = if (isP1First) Color(0xFF2563EB).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("👤 ${setupState.player1Name}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }

                                val isP2First = setupState.player2StartsFirst
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            soundManager.playUiTap()
                                            onTogglePlayer2StartsFirst?.invoke(true)
                                        }
                                        .border(
                                            width = if (isP2First) 2.dp else 1.dp,
                                            color = if (isP2First) Color(0xFFFF5252) else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .testTag("select_p2_first"),
                                    color = if (isP2First) Color(0xFFFF5252).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("👥 ${setupState.player2Name}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val isHumanFirst = !setupState.firstPlayerIsAi
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            soundManager.playUiTap()
                                            onToggleFirstPlayer(false)
                                        }
                                        .border(
                                            width = if (isHumanFirst) 2.dp else 1.dp,
                                            color = if (isHumanFirst) BrandIndigo else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(14.dp)
                                        ),
                                    color = if (isHumanFirst) BrandIndigo.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("👤 You First", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }

                                val isAiFirst = setupState.firstPlayerIsAi
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            soundManager.playUiTap()
                                            onToggleFirstPlayer(true)
                                        }
                                        .border(
                                            width = if (isAiFirst) 2.dp else 1.dp,
                                            color = if (isAiFirst) BrandCoral else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(14.dp)
                                        ),
                                    color = if (isAiFirst) BrandCoral.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("🤖 AI First", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
