package com.kirankotla.connectfour.multiplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.kirankotla.connectfour.model.GameState
import com.kirankotla.connectfour.model.Player
import com.kirankotla.connectfour.model.reward.BoxReward
import com.kirankotla.connectfour.model.reward.RewardContext
import com.kirankotla.connectfour.model.reward.RewardKind
import com.kirankotla.connectfour.model.reward.RewardTable
import com.kirankotla.connectfour.multiplayer.model.DEFAULT_TURN_TIME_SECONDS
import com.kirankotla.connectfour.multiplayer.model.OnlineGameDoc
import com.kirankotla.connectfour.multiplayer.model.OnlineGameStatus
import com.kirankotla.connectfour.multiplayer.model.OnlinePlayerDoc
import com.kirankotla.connectfour.multiplayer.model.Reaction
import com.kirankotla.connectfour.multiplayer.viewmodel.MultiplayerViewModel
import com.kirankotla.connectfour.rules.MoveInsights
import com.kirankotla.connectfour.ui.components.ComboChainBanner
import com.kirankotla.connectfour.ui.components.ConnectFourBoard
import com.kirankotla.connectfour.ui.components.MiniBoardDiagram
import com.kirankotla.connectfour.ui.components.VectorAvatar
import com.kirankotla.connectfour.ui.components.getAvatarById
import com.kirankotla.connectfour.ui.theme.*
import com.kirankotla.connectfour.ui.UiTestTags
import com.kirankotla.connectfour.util.SoundManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineGameScreen(
    multiplayerViewModel: MultiplayerViewModel,
    onOpenLeaderboard: () -> Unit,
    soundEnabled: Boolean,
    onToggleSound: (Boolean) -> Unit,
    hapticEnabled: Boolean,
    onToggleHaptic: (Boolean) -> Unit,
    musicEnabled: Boolean,
    onToggleMusic: (Boolean) -> Unit,
    onLeaveToHome: () -> Unit
) {
    val session by multiplayerViewModel.session.collectAsState()
    val players by multiplayerViewModel.players.collectAsState()
    val isOnline by multiplayerViewModel.isOnline.collectAsState()
    val currentReaction by multiplayerViewModel.currentReaction.collectAsState()
    val isChatMuted by multiplayerViewModel.isChatMuted.collectAsState()
    val isSimulatedMatch by multiplayerViewModel.isSimulatedMatch.collectAsState()
    val isResigning by multiplayerViewModel.isResigning.collectAsState()
    val myUid = multiplayerViewModel.myUid

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val soundManager = remember { SoundManager(context) }
    var isLoadingGame by remember { mutableStateOf(true) }
    var isPaused by remember { mutableStateOf(false) }
    soundManager.isEnabled = soundEnabled

    LaunchedEffect(session?.gameId) {
        isLoadingGame = true
        delay(1200L)
        isLoadingGame = false
    }

    // Audio feedback on edge claims, box captures, and victory
    var lastEdgesCount by remember { mutableIntStateOf(session?.claimedEdges?.size ?: 0) }
    var lastBoxesCount by remember { mutableIntStateOf(session?.boxOwners?.size ?: 0) }
    var onlineReward by remember { mutableStateOf<BoxReward?>(null) }
    var onlineRewardEventId by remember { mutableIntStateOf(0) }
    var onlineRewardBoxId by remember { mutableStateOf<String?>(null) }
    var onlineRewardHistory by remember { mutableStateOf<List<Pair<BoxReward, String>>>(emptyList()) }
    var knownBoxIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(session?.gameId) {
        knownBoxIds = session?.boxOwners?.keys ?: emptySet()
    }

    LaunchedEffect(session?.claimedEdges?.size) {
        val current = session?.claimedEdges?.size ?: 0
        if (current > lastEdgesCount) {
            soundManager.playLineStroke()
        }
        lastEdgesCount = current
    }

    LaunchedEffect(session?.boxOwners?.size) {
        val current = session?.boxOwners?.size ?: 0
        if (current > lastBoxesCount) {
            soundManager.playGridCapture()
        }
        lastBoxesCount = current
    }

    LaunchedEffect(session?.boxOwners) {
        val currentBoxes = session?.boxOwners?.keys ?: emptySet()
        val newlyCompleted = currentBoxes - knownBoxIds
        if (newlyCompleted.isNotEmpty()) {
            val rewardContext = if (newlyCompleted.size >= 2) RewardContext.DOUBLE_CAPTURE else RewardContext.NORMAL_BOX
            val seed = (session?.gameId?.hashCode()?.toLong() ?: 0L) xor (session?.version ?: 0L)
            onlineReward = RewardTable.roll(seed, rewardContext)
            val rewardBox = newlyCompleted.last()
            onlineRewardBoxId = rewardBox
            val rewardOwner = session?.boxOwners?.get(rewardBox) ?: myUid
            onlineRewardHistory = (onlineRewardHistory + (onlineReward!! to rewardOwner)).takeLast(8)
            onlineRewardEventId++
            soundManager.playRewardSound(onlineReward?.type?.soundProfile)
        }
        knownBoxIds = currentBoxes
    }

    LaunchedEffect(session?.status) {
        if (session?.status == OnlineGameStatus.COMPLETED) {
            if (session?.winnerId == myUid) {
                soundManager.playGameWin()
            }
        }
    }

    LaunchedEffect(session?.rematchStatus) {
        if (session?.rematchStatus == "DECLINED") {
            multiplayerViewModel.leaveSession()
            onLeaveToHome()
        }
    }

    val me = players.find { it.uid == myUid } ?: OnlinePlayerDoc(uid = myUid, displayName = "You", colorHex = "#FF5252")
    val opponent = players.find { it.uid != myUid } ?: OnlinePlayerDoc(uid = "opponent", displayName = "Opponent", colorHex = "#06B6D4")

    // Ensure players always have distinct, vibrant colors
    val p1ColorHex = me.colorHex.ifBlank { "#FF5252" }
    val p2ColorHex = if (
        opponent.colorHex.isBlank() ||
        opponent.colorHex.equals(p1ColorHex, ignoreCase = true)
    ) {
        if (p1ColorHex.equals("#06B6D4", ignoreCase = true) || p1ColorHex.equals("#22B8A7", ignoreCase = true)) {
            "#FF5252"
        } else {
            "#06B6D4"
        }
    } else {
        opponent.colorHex
    }

    val p1Color = remember(p1ColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(p1ColorHex)) }.getOrDefault(BrandCoral)
    }
    val p2Color = remember(p2ColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(p2ColorHex)) }.getOrDefault(BrandCyan)
    }

    val isMyTurn = session?.currentTurn == myUid
    val isFinished = session?.isFinished == true
    var showResignConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(session?.status) {
        if (session?.isFinished == true) {
            showResignConfirm = false
        }
    }

    BackHandler {
        if (!isFinished) {
            showResignConfirm = true
        } else {
            multiplayerViewModel.leaveSession()
            onLeaveToHome()
        }
    }

    // Map OnlineGameDoc to local GameState format for ConnectFourBoard rendering
    val currentDoc = session
    val dummyLocalState = remember(currentDoc, players, p1ColorHex, p2ColorHex) {
        val p1 = Player(
            id = 1,
            name = me.displayName,
            colorHex = p1ColorHex,
            mascotId = me.avatarId,
            score = me.score
        )
        val p2 = Player(
            id = 2,
            name = opponent.displayName,
            colorHex = p2ColorHex,
            mascotId = opponent.avatarId,
            score = opponent.score
        )
        val activeIndex = if (isMyTurn) 0 else 1
        val boxOwnersInt = currentDoc?.boxOwners?.mapValues { (_, ownerUid) ->
            if (ownerUid == myUid) 1 else 2
        } ?: emptyMap()

        val historyItems = MoveInsights.rebuildHistory(
            rows = currentDoc?.rows ?: 3,
            cols = currentDoc?.cols ?: 3,
            orderedEdges = currentDoc?.claimedEdges ?: emptyList()
        ) { index, edgeId ->
            val ownerUid = currentDoc?.edgeOwners?.get(edgeId)
            when (ownerUid) {
                myUid -> 1
                opponent.uid -> 2
                null -> if (index % 2 == 0) 1 else 2
                else -> 2
            }
        }

        val lastMoveCol = currentDoc?.claimedEdges?.lastOrNull()?.removePrefix("col_")?.toIntOrNull()
        val lastRow = if (lastMoveCol != null && currentDoc != null) {
            val count = currentDoc.claimedEdges.count { it == "col_$lastMoveCol" }
            (currentDoc.rows - count)
        } else null
        val lastDropPair = if (lastRow != null && lastMoveCol != null) Pair(lastRow, lastMoveCol) else null

        val resolvedWinnerId = when {
            session?.isDraw == true -> null
            session?.winnerId == myUid -> 1
            session?.winnerId == opponent.uid -> 2
            else -> null
        }
        val isMatchDraw = session?.isDraw == true || (session?.isFinished == true && (session?.winnerId.isNullOrBlank() || session?.winnerId == "draw"))

        GameState(
            gameId = currentDoc?.gameId ?: "online_game",
            rows = currentDoc?.rows ?: 6,
            cols = currentDoc?.cols ?: 7,
            mode = com.kirankotla.connectfour.model.GameMode.QUICK_PLAY,
            players = listOf(p1, p2),
            currentPlayerIndex = activeIndex,
            boardCells = boxOwnersInt,
            claimedEdges = currentDoc?.claimedEdges?.toSet() ?: emptySet(),
            boxOwners = boxOwnersInt,
            moveHistory = historyItems,
            lastDrop = lastDropPair,
            isGameOver = isFinished,
            winnerId = resolvedWinnerId,
            isDraw = isMatchDraw
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
                        Text("Online Match", fontWeight = FontWeight.Black)
                        val gridRows = currentDoc?.rows ?: 3
                        val gridCols = currentDoc?.cols ?: 3
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${gridRows}×${gridCols}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (!isOnline) {
                            Surface(
                                color = Color(0xFFF59E0B),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "RECONNECTING",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            soundManager.playUiTap()
                            isPaused = true
                        },
                        modifier = Modifier.testTag(UiTestTags.ONLINE_GAME_SETTINGS_BUTTON)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(
                        onClick = { showResignConfirm = true },
                        modifier = Modifier.testTag(UiTestTags.ONLINE_GAME_RESIGN_BUTTON)
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = "Resign", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Disconnect Forfeit Countdown Banner
            val disconnectSec by multiplayerViewModel.disconnectCountdown.collectAsState()
            AnimatedVisibility(visible = disconnectSec != null && disconnectSec!! > 0) {
                Surface(
                    color = Color(0xFFEF4444).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFDC2626)
                            )
                            Column {
                                Text(
                                    text = "⚠️ Opponent Disconnected",
                                    fontSize = 12.sp,
                                    color = Color(0xFFDC2626),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Forfeiting match in ${disconnectSec}s",
                                    fontSize = 10.sp,
                                    color = Color(0xFFB91C1C),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Button(
                            onClick = { multiplayerViewModel.claimAbandonmentWin() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Claim Win", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Reconnecting Warning Banner (Local Network Lost)
            AnimatedVisibility(visible = !isOnline && disconnectSec == null) {
                Surface(
                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFB45309)
                        )
                        Text(
                            text = "Connection lost. Reconnecting to session...",
                            fontSize = 11.sp,
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Top Status Bar: You vs Opponent & Scores (Compact and bounds-safe)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // You (Left Player)
                    Box(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(p1Color, BrandAmber))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = me.displayName.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                            }
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = me.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val myChipsCount = session?.boxOwners?.values?.count { it == me.uid } ?: 0
                                Text(
                                    text = if (me.score > 0) "🏆 ${me.score} pts" else "$myChipsCount chips",
                                    color = p1Color,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (currentReaction != null && currentReaction!!.fromUid == me.uid) {
                            ReactionBubble(
                                reaction = currentReaction!!.reaction,
                                modifier = Modifier.align(Alignment.TopStart)
                            )
                        }
                    }

                    // Turn Status Pill with countdown
                    val turnTimeRemaining by multiplayerViewModel.turnTimeRemaining.collectAsState()
                    val turnProgress = (turnTimeRemaining / (session?.turnTimeSeconds ?: DEFAULT_TURN_TIME_SECONDS).toFloat()).coerceIn(0f, 1f)
                    val turnColor = if (isMyTurn) p1Color else p2Color

                    Surface(
                        color = turnColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { turnProgress },
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = if (turnTimeRemaining <= 4) Color(0xFFDC2626) else turnColor,
                                    trackColor = turnColor.copy(alpha = 0.2f)
                                )
                                Text(
                                    text = "$turnTimeRemaining",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (turnTimeRemaining <= 4) Color(0xFFDC2626) else turnColor
                                )
                            }
                            Text(
                                text = if (isMyTurn) "YOUR TURN" else "THEIR TURN",
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                color = turnColor,
                                maxLines = 1
                            )
                        }
                    }

                    // Opponent (Right Player)
                    Box(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Column(
                                modifier = Modifier.weight(1f, fill = false),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = opponent.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val oppChipsCount = session?.boxOwners?.values?.count { it == opponent.uid } ?: 0
                                Text(
                                    text = if (opponent.score > 0) "🏆 ${opponent.score} pts" else "$oppChipsCount chips",
                                    color = p2Color,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(p2Color, BrandTeal))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opponent.displayName.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        if (currentReaction != null && currentReaction!!.fromUid != me.uid) {
                            ReactionBubble(
                                reaction = currentReaction!!.reaction,
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                        }
                    }
                }
            }

            // Combo Chain Banner (Positioned cleanly above the board, NOT on the board or blocking taps)
            val comboCount = remember(dummyLocalState.moveHistory) {
                MoveInsights.trailingChainCount(dummyLocalState.moveHistory)
            }
            if (comboCount > 0) {
                ComboChainBanner(
                    comboCount = comboCount,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            // Game Board - BoxWithConstraints dynamically clamps board size to never overflow!
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                ConnectFourBoard(
                    gameState = dummyLocalState,
                    themeColor = if (isMyTurn) p1Color else p2Color,
                    warnOnDangerousMoves = true,
                    reward = onlineReward,
                    rewardEventId = onlineRewardEventId,
                    rewardBoxId = onlineRewardBoxId,
                    onEdgeTap = { edgeId ->
                        if (isMyTurn && !isFinished) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            multiplayerViewModel.submitMove(edgeId)
                        }
                    },
                    modifier = Modifier.fillMaxSize().clipToBounds()
                )
            }

            // Quick Emote Reactions Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { multiplayerViewModel.toggleChatMute() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isChatMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isChatMuted) "Unmute Reactions" else "Mute Reactions",
                        tint = if (isChatMuted) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(Reaction.values()) { reaction ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics {
                                    contentDescription = "Send ${reaction.name.lowercase()} reaction"
                                },
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                multiplayerViewModel.sendReaction(reaction)
                            }
                        ) {
                            Text(
                                text = reaction.emoji,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

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
                        val yourRewards = onlineRewardHistory.filter { it.second == myUid }.map { it.first }
                        val opponentRewards = onlineRewardHistory.filter { it.second != myUid }.map { it.first }
                        if (onlineRewardHistory.isEmpty()) {
                            Text("Box rewards will appear here", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        } else {
                            RewardRecordRow("YOUR REWARDS", yourRewards)
                            RewardRecordRow("${opponent.displayName.uppercase()} REWARDS", opponentRewards)
                        }
                    }
            }

            // Bottom Turn Status Banner
            Surface(
                color = (if (isMyTurn) p1Color else p2Color).copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMyTurn) "👉 Your turn! Tap any column to drop a chip" else "⏳ ${opponent.displayName} is thinking...",
                        fontSize = 12.sp,
                        fontWeight = if (isMyTurn) FontWeight.Bold else FontWeight.Medium,
                        color = if (isMyTurn) p1Color else p2Color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${currentDoc?.claimedEdges?.size ?: 0} / ${currentDoc?.totalSlots ?: 20} chips",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        if (isLoadingGame) {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF1B2452), Color(0xFF05070F)),
                                radius = 1500f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Text(
                            text = "MATCH READY",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            LoadingPlayerBadge(me.displayName, me.avatarId, p1Color)
                            Text("VS", color = CyberCyan, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            LoadingPlayerBadge(opponent.displayName, opponent.avatarId, p2Color)
                        }
                        Surface(
                            color = Color(0xFF101C35).copy(alpha = 0.85f),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.55f))
                        ) {
                            MiniBoardDiagram(
                                rows = currentDoc?.rows ?: 3,
                                cols = currentDoc?.cols ?: 3,
                                accentColor = CyberCyan,
                                isHighlighted = true,
                                modifier = Modifier.padding(18.dp).size(150.dp)
                            )
                        }
                        CircularProgressIndicator(color = CyberCyan, strokeWidth = 3.dp)
                        Text("Preparing your board...", color = Color(0xFFB8C7E6), fontSize = 14.sp)
                    }
                }
            }
        }

        // Resign Confirmation Dialog
        if (showResignConfirm) {
            AlertDialog(
                onDismissRequest = { if (!isResigning) showResignConfirm = false },
                title = { Text("Resign Match?", fontWeight = FontWeight.Bold) },
                text = { Text("Surrendering will award victory to your opponent. Are you sure?") },
                confirmButton = {
                    Button(
                        enabled = !isResigning,
                        onClick = {
                            multiplayerViewModel.resign()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        if (isResigning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Resigning...")
                        } else {
                            Text("Resign")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isResigning,
                        onClick = { showResignConfirm = false }
                    ) {
                        Text("Continue Playing")
                    }
                }
            )
        }

        // Game Over Dialog
        if (isFinished) {
            val isDraw = session?.isDraw == true || (session?.isFinished == true && (session?.winnerId.isNullOrBlank() || session?.winnerId == "draw"))
            val isWinner = !isDraw && session?.winnerId == myUid
            val isDefeat = !isDraw && !session?.winnerId.isNullOrBlank() && session?.winnerId != myUid
            val winnerName = when {
                isDraw -> null
                isWinner -> me.displayName
                else -> opponent.displayName
            }
            val loserName = when {
                isDraw -> null
                isWinner -> opponent.displayName
                else -> me.displayName
            }
            val rematchPending = session?.rematchStatus == "PENDING"
            val rematchRequestedByMe = session?.rematchRequesterId == myUid
            val rematchDeclined = session?.rematchStatus == "DECLINED"
            val resultTransition = rememberInfiniteTransition(label = "resultModels")
            val winnerScale by resultTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "winnerScale"
            )
            AlertDialog(
                onDismissRequest = { /* forced action */ },
                shape = RoundedCornerShape(24.dp),
                title = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when {
                                isWinner -> "🏆 VICTORY!"
                                isDraw -> "🤝 DRAW MATCH"
                                else -> "❌ DEFEAT"
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            color = when {
                                isWinner -> BrandAmber
                                isDraw -> BrandIndigo
                                else -> Color(0xFFEF4444)
                            }
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = when {
                                isDraw -> "Match Drawn • Perfectly Tied"
                                isWinner -> "👑 $winnerName won the match!"
                                else -> "👑 $winnerName defeated $loserName"
                            },
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = if (isDraw) BrandIndigo else BrandAmber
                        )
                        if (!isSimulatedMatch && rematchPending) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (rematchRequestedByMe) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.5.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(9.dp))
                                        Text(
                                            text = "Rematch request sent\nWaiting for opponent...",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    } else {
                                        Text(
                                            text = "Your opponent wants a rematch.",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        } else if (!isSimulatedMatch && rematchDeclined) {
                            Text(
                                text = "Rematch declined. Returning to Home.",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val playerCards = listOf(
                                me to (if (isDraw) "DRAW 🤝" else if (isWinner) "WINNER 🏆" else "RUNNER-UP 🥈"),
                                opponent to (if (isDraw) "DRAW 🤝" else if (!isWinner) "WINNER 🏆" else "RUNNER-UP 🥈")
                            )
                            playerCards.forEach { (player, badge) ->
                                val isCardWinner = badge.contains("WINNER")
                                Surface(
                                    color = if (isCardWinner) {
                                        BrandAmber.copy(alpha = 0.16f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    },
                                    border = if (isCardWinner) BorderStroke(1.5.dp, BrandAmber) else null,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(62.dp)
                                                .scale(if (isCardWinner) winnerScale else 1f)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.radialGradient(
                                                        listOf(
                                                            (if (isCardWinner) BrandAmber else p1Color).copy(alpha = 0.42f),
                                                            Color.Transparent
                                                        )
                                                    )
                                                )
                                                .border(
                                                    2.dp,
                                                    if (isCardWinner) BrandAmber else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            VectorAvatar(
                                                avatar = getAvatarById(player.avatarId),
                                                size = 52.dp,
                                                ringColor = if (isCardWinner) BrandAmber else p2Color
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = badge,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isCardWinner) BrandAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = player.displayName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isDraw) "🤝 Tied • 0 pts" else if (isCardWinner) "🏆 100 pts" else "0 pts",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isCardWinner) BrandAmber else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Final score",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val stats = remember(dummyLocalState) { MoveInsights.matchStats(dummyLocalState) }
                        val p1Stats = stats[1]
                        val p2Stats = stats[2]
                        if (p1Stats != null && p2Stats != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Match Review",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("🎯 Chips Dropped", fontSize = 11.sp)
                                        Text("${p1Stats.chipsPlaced} vs ${p2Stats.chipsPlaced}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("⚡ Moves Played", fontSize = 11.sp)
                                        Text("${p1Stats.turnsTaken} vs ${p2Stats.turnsTaken}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("🔥 Max Connected", fontSize = 11.sp)
                                        Text("${p1Stats.longestStreak} in a row vs ${p2Stats.longestStreak} in a row", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                    }
                },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isSimulatedMatch && rematchPending && !rematchRequestedByMe) {
                            Button(
                                onClick = { multiplayerViewModel.respondRematch(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Accept", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { multiplayerViewModel.respondRematch(false) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                Text("Decline", fontWeight = FontWeight.Bold)
                            }
                        } else if (!isSimulatedMatch && !rematchDeclined) {
                            Button(
                                onClick = { multiplayerViewModel.requestRematch() },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(56.dp)
                                    .weight(1.2f),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .scale(winnerScale)
                                        .clip(CircleShape)
                                        .background(BrandAmber.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    VectorAvatar(
                                        avatar = getAvatarById(opponent.avatarId),
                                        size = 25.dp,
                                        showRing = true,
                                        ringColor = BrandAmber
                                    )
                                }
                                Spacer(Modifier.width(5.dp))
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Rematch", fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                multiplayerViewModel.leaveSession()
                                onLeaveToHome()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(56.dp)
                                .weight(0.8f)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Home", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            multiplayerViewModel.leaveSession()
                            onOpenLeaderboard()
                        }
                    ) {
                        Text("Leaderboard")
                    }
                }
            )
        }
    }
}

@Composable
private fun RewardRecordRow(title: String, rewards: List<BoxReward>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Black)
        if (rewards.isEmpty()) {
            Text("—", color = Color(0xFF64748B), fontSize = 14.sp)
        } else {
            rewards.forEach { reward ->
                Text(
                    text = reward.type?.icon ?: when (reward.kind) {
                        RewardKind.COIN -> "🪙"
                        RewardKind.GEM -> "💎"
                        RewardKind.JOKER -> "🃏"
                        RewardKind.COLLECTIBLE -> "🧩"
                        else -> "✨"
                    },
                    fontSize = 20.sp
                )
            }
        }
    }
}

@Composable
private fun LoadingPlayerBadge(name: String, avatarId: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.5f), Color.Transparent)))
                .border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            VectorAvatar(avatar = getAvatarById(avatarId), size = 48.dp, ringColor = accent)
        }
        Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ReactionBubble(reaction: Reaction, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 6.dp,
        modifier = modifier
            .offset(y = (-42).dp)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(reaction.emoji, fontSize = 18.sp)
            Text(reaction.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
