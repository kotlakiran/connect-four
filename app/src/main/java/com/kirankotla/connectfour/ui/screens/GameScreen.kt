package com.kirankotla.connectfour.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.kirankotla.connectfour.model.GameMode
import com.kirankotla.connectfour.model.GameState
import com.kirankotla.connectfour.model.TurnRule
import com.kirankotla.connectfour.rules.MoveInsights
import com.kirankotla.connectfour.ui.theme.*
import com.kirankotla.connectfour.ui.UiTestTags
import com.kirankotla.connectfour.ui.components.AccessibleMovePicker
import com.kirankotla.connectfour.ui.components.CelebratoryParticleOverlay
import com.kirankotla.connectfour.ui.components.ComboChainBanner
import com.kirankotla.connectfour.ui.components.ConnectFourBoard
import com.kirankotla.connectfour.ui.components.MoveHistorySheet
import com.kirankotla.connectfour.ui.components.TurnTimerBar
import com.kirankotla.connectfour.ui.components.VectorAvatar
import com.kirankotla.connectfour.ui.components.getAvatarById
import com.kirankotla.connectfour.util.HapticManager
import com.kirankotla.connectfour.util.SoundManager
import com.kirankotla.connectfour.data.WalletBalance
import com.kirankotla.connectfour.model.reward.BoxReward
import com.kirankotla.connectfour.model.reward.RewardKind
import kotlinx.coroutines.delay

@Composable
fun GameLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepCosmicDark),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF121D36).copy(alpha = 0.96f),
            border = BorderStroke(1.dp, BrandIndigo.copy(alpha = 0.45f)),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    color = CyberCyan,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Preparing your battle...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Setting up the board",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    gameState: GameState?,
    hintEdgeId: String?,
    highContrast: Boolean = false,
    reduceMotion: Boolean = false,
    soundEnabled: Boolean = true,
    onToggleSound: (Boolean) -> Unit = {},
    hapticEnabled: Boolean = true,
    onToggleHaptic: (Boolean) -> Unit = {},
    musicEnabled: Boolean = true,
    onToggleMusic: (Boolean) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    turnTimerEnabled: Boolean = true,
    turnTimerSeconds: Int = 15,
    onUpdateTimerEnabled: ((Boolean) -> Unit)? = null,
    onUpdateTimerSeconds: ((Int) -> Unit)? = null,
    onTurnTimeOut: () -> Unit = {},
    onEdgeTap: (String) -> Unit,
    onDiceRollTap: () -> Unit,
    onEndTurnEarlyTap: () -> Unit,
    onRequestHint: () -> Unit,
    onUndoMove: () -> Unit,
    onRestartGame: () -> Unit,
    onBackToHome: () -> Unit,
    reward: BoxReward? = null,
    rewardEventId: Int = 0,
    rewardBoxId: String? = null,
    rewardHistory: List<BoxReward> = emptyList(),
    walletBalance: WalletBalance = WalletBalance(),
    ownedJokerIds: List<String> = emptyList(),
    onUseJoker: (String) -> Unit = {}
) {
    if (gameState == null) return

    val context = LocalContext.current
    val soundManager = remember { SoundManager(context) }
    val hapticManager = remember { HapticManager(context) }
    LaunchedEffect(soundEnabled) {
        soundManager.isEnabled = soundEnabled
    }
    DisposableEffect(Unit) {
        onDispose { soundManager.release() }
    }

    var isPaused by remember { mutableStateOf(false) }
    var showAccessiblePicker by remember { mutableStateOf(false) }
    var showMoveHistory by remember { mutableStateOf(false) }
    var showDiceRollHint by remember { mutableStateOf(false) }

    var localTimerEnabled by remember(turnTimerEnabled) { mutableStateOf(turnTimerEnabled) }
    var currentTurnDurationSeconds by remember(turnTimerSeconds) { mutableIntStateOf(turnTimerSeconds) }

    var secondsRemaining by remember(
        gameState.currentPlayerIndex,
        gameState.moveHistory.size,
        gameState.diceState?.hasRolledThisTurn,
        gameState.gameId,
        currentTurnDurationSeconds
    ) {
        mutableIntStateOf(currentTurnDurationSeconds)
    }

    // Countdown Timer Loop
    LaunchedEffect(
        gameState.currentPlayerIndex,
        gameState.moveHistory.size,
        gameState.gameId,
        isPaused,
        gameState.isGameOver,
        localTimerEnabled,
        currentTurnDurationSeconds
    ) {
        if (!localTimerEnabled || isPaused || gameState.isGameOver || currentTurnDurationSeconds <= 0 || gameState.currentPlayer.isAi) {
            return@LaunchedEffect
        }

        while (secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining -= 1
            if (secondsRemaining in 1..3) {
                soundManager.playTimerTick()
                if (hapticEnabled) hapticManager.performTimerTick()
            } else if (secondsRemaining == 5) {
                soundManager.playTimeWarning()
            }
        }

        if (secondsRemaining <= 0 && !gameState.isGameOver && !gameState.currentPlayer.isAi) {
            soundManager.playTurnChange()
            onTurnTimeOut()
        }
    }

    var lastMoveCount by remember(gameState.gameId) { mutableIntStateOf(gameState.moveHistory.size) }
    LaunchedEffect(gameState.moveHistory.size) {
        val count = gameState.moveHistory.size
        if (count > lastMoveCount) {
            val combo = MoveInsights.trailingChainCount(gameState.moveHistory)
            if (combo >= 2) {
                soundManager.playChainCombo(combo)
                if (hapticEnabled) hapticManager.performChainCombo(combo)
            }
        }
        lastMoveCount = count
    }

    var showVictoryOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(gameState.isGameOver) {
        if (gameState.isGameOver) {
            // Wait 2 seconds so player can clearly see the winning connect-four line and final chip
            delay(2000L)
            showVictoryOverlay = true
            soundManager.playGameWin()
        } else {
            showVictoryOverlay = false
        }
    }

    LaunchedEffect(showDiceRollHint) {
        if (showDiceRollHint) {
            delay(1400L)
            showDiceRollHint = false
        }
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
                                text = "${gameState.rows}x${gameState.cols}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = if (gameState.mode == GameMode.PASS_AND_PLAY) "Pass & Play Battle" else gameState.mode.displayName,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        soundManager.playUiTap()
                        isPaused = true
                    }) {
                        Icon(Icons.Default.PauseCircle, contentDescription = "Pause", modifier = Modifier.size(26.dp))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            soundManager.playUiTap()
                            onOpenSettings()
                        },
                        modifier = Modifier.testTag(UiTestTags.GAME_SETTINGS_BUTTON)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Surface(
                        color = Color(0xFFFFC107).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "🪙 ${walletBalance.coins}",
                            color = Color(0xFFFFC107),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            soundManager.playUiTap()
                            showMoveHistory = true
                        },
                        modifier = Modifier.testTag("move_history_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (gameState.moveHistory.isNotEmpty()) {
                                    Badge(containerColor = BrandIndigo) {
                                        Text(
                                            minOf(gameState.moveHistory.size, 99).toString(),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.History, contentDescription = "Move History")
                        }
                    }
                    IconButton(onClick = {
                        soundManager.playUiTap()
                        showAccessiblePicker = !showAccessiblePicker
                    }) {
                        Icon(
                            Icons.Default.AccessibilityNew,
                            contentDescription = "Accessibility Picker",
                            tint = if (showAccessiblePicker) BrandIndigo else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ====================================================
            // 1. STADIUM BATTLE HUD: PLAYER 1 vs PLAYER 2 / AI
            // ====================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Player 1 Card (Left)
                        val p1 = gameState.players.getOrNull(0)
                        val isP1Active = gameState.currentPlayerIndex == 0 && !gameState.isGameOver
                        val p1Color = runCatching { Color(android.graphics.Color.parseColor(p1?.colorHex.orEmpty())) }
                            .getOrDefault(BrandCoral)

                        val p1Chips = gameState.boardCells.values.count { it == p1?.id }
                        val p1Lines = gameState.playerLinesCount[p1?.id] ?: 0
                        val isMaxLines = gameState.turnRule == TurnRule.MAX_LINES

                        StadiumPlayerCard(
                            player = p1,
                            isActive = isP1Active,
                            playerColor = p1Color,
                            isLeft = true,
                            chipsCount = p1Chips,
                            linesCount = p1Lines,
                            isMaxLines = isMaxLines,
                            modifier = Modifier.weight(1f)
                        )

                        // Center "VS" Emblem
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(BrandIndigo, BrandPurple)
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "VS",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Player 2 / AI Card (Right)
                        val p2 = gameState.players.getOrNull(1)
                        val isP2Active = gameState.currentPlayerIndex == 1 && !gameState.isGameOver
                        val p2Color = runCatching { Color(android.graphics.Color.parseColor(p2?.colorHex.orEmpty())) }
                            .getOrDefault(BrandCyan)
                        val p2Chips = gameState.boardCells.values.count { it == p2?.id }
                        val p2Lines = gameState.playerLinesCount[p2?.id] ?: 0

                        StadiumPlayerCard(
                            player = p2,
                            isActive = isP2Active,
                            playerColor = p2Color,
                            isLeft = false,
                            chipsCount = p2Chips,
                            linesCount = p2Lines,
                            isMaxLines = isMaxLines,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ====================================================
            // 1b. POWER-UP JOKER TRAY (only when Power-Up Mode is on)
            // ====================================================
            if (gameState.powerUpState != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("joker_tray"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tray = ownedJokerIds.distinct().mapNotNull { com.kirankotla.connectfour.model.reward.JokerCatalog.byId(it) }
                        .filter { it.id in com.kirankotla.connectfour.rules.PowerUpEngine.PLAYABLE_JOKER_IDS }
                    if (tray.isEmpty()) {
                        Text("No jokers yet - win matches to find some", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    tray.forEach { joker ->
                        AssistChip(
                            onClick = { onUseJoker(joker.id) },
                            enabled = !gameState.currentPlayer.isAi &&
                                com.kirankotla.connectfour.rules.PowerUpEngine.canUse(gameState, joker.id),
                            label = { Text(joker.title, fontSize = 12.sp) },
                            modifier = Modifier.testTag("joker_${joker.id}")
                        )
                    }
                }
            }

            // ====================================================
            // 2. TURN COUNTDOWN TIMER HUD
            // ====================================================
            if (localTimerEnabled && !gameState.isGameOver) {
                TurnTimerBar(
                    activePlayer = gameState.currentPlayer,
                    secondsRemaining = secondsRemaining,
                    totalSeconds = currentTurnDurationSeconds,
                    isTimerEnabled = localTimerEnabled,
                    onSelectDuration = { newDuration ->
                        if (newDuration == 0) {
                            localTimerEnabled = false
                            onUpdateTimerEnabled?.invoke(false)
                        } else {
                            localTimerEnabled = true
                            onUpdateTimerEnabled?.invoke(true)
                            onUpdateTimerSeconds?.invoke(newDuration)
                            currentTurnDurationSeconds = newDuration
                            secondsRemaining = newDuration
                        }
                    },
                    highContrast = highContrast,
                    reduceMotion = reduceMotion
                )
            }

            // ====================================================
            // 3. SPECIAL MODES ARENA STATUS (DICE / PASS & PLAY)
            // ====================================================
            // Pass & Play Device Handover Stadium Banner
            if (gameState.mode == GameMode.PASS_AND_PLAY && !gameState.isGameOver) {
                val activeP = gameState.currentPlayer
                val isP1 = gameState.currentPlayerIndex == 0
                val activeColor = runCatching { Color(android.graphics.Color.parseColor(activeP.colorHex)) }
                    .getOrDefault(if (isP1) BrandCoral else BrandCyan)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .testTag("pass_and_play_turn_banner"),
                    color = activeColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.5.dp, activeColor.copy(alpha = 0.5f)),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VectorAvatar(
                                avatar = getAvatarById(activeP.mascotId),
                                size = 36.dp,
                                showRing = true,
                                ringColor = activeColor
                            )
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${activeP.name}'s Turn",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = activeColor
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = activeColor
                                    ) {
                                        Text(
                                            text = if (isP1) "PLAYER 1" else "PLAYER 2",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "📱 Pass device to ${activeP.name} to drop chip",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Dice Mode Turn Status Card
            if (gameState.mode == GameMode.DICE_MODE) {
                val dice = gameState.diceState
                Surface(
                    color = if (showDiceRollHint) {
                        BrandPurple.copy(alpha = 0.24f)
                    } else {
                        BrandPurple.copy(alpha = 0.12f)
                    },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        if (showDiceRollHint) 2.dp else 1.dp,
                        BrandPurple.copy(alpha = if (showDiceRollHint) 0.9f else 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (dice?.hasRolledThisTurn == true) "Chips Allowance: ${dice.remainingAllowance}" else "Roll Dice to Begin Turn",
                                fontWeight = FontWeight.Black,
                                color = BrandPurple,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (dice?.hasRolledThisTurn == true) "Drop up to ${dice.remainingAllowance} chips this turn" else "Tap roll to determine allowed moves",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (dice?.hasRolledThisTurn == false && !gameState.currentPlayer.isAi) {
                            Button(
                                onClick = {
                                    soundManager.playDiceRoll()
                                    if (hapticEnabled) hapticManager.performDiceRoll()
                                    onDiceRollTap()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = if (showDiceRollHint) {
                                    Modifier.border(
                                        2.dp,
                                        BrandAmber,
                                        RoundedCornerShape(12.dp)
                                    )
                                } else {
                                    Modifier
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                            ) {
                                Text("ROLL 🎲", fontWeight = FontWeight.Black)
                            }
                        } else if (dice?.hasRolledThisTurn == true && !gameState.currentPlayer.isAi) {
                            OutlinedButton(
                                onClick = {
                                    soundManager.playUiTap()
                                    if (hapticEnabled) hapticManager.performUiTap()
                                    onEndTurnEarlyTap()
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("End Turn", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ====================================================
            // 4. MAIN BATTLE BOARD STADIUM FRAME
            // ====================================================
            val activeColor = runCatching { Color(android.graphics.Color.parseColor(gameState.currentPlayer.colorHex)) }
                .getOrDefault(if (gameState.currentPlayerIndex == 0) BrandCoral else BrandCyan)

            val comboCount = remember(gameState.moveHistory) {
                MoveInsights.trailingChainCount(gameState.moveHistory)
            }
            ComboChainBanner(
                comboCount = comboCount,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(26.dp),
                color = DeepCosmicDark,
                shadowElevation = 12.dp,
                border = BorderStroke(1.5.dp, activeColor.copy(alpha = 0.65f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ConnectFourBoard(
                        gameState = gameState,
                        themeColor = activeColor,
                        highContrast = highContrast,
                        reduceMotion = reduceMotion,
                        hintEdgeId = hintEdgeId,
                        reward = reward,
                        rewardEventId = rewardEventId,
                        rewardBoxId = rewardBoxId,
                        onEdgeTap = { edgeId ->
                            if (
                                gameState.mode == GameMode.DICE_MODE &&
                                gameState.diceState?.hasRolledThisTurn != true &&
                                !gameState.currentPlayer.isAi
                            ) {
                                showDiceRollHint = true
                                soundManager.playUiTap()
                            } else {
                                onEdgeTap(edgeId)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Accessible Move Picker Panel (if activated)
            if (showAccessiblePicker) {
                AccessibleMovePicker(
                    gameState = gameState,
                    onConfirmMove = onEdgeTap
                )
            }

            if (rewardHistory.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF101C35),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = "RECENT REWARDS",
                            color = Color(0xFF67E8F9),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rewardHistory.takeLast(6).forEach { historyReward ->
                                val icon = historyReward.type?.icon ?: when (historyReward.kind) {
                                    RewardKind.COIN -> "🪙"
                                    RewardKind.GEM -> "💎"
                                    RewardKind.JOKER -> "🃏"
                                    RewardKind.COLLECTIBLE -> "🧩"
                                    else -> "✨"
                                }
                                Text(icon, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }

            // ====================================================
            // 5. WORLD-CLASS FLOATING ACTION DOCK
            // ====================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // UNDO BUTTON
                    Surface(
                        onClick = {
                            soundManager.playUiTap()
                            onUndoMove()
                        },
                        enabled = gameState.moveHistory.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (gameState.moveHistory.isNotEmpty()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                modifier = Modifier.size(18.dp),
                                tint = if (gameState.moveHistory.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Undo",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (gameState.moveHistory.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }

                    // HINT BUTTON
                    Surface(
                        onClick = {
                            soundManager.playUiTap()
                            onRequestHint()
                        },
                        enabled = !gameState.isGameOver,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = BrandAmber.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, BrandAmber.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Lightbulb, contentDescription = "Hint", tint = BrandAmber, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hint", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = BrandAmber)
                        }
                    }

                    // MOVES HISTORY BUTTON
                    Surface(
                        onClick = {
                            soundManager.playUiTap()
                            showMoveHistory = true
                        },
                        modifier = Modifier.weight(1.2f).height(46.dp).testTag("bottom_move_history_button"),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.History, contentDescription = "History", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Moves (${gameState.moveHistory.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // RESTART BUTTON
                    Surface(
                        onClick = {
                            soundManager.playUiTap()
                            onRestartGame()
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Restart", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restart", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Slide-up Sheet showing Last 10 Moves
    if (showMoveHistory) {
        MoveHistorySheet(
            gameState = gameState,
            onDismissRequest = { showMoveHistory = false },
            highContrast = highContrast
        )
    }

    // Pause Dialog
    if (isPaused) {
        ModalBottomSheet(
            onDismissRequest = { isPaused = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .shadow(6.dp, RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFFC857), Color(0xFFE18B00))
                                )
                            )
                            .border(1.dp, Color(0xFFFFE7A3), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            repeat(2) {
                                Box(
                                    modifier = Modifier
                                        .width(5.dp)
                                        .height(19.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFF332000))
                                        .border(1.dp, Color(0xFFFFE7A3), RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                    Text("Battle Paused", fontWeight = FontWeight.Black)
                }
                Text("Match is on hold. Adjust sounds or resume when ready.", style = MaterialTheme.typography.bodyMedium)
                listOf(
                    Triple(Icons.Default.VolumeUp, "Sound FX", soundEnabled),
                    Triple(Icons.Default.Vibration, "Haptic Feedback", hapticEnabled),
                    Triple(Icons.Default.MusicNote, "Background Music", musicEnabled)
                ).forEach { (icon, label, enabled) ->
                    val testTag = when (label) {
                        "Sound FX" -> UiTestTags.PAUSE_SOUND_TOGGLE
                        "Haptic Feedback" -> UiTestTags.PAUSE_HAPTIC_TOGGLE
                        else -> UiTestTags.PAUSE_MUSIC_TOGGLE
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(label, fontWeight = FontWeight.Bold)
                            }
                            Switch(
                                checked = enabled,
                                modifier = Modifier.testTag(testTag),
                                onCheckedChange = {
                                    if (label == "Sound FX") {
                                        onToggleSound(it)
                                        if (it) soundManager.playUiTap()
                                    } else if (label == "Haptic Feedback") {
                                        onToggleHaptic(it)
                                    } else {
                                        onToggleMusic(it)
                                    }
                                }
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { soundManager.playUiTap(); onBackToHome() }, modifier = Modifier.weight(1f)) {
                        Text("Quit to Arena", fontWeight = FontWeight.SemiBold)
                    }
                    Button(onClick = { soundManager.playUiTap(); isPaused = false }, modifier = Modifier.weight(1f)) {
                        Text("Resume Battle", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Celebratory Particle System & Screen-filling Victory Overlay
    if (gameState.isGameOver && showVictoryOverlay) {
        val stats = remember(gameState) { MoveInsights.matchStats(gameState) }
        CelebratoryParticleOverlay(
            winnerPlayer = gameState.players.find { it.id == gameState.winnerId },
            isDraw = gameState.isDraw,
            finalScores = gameState.players,
            onPlayAgain = onRestartGame,
            onBackToHome = onBackToHome,
            reduceMotion = reduceMotion,
            stats = stats
        )
    }
}

@Composable
private fun StadiumPlayerCard(
    player: com.kirankotla.connectfour.model.Player?,
    isActive: Boolean,
    playerColor: Color,
    isLeft: Boolean,
    modifier: Modifier = Modifier,
    chipsCount: Int = 0,
    linesCount: Int = 0,
    isMaxLines: Boolean = false
) {
    if (player == null) return

    val displayBadgeText = when {
        isMaxLines -> "$linesCount ⚡"
        player.score >= 100 -> "🏆"
        else -> "$chipsCount 🎯"
    }

    Surface(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) playerColor.copy(alpha = 0.16f) else Color(0xFF131B2E),
        border = BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = if (isActive) playerColor else Color(0xFF1E2D4A)
        ),
        shadowElevation = if (isActive) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLeft) {
                // Left Player: Avatar on Left
                VectorAvatar(
                    avatar = getAvatarById(player.mascotId),
                    size = 38.dp,
                    showRing = isActive,
                    ringColor = playerColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = player.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            color = playerColor,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = displayBadgeText,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        if (isActive) {
                            Surface(
                                color = playerColor.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, playerColor.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "● TURN",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = playerColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Right Player: Info on Left, Avatar on Right
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = player.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isActive) {
                            Surface(
                                color = playerColor.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, playerColor.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "● TURN",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = playerColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Surface(
                            color = playerColor,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = displayBadgeText,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                VectorAvatar(
                    avatar = getAvatarById(player.mascotId),
                    size = 38.dp,
                    showRing = isActive,
                    ringColor = playerColor
                )
            }
        }
    }
}

