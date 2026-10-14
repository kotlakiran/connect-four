package com.kirankotla.connectfour.multiplayer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirankotla.connectfour.auth.PlayerProfile
import com.kirankotla.connectfour.auth.PublicPlayerProfile
import com.kirankotla.connectfour.model.BoardSpec
import com.kirankotla.connectfour.multiplayer.model.OnlineGameStatus
import com.kirankotla.connectfour.multiplayer.model.ONLINE_REWARD_MODE_CASUAL
import com.kirankotla.connectfour.multiplayer.model.ONLINE_REWARD_MODE_POWER_UP
import com.kirankotla.connectfour.multiplayer.model.allowedRewardModesFor
import com.kirankotla.connectfour.multiplayer.model.resolveRewardModeForGame
import com.kirankotla.connectfour.multiplayer.model.SUPPORTED_TURN_TIME_SECONDS
import com.kirankotla.connectfour.multiplayer.model.SimulatedPlayerCatalog
import com.kirankotla.connectfour.multiplayer.viewmodel.MultiplayerViewModel
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Intent
import android.os.Build
import com.kirankotla.connectfour.ui.theme.*
import com.kirankotla.connectfour.ui.components.MiniBoardDiagram
import com.kirankotla.connectfour.ui.components.VectorAvatar
import com.kirankotla.connectfour.ui.components.getAvatarById
import com.kirankotla.connectfour.util.SoundManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalContext
import com.kirankotla.connectfour.multiplayer.repository.NearbyMultiplayerRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    profile: PlayerProfile,
    multiplayerViewModel: MultiplayerViewModel,
    onGameStart: () -> Unit,
    onBack: () -> Unit,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val soundManager = remember(context) { SoundManager(context) }
    val session by multiplayerViewModel.session.collectAsState()
    val players by multiplayerViewModel.players.collectAsState()
    val isSearching by multiplayerViewModel.isSearching.collectAsState()
    val searchElapsedSeconds by multiplayerViewModel.searchElapsedSeconds.collectAsState()
    val onlineSearchPlayers by multiplayerViewModel.onlineSearchPlayers.collectAsState()
    val countdown by multiplayerViewModel.countdown.collectAsState()
    val privateCode by multiplayerViewModel.privateCode.collectAsState()
    val errorMessage by multiplayerViewModel.errorMessage.collectAsState()
    val isOnline by multiplayerViewModel.isOnline.collectAsState()
    val selectedBoard by multiplayerViewModel.selectedBoard.collectAsState()
    val selectedTurnTime by multiplayerViewModel.selectedTurnTimeSeconds.collectAsState()
    val rewardModes by multiplayerViewModel.selectedRewardModes.collectAsState()

    val isNearbySession by multiplayerViewModel.isNearbySession.collectAsState()
    val nearbyHosts by multiplayerViewModel.nearbyHosts.collectAsState()
    val nearbyStatus by multiplayerViewModel.nearbyStatus.collectAsState()
    val nearbyError by multiplayerViewModel.nearbyError.collectAsState()

    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) } // 0: Quick Play, 1: Private Room, 2: Nearby P2P
    var joinCodeInput by remember { mutableStateOf("") }

    BackHandler {
        multiplayerViewModel.leaveSession()
        onBack()
    }
    var nearbyPinInput by remember { mutableStateOf("") }
    var isCreatingPrivateGame by remember { mutableStateOf(false) }
    val clipboardManager: ClipboardManager = LocalClipboardManager.current

    LaunchedEffect(privateCode, errorMessage) {
        if (privateCode != null || errorMessage != null) {
            isCreatingPrivateGame = false
        }
    }

    LaunchedEffect(isSearching, searchElapsedSeconds) {
        if (!isSearching) {
            soundManager.stopPlayerSearchSound()
        }
        if (isSearching && searchElapsedSeconds > 0 && searchElapsedSeconds % 2 == 0) {
            soundManager.playPlayerSearchPulse()
        }
    }

    LaunchedEffect(countdown) {
        if (countdown != null) {
            soundManager.stopPlayerSearchSound()
        }
    }

    LaunchedEffect(players.size) {
        if (players.any { it.uid != profile.uid }) {
            soundManager.playMatchFound()
        }
    }

    var nearbyIsHosting by remember { mutableStateOf(false) }
    var nearbyIsScanning by remember { mutableStateOf(false) }
    var showFriendsDialog by remember { mutableStateOf(false) }

    // Nearby Connections runtime permissions differ per Android version.
    val nearbyPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Location: fine OR coarse is acceptable on newer Android versions
        val locationOk = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val othersOk = permissions
            .filterKeys { it != Manifest.permission.ACCESS_FINE_LOCATION && it != Manifest.permission.ACCESS_COARSE_LOCATION }
            .values.all { it }
        if (locationOk && othersOk) {
            if (nearbyIsHosting) {
                multiplayerViewModel.startNearbyHost(context, selectedBoard, selectedTurnTime)
            } else if (nearbyIsScanning) {
                multiplayerViewModel.startNearbyJoin(context, nearbyPinInput)
            }
        } else {
            multiplayerViewModel.reportPermissionDenied()
        }
    }

    // Trigger navigation to online game screen when state turns PLAYING
    LaunchedEffect(session?.status) {
        if (session?.status == OnlineGameStatus.PLAYING) {
            onGameStart()
        }
    }

    // Matchmaking pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Online Arena", fontWeight = FontWeight.Black)
                        Surface(
                            shape = CircleShape,
                            color = if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(10.dp)
                        ) {}
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            multiplayerViewModel.leaveSession()
                            onBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Play Connect Four with me!")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Hey! Check out Connect Four - an awesome, fast-paced multiplayer 4-in-a-row strategy game! 🎮🔥\n\nDownload now from Google Play:\nhttps://play.google.com/store/apps/details?id=com.kirankotla.connectfour"
                                )
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Connect Four App"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Connect Four App")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("multiplayer_lobby_screen")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!isOnline && selectedTab != 2) {
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⚡", fontSize = 16.sp)
                            Text(
                                text = "Offline Mode: Simulated matchmaking is enabled for instant practice.",
                                fontSize = 12.sp,
                                color = Color(0xFFB45309),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                val shownError = errorMessage ?: if (selectedTab == 2 && isNearbySession) nearbyError else null
                if (shownError != null) {
                    Text(
                        text = shownError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                when (selectedTab) {
                    0 -> {
                        // Quick Match Lobby Card
                        QuickMatchSection(
                            profile = profile,
                            currentUserId = multiplayerViewModel.myUid,
                            players = players,
                            isSearching = isSearching,
                            searchElapsedSeconds = searchElapsedSeconds,
                            onlineSearchPlayers = onlineSearchPlayers,
                            pulseScale = pulseScale,
                            selectedBoard = selectedBoard,
                            onSelectBoard = { multiplayerViewModel.setSelectedBoard(it) },
                            selectedTurnTime = selectedTurnTime,
                            onSelectTurnTime = { multiplayerViewModel.setSelectedTurnTimeSeconds(it) },
                            selectedRewardMode = resolveRewardModeForGame("QUICK_MATCH", rewardModes["QUICK_MATCH"]),
                            onSelectRewardMode = { multiplayerViewModel.setRewardMode("QUICK_MATCH", it) },
                            onStartSearching = {
                                soundManager.playUiTap()
                                multiplayerViewModel.startQuickMatch(selectedBoard, selectedTurnTime)
                            },
                            onCancelSearching = {
                                soundManager.playUiTap()
                                multiplayerViewModel.cancelMatchmaking()
                            },
                            onSearchProfileChange = { soundManager.playProfileSearchChange() }
                        )
                    }
                    1 -> {
                        // Private Room Section
                        PrivateRoomSection(
                            privateCode = if (isNearbySession) null else privateCode,
                            joinCodeInput = joinCodeInput,
                            players = players,
                            selectedBoard = selectedBoard,
                            onSelectBoard = { multiplayerViewModel.setSelectedBoard(it) },
                            selectedTurnTime = selectedTurnTime,
                            onSelectTurnTime = { multiplayerViewModel.setSelectedTurnTimeSeconds(it) },
                            selectedRewardMode = resolveRewardModeForGame("PRIVATE_ROOM", rewardModes["PRIVATE_ROOM"]),
                            onSelectRewardMode = { multiplayerViewModel.setRewardMode("PRIVATE_ROOM", it) },
                            onJoinCodeChanged = { joinCodeInput = it.uppercase() },
                            isCreatingPrivateGame = isCreatingPrivateGame,
                            onCreatePrivateGame = {
                                isCreatingPrivateGame = true
                                multiplayerViewModel.createPrivateGame(selectedBoard, selectedTurnTime)
                            },
                            onJoinPrivateGame = { multiplayerViewModel.joinPrivateGame(joinCodeInput) },
                            onCancelRoom = { multiplayerViewModel.cancelMatchmaking() },
                            onInviteFriends = { showFriendsDialog = true },
                            onCopyCode = { code ->
                                clipboardManager.setText(AnnotatedString(code))
                            }
                        )
                    }
                    2 -> {
                        // Nearby Offline Bluetooth & Wi-Fi P2P Section
                        NearbySection(
                            privateCode = if (isNearbySession) privateCode else null,
                            isSearching = isSearching && isNearbySession,
                            isHosting = nearbyIsHosting,
                            pulseScale = pulseScale,
                            hosts = nearbyHosts,
                            status = nearbyStatus,
                            pinInput = nearbyPinInput,
                            selectedBoard = selectedBoard,
                            onSelectBoard = { multiplayerViewModel.setSelectedBoard(it) },
                            selectedTurnTime = selectedTurnTime,
                            onSelectTurnTime = { multiplayerViewModel.setSelectedTurnTimeSeconds(it) },
                            selectedRewardMode = resolveRewardModeForGame("OFFLINE_NEARBY", rewardModes["OFFLINE_NEARBY"]),
                            onSelectRewardMode = { multiplayerViewModel.setRewardMode("OFFLINE_NEARBY", it) },
                            onPinChanged = { nearbyPinInput = it.filter(Char::isDigit).take(4) },
                            onStartHost = {
                                nearbyIsHosting = true
                                nearbyIsScanning = false
                                permissionLauncher.launch(nearbyPermissions.toTypedArray())
                            },
                            onStartJoin = {
                                nearbyIsHosting = false
                                nearbyIsScanning = true
                                permissionLauncher.launch(nearbyPermissions.toTypedArray())
                            },
                            onConnectToHost = { multiplayerViewModel.connectToNearbyHost(it) },
                            onCancel = {
                                nearbyIsHosting = false
                                nearbyIsScanning = false
                                multiplayerViewModel.cancelMatchmaking()
                            }
                        )
                    }
                }

                // Share App with Friends Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BrandIndigo.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = BrandIndigo, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Invite Friends", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Share Connect Four so friends can download & play", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Play Connect Four with me!")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Hey! Check out Connect Four - an awesome, fast-paced multiplayer 4-in-a-row strategy game! 🎮🔥\n\nDownload now from Google Play:\nhttps://play.google.com/store/apps/details?id=com.kirankotla.connectfour"
                                    )
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Connect Four App"))
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (showFriendsDialog) {
                FriendsDialog(
                    repository = multiplayerViewModel.multiplayerRepository,
                    onInviteFriend = { friend ->
                        multiplayerViewModel.inviteFriend(friend.friendUid)
                        showFriendsDialog = false
                    },
                    onDismiss = { showFriendsDialog = false }
                )
            }

            // Match Found & 3..2..1 Countdown Overlay
            if (countdown != null) {
                val localPlayer = players.find { it.uid == multiplayerViewModel.myUid }
                val opponent = players.find { it.uid != multiplayerViewModel.myUid }
                MatchFoundVsOverlay(
                    countdown = countdown!!,
                    pulseScale = pulseScale,
                    localName = localPlayer?.displayName?.takeIf { it.isNotBlank() } ?: profile.displayName,
                    localAvatarId = localPlayer?.avatarId ?: profile.avatarId,
                    opponentName = opponent?.displayName?.takeIf { it.isNotBlank() } ?: "Opponent",
                    opponentAvatarId = opponent?.avatarId ?: "piko"
                )
            }
        }
    }
}

@Composable
private fun MatchFoundVsOverlay(
    countdown: Int,
    pulseScale: Float,
    localName: String,
    localAvatarId: String,
    opponentName: String,
    opponentAvatarId: String
) {
    // One-shot slide-in when the overlay first appears
    var reveal by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { reveal = true }
    val slide by animateFloatAsState(
        targetValue = if (reveal) 0f else 1f,
        animationSpec = tween(460, easing = FastOutSlowInEasing),
        label = "vsSlide"
    )
    val localColor = CyberCyan
    val opponentColor = RubyRed

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1B2452), Color(0xFF05070F)),
                    radius = 1600f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "MATCH FOUND!",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            // VS clash row: you slide in from left, opponent from right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                PlayerBattleBadge(
                    name = localName,
                    avatarId = localAvatarId,
                    accent = localColor,
                    alignEnd = false,
                    modifier = Modifier
                        .weight(1f)
                        .offset(x = (slide * -260).dp)
                )

                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(listOf(ArcadeGoldTop, ArcadeGoldBottom))
                        )
                        .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VS",
                        color = Color(0xFF451A03),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                PlayerBattleBadge(
                    name = opponentName,
                    avatarId = opponentAvatarId,
                    accent = opponentColor,
                    alignEnd = true,
                    modifier = Modifier
                        .weight(1f)
                        .offset(x = (slide * 260).dp)
                )
            }

            Text(
                text = if (countdown == 0) "PLAY!" else "$countdown",
                color = BrandAmber,
                fontSize = 72.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.scale(pulseScale)
            )

            Text(
                text = "Both Players Ready",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PlayerBattleBadge(
    name: String,
    avatarId: String,
    accent: Color,
    alignEnd: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent))
                )
                .border(2.5.dp, accent.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            VectorAvatar(
                avatar = getAvatarById(avatarId),
                size = 78.dp,
                ringColor = accent
            )
        }
        Text(
            text = name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickMatchSection(
    profile: PlayerProfile,
    currentUserId: String,
    players: List<com.kirankotla.connectfour.multiplayer.model.OnlinePlayerDoc>,
    isSearching: Boolean,
    searchElapsedSeconds: Int,
    onlineSearchPlayers: List<PublicPlayerProfile>,
    pulseScale: Float,
    selectedBoard: BoardSpec,
    onSelectBoard: (BoardSpec) -> Unit,
    selectedTurnTime: Int,
    onSelectTurnTime: (Int) -> Unit,
    selectedRewardMode: String,
    onSelectRewardMode: (String) -> Unit,
    onStartSearching: () -> Unit,
    onCancelSearching: () -> Unit,
    onSearchProfileChange: () -> Unit
) {
    val candidateTransition = rememberInfiniteTransition(label = "candidate-rotation")
    val candidatePosition by candidateTransition.animateFloat(
        initialValue = 0f,
        targetValue = SimulatedPlayerCatalog.all.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(10_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "candidatePosition"
    )
    val candidateIndex = candidatePosition.toInt().coerceIn(0, SimulatedPlayerCatalog.all.lastIndex)
    LaunchedEffect(candidateIndex, isSearching) {
        if (isSearching) onSearchProfileChange()
    }
    val simulatedCandidate = SimulatedPlayerCatalog.all[
        candidateIndex
    ]
    val liveCandidates = onlineSearchPlayers.filter {
        it.uid != currentUserId && it.onlineStatus == com.kirankotla.connectfour.auth.OnlineStatus.ONLINE
    }
    val liveCandidate = liveCandidates.getOrNull(
        candidateIndex % liveCandidates.size.coerceAtLeast(1)
    )
    val searchTargetName = liveCandidate?.displayName ?: simulatedCandidate.displayName
    val searchTargetAvatar = liveCandidate?.avatarId ?: simulatedCandidate.avatarId

    val neonCyan = Color(0xFF00E5FF)
    val neonMagenta = Color(0xFFFF2BD6)
    val arenaBrush = Brush.verticalGradient(listOf(Color(0xFF0B1030), Color(0xFF1A0E3A)))

    // Neon arcade arena card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1030)),
        border = BorderStroke(2.dp, Brush.linearGradient(listOf(neonCyan, neonMagenta))),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(arenaBrush)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "FIND AN OPPONENT",
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = neonCyan,
                    fontSize = 22.sp,
                    style = MaterialTheme.typography.titleLarge.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(neonCyan, blurRadius = 18f)
                    ),
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "Challenge players from around the world",
                    color = Color(0xFFC4B5FD),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Players VS Showcase Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Current Player (YOU)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0F1A3D))
                        .border(1.5.dp, neonCyan.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                        .padding(vertical = 12.dp, horizontal = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFF5252), Color(0xFFFF7A00))
                                )
                            )
                            .border(2.dp, Color(0xFFFF8A65).copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        VectorAvatar(
                            avatar = getAvatarById(profile.avatarId),
                            size = 64.dp,
                            ringColor = BrandAmber
                        )
                    }
                    Text(
                        text = profile.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 1
                    )
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "YOU",
                            color = Color(0xFF10B981),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                // Center: VS badge over board preview
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(neonMagenta, Color(0xFF7C3AED))))
                            .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "VS",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .semantics {
                                contentDescription = "Selected board ${selectedBoard.displayName}"
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF120B33),
                        shadowElevation = 10.dp,
                        border = BorderStroke(1.5.dp, neonMagenta.copy(alpha = 0.85f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            MiniBoardDiagram(
                                rows = selectedBoard.rows,
                                cols = selectedBoard.cols,
                                accentColor = neonCyan,
                                isHighlighted = true,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }
                }

                // Right: Opponent
                val opponent = players.find { it.uid != currentUserId }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1B0F3F))
                        .border(1.5.dp, neonMagenta.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                        .padding(vertical = 12.dp, horizontal = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                if (opponent != null) Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)))
                                else Brush.linearGradient(listOf(Color(0xFF162035), Color(0xFF1E293B)))
                            )
                            .border(
                                width = if (isSearching) 2.5.dp else 1.dp,
                                color = if (isSearching) Color(0xFF00E5FF).copy(alpha = 0.8f) else Color(0xFF334155),
                                shape = CircleShape
                            )
                            .scale(if (isSearching) pulseScale else 1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (opponent != null) {
                            VectorAvatar(
                                avatar = getAvatarById(opponent.avatarId),
                                size = 64.dp,
                                ringColor = Color(0xFF00E5FF)
                            )
                        } else if (isSearching) {
                            VectorAvatar(
                                avatar = getAvatarById(searchTargetAvatar),
                                size = 64.dp,
                                ringColor = Color(0xFF00E5FF)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = if (isSearching) Color(0xFF00E5FF) else Color(0xFF64748B),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Text(
                        text = opponent?.displayName ?: if (isSearching) searchTargetName else "Opponent",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (opponent != null) Color.White else Color(0xFF94A3B8),
                        maxLines = 1
                    )
                    Surface(
                        color = if (opponent != null) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, if (opponent != null) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF334155))
                    ) {
                        Text(
                            text = if (opponent != null) "READY" else if (isSearching) "SEARCHING" else "WAITING",
                            color = if (opponent != null) Color(0xFF10B981) else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (isSearching) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Finding a player... ${searchElapsedSeconds}s / 10s",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )

                    Button(
                        onClick = onCancelSearching,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                            contentColor = Color(0xFFEF4444)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("STOP SEARCH", fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                    }

                }
            } else {
                // Grid Size Selector matching image reference
                BoardSpecSelector(
                    selectedBoard = selectedBoard,
                    onSelectBoard = onSelectBoard,
                    compact = true
                )
                TurnTimeSelector(selectedSeconds = selectedTurnTime, onSelect = onSelectTurnTime)
                RewardModeSelector("QUICK_MATCH", selectedRewardMode, onSelectRewardMode)

                // Full-width neon CTA
                Surface(
                    onClick = onStartSearching,
                    shape = RoundedCornerShape(100.dp),
                    color = neonMagenta,
                    shadowElevation = 12.dp,
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics { contentDescription = "Play online" }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF7C3AED), neonMagenta, Color(0xFFFF6A00))
                                )
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "PLAY ONLINE",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LobbyModePreview(
    accent: Color,
    board: BoardSpec,
    leftAvatar: String,
    rightAvatar: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF13233A),
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            MiniBoardDiagram(
                rows = board.rows,
                cols = board.cols,
                accentColor = accent,
                isHighlighted = true,
                modifier = Modifier.size(84.dp)
            )
            VectorAvatar(
                avatar = getAvatarById(leftAvatar),
                size = 48.dp,
                ringColor = accent,
                modifier = Modifier.align(Alignment.CenterStart).offset(x = 12.dp)
            )
            VectorAvatar(
                avatar = getAvatarById(rightAvatar),
                size = 48.dp,
                ringColor = Color.White,
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-12).dp)
            )
        }
    }
}

@Composable
private fun PrivateRoomSection(
    privateCode: String?,
    isCreatingPrivateGame: Boolean,
    joinCodeInput: String,
    players: List<com.kirankotla.connectfour.multiplayer.model.OnlinePlayerDoc>,
    selectedBoard: BoardSpec,
    onSelectBoard: (BoardSpec) -> Unit,
    selectedTurnTime: Int,
    onSelectTurnTime: (Int) -> Unit,
    selectedRewardMode: String,
    onSelectRewardMode: (String) -> Unit,
    onJoinCodeChanged: (String) -> Unit,
    onCreatePrivateGame: () -> Unit,
    onJoinPrivateGame: () -> Unit,
    onCancelRoom: () -> Unit,
    onInviteFriends: () -> Unit,
    onCopyCode: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1030)),
        border = BorderStroke(2.dp, Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFFF2BD6)))),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF0B1030), Color(0xFF1A0E3A))))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "PRIVATE GAME WITH FRIENDS",
                style = MaterialTheme.typography.titleMedium.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(Color(0xFF00E5FF), blurRadius = 18f)
                ),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color(0xFF00E5FF),
                fontSize = 17.sp,
                modifier = Modifier.semantics { heading() }
            )

            LobbyModePreview(
                accent = BrandTeal,
                board = selectedBoard,
                leftAvatar = "piko",
                rightAvatar = "mavi"
            )

            if (privateCode != null) {
                Surface(
                    color = Color(0xFF120B33),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFFF2BD6).copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SHARE THIS ROOM CODE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC4B5FD)
                        )
                        Text(
                            text = privateCode,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                            color = Color(0xFF00E5FF),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                shadow = androidx.compose.ui.graphics.Shadow(Color(0xFF00E5FF), blurRadius = 18f)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Join my Connect Four room: $privateCode")
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Play Connect Four with me! 🎮\n\n" +
                                            "👉 Tap to join room:\nhttps://kotlakiran.github.io/connectfour-join/?code=$privateCode\n\n" +
                                            "🔑 Room Code: $privateCode\n\n" +
                                            "Don't have Connect Four installed yet?\n" +
                                            "📥 Download it on Google Play:\nhttps://play.google.com/store/apps/details?id=com.kirankotla.connectfour"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Game Invite"))
                                },
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2BD6)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text("Share Link", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onCopyCode(privateCode) },
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                                border = BorderStroke(1.5.dp, Color(0xFF00E5FF)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Code", fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onInviteFriends,
                            shape = RoundedCornerShape(100.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFFF2BD6))))
                        ) {
                            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Invite from Friends", fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = onCancelRoom,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel Room", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        Text(
                            text = if (players.size > 1) "Opponent joined! Connecting..." else "Waiting for your friend to enter this code on their phone...",
                            fontSize = 12.sp,
                            color = Color(0xFFC4B5FD)
                        )
                    }
                }
            } else {
                BoardSpecSelector(
                    selectedBoard = selectedBoard,
                    onSelectBoard = onSelectBoard,
                    compact = true
                )
                TurnTimeSelector(selectedSeconds = selectedTurnTime, onSelect = onSelectTurnTime)
                RewardModeSelector("PRIVATE_ROOM", selectedRewardMode, onSelectRewardMode)
                Surface(
                    onClick = onCreatePrivateGame,
                    enabled = !isCreatingPrivateGame,
                    shape = RoundedCornerShape(100.dp),
                    color = Color.Transparent,
                    shadowElevation = if (isCreatingPrivateGame) 5.dp else 12.dp,
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF7C3AED), Color(0xFFFF2BD6), Color(0xFFFF6A00))
                                )
                            ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCreatingPrivateGame) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("CREATING ROOM...", fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 0.5.sp)
                        } else {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("CREATE PRIVATE GAME", fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 0.5.sp)
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF00E5FF).copy(alpha = 0.25f))

            Text(
                text = "JOIN WITH GAME CODE",
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = joinCodeInput,
                    onValueChange = onJoinCodeChanged,
                    placeholder = { Text("e.g. K7P4X2", color = Color(0xFF64748B)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFFFF2BD6).copy(alpha = 0.5f),
                        focusedContainerColor = Color(0xFF0F1A3D),
                        unfocusedContainerColor = Color(0xFF0F1A3D)
                    ),
                    trailingIcon = {
                        if (joinCodeInput.isNotEmpty()) {
                            IconButton(onClick = { onJoinCodeChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = onJoinPrivateGame,
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("JOIN", fontWeight = FontWeight.Black, color = Color(0xFF07111F))
                }
            }
        }
    }
}

@Composable
private fun NearbySection(
    privateCode: String?,
    isSearching: Boolean,
    isHosting: Boolean,
    pulseScale: Float,
    hosts: List<NearbyMultiplayerRepository.NearbyHost>,
    status: String?,
    pinInput: String,
    selectedBoard: BoardSpec,
    onSelectBoard: (BoardSpec) -> Unit,
    selectedTurnTime: Int,
    onSelectTurnTime: (Int) -> Unit,
    selectedRewardMode: String,
    onSelectRewardMode: (String) -> Unit,
    onPinChanged: (String) -> Unit,
    onStartHost: () -> Unit,
    onStartJoin: () -> Unit,
    onConnectToHost: (String) -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1030)),
        border = BorderStroke(2.dp, Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFFF2BD6)))),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(Color(0xFF0B1030), Color(0xFF1A0E3A))))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LobbyModePreview(
                accent = BrandAmber,
                board = selectedBoard,
                leftAvatar = "bolt",
                rightAvatar = "ember"
            )

            Surface(
                color = Color(0xFF0F1A3D),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                    Column {
                        Text(
                            text = "Offline Bluetooth & Local Wi-Fi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF00E5FF)
                        )
                        Text(
                            text = "Play peer-to-peer with a friend nearby. Zero internet or mobile data required!",
                            fontSize = 11.sp,
                            color = Color(0xFFC4B5FD)
                        )
                    }
                }
            }

            if (isSearching) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(BrandIndigo.copy(alpha = 0.35f), Color.Transparent))),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BrandIndigo, modifier = Modifier.size(44.dp))
                    }

                    if (isHosting || privateCode != null) {
                        // Host phone state: Shows PIN and waits for peer
                        Text(
                            text = "HOSTING NEARBY ROOM",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF2BD6)
                        )

                        if (privateCode != null) {
                            Surface(
                                color = Color(0xFF120B33),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, Color(0xFFFF2BD6).copy(alpha = 0.8f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("ROOM PIN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC4B5FD))
                                    Text(
                                        text = privateCode,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 4.sp,
                                        color = Color(0xFF00E5FF)
                                    )
                                }
                            }
                        }

                        Text(
                            text = status ?: "Waiting for nearby phone to connect...",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tell your friend to tap 'Find Nearby Rooms' on their phone and select your room.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )

                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cancel Hosting")
                        }
                    } else {
                        // Guest phone state: Discovering hosts
                        Text(
                            text = "LOOKING FOR NEARBY ROOMS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = BrandTeal
                        )

                        Text(
                            text = status ?: "Scanning for nearby hosts...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (hosts.isNotEmpty()) {
                            Text(
                                text = "AVAILABLE ROOMS (${hosts.size}):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                hosts.forEach { host ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF0F1A3D),
                                        border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.7f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = host.hostName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = BrandTeal.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "${host.board.rows}×${host.board.cols}",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = BrandTeal,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (host.pin.isNotBlank()) {
                                                    Text(
                                                        text = "PIN: ${host.pin}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = BrandTeal
                                                    )
                                                }
                                            }

                                            Button(
                                                onClick = { onConnectToHost(host.endpointId) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF07111F)),
                                                shape = RoundedCornerShape(100.dp)
                                            ) {
                                                Text("JOIN", fontWeight = FontWeight.Black)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Make sure the other phone tapped 'Host Match' and has Bluetooth & Wi-Fi turned on.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cancel Search")
                        }
                    }
                }
            } else {
                var nearbySubTab by remember { mutableIntStateOf(if (pinInput.isNotBlank()) 1 else 0) }

                // Mode Selector Toggle matching clean gaming aesthetics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color(0xFF0F1A3D))
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(100.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = { nearbySubTab = 0 },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(100.dp),
                        color = if (nearbySubTab == 0) Color(0xFFFF2BD6) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("HOST ROOM", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White)
                        }
                    }

                    Surface(
                        onClick = { nearbySubTab = 1 },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(100.dp),
                        color = if (nearbySubTab == 1) Color(0xFF7C3AED) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("FIND ROOM", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White)
                        }
                    }
                }

                if (nearbySubTab == 0) {
                    // Option 1: Host Match
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0F1A3D),
                        border = BorderStroke(1.5.dp, Color(0xFFFF2BD6).copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color(0xFFFF2BD6))
                                Text("Host a Match", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            }
                            Text(
                                text = "Your phone creates the room and shows a 4-digit PIN for your friend to connect.",
                                fontSize = 12.sp,
                                color = Color(0xFFC4B5FD)
                            )
                            BoardSpecSelector(
                                selectedBoard = selectedBoard,
                                onSelectBoard = onSelectBoard,
                                compact = true
                            )
                            TurnTimeSelector(selectedSeconds = selectedTurnTime, onSelect = onSelectTurnTime)
                            RewardModeSelector("OFFLINE_NEARBY", selectedRewardMode, onSelectRewardMode)

                            Button(
                                onClick = onStartHost,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2BD6)),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                Text("HOST MATCH (SHOW PIN)", fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                            }
                        }
                    }
                } else {
                    // Option 2: Join Match (Prominently visible at the top)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0F1A3D),
                        border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF00E5FF))
                                Text("Find Nearby Rooms", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            }
                            Text(
                                text = "Search for nearby rooms, then choose your friend's room from the list.",
                                fontSize = 12.sp,
                                color = Color(0xFFC4B5FD)
                            )

                            Button(
                                onClick = onStartJoin,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF07111F)),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                Text("FIND NEARBY ROOMS", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RewardModeSelector(
    gameMode: String,
    selectedMode: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = allowedRewardModesFor(gameMode)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "REWARDS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.weight(1f)
        )
        options.forEach { mode ->
            val isSelected = mode == selectedMode
            Surface(
                onClick = { onSelect(mode) },
                modifier = Modifier.testTag("reward_mode_${gameMode}_$mode"),
                shape = RoundedCornerShape(100.dp),
                color = if (isSelected) Color(0xFFA78BFA).copy(alpha = 0.28f) else Color(0xFF131B2E),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) Color(0xFFA78BFA) else Color(0xFF334155)
                )
            ) {
                Text(
                    text = when (mode) {
                        ONLINE_REWARD_MODE_CASUAL -> "Casual"
                        ONLINE_REWARD_MODE_POWER_UP -> "Power-up"
                        else -> "Ranked"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

internal val lobbyBoardOptions: List<BoardSpec> =
    (3..8).map { BoardSpec.square(it) } + listOf(BoardSpec.WIDE_3X6, BoardSpec.TALL_6X3)

@Composable
fun TurnTimeSelector(
    selectedSeconds: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "TURN TIME",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.weight(1f)
        )
        SUPPORTED_TURN_TIME_SECONDS.forEach { seconds ->
            val isSelected = seconds == selectedSeconds
            Surface(
                onClick = { onSelect(seconds) },
                shape = RoundedCornerShape(100.dp),
                color = if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.28f) else Color(0xFF131B2E),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                )
            ) {
                Text(
                    text = "${seconds}s",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun BoardSizeSelector(
    selectedSize: Int,
    onSelectSize: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    BoardSpecSelector(
        selectedBoard = BoardSpec.square(selectedSize),
        onSelectBoard = { onSelectSize(it.rows) },
        modifier = modifier,
        enabled = enabled
    )
}

@Composable
fun BoardSpecSelector(
    selectedBoard: BoardSpec,
    onSelectBoard: (BoardSpec) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val context = LocalContext.current
    val soundManager = remember(context) { SoundManager(context) }
    val accents = listOf(
        Color(0xFF38BDF8), Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFFB7185),
        Color(0xFFA78BFA), Color(0xFFF97316), Color(0xFF2DD4BF), Color(0xFFF472B6)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BOARD GRID SIZE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color(0xFF94A3B8)
            )
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Text(
                    text = "${selectedBoard.totalBoxes} SLOTS • ${selectedBoard.displayName.uppercase()}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            lobbyBoardOptions.forEachIndexed { index, board ->
                val label = if (board.rows == board.cols) board.label else "${board.rows} x ${board.cols}"
                val subtitle = "${board.totalBoxes} slots • ${board.dotLabel}"
                val isSelected = selectedBoard.rows == board.rows && selectedBoard.cols == board.cols
                val accent = accents[index % accents.size]

                Surface(
                    onClick = {
                        if (enabled) {
                            soundManager.playUiTap()
                            onSelectBoard(board)
                        }
                    },
                    enabled = enabled,
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) accent.copy(alpha = 0.28f) else Color(0xFF131B2E),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) accent else accent.copy(alpha = 0.18f)
                    ),
                    shadowElevation = if (isSelected) 10.dp else 2.dp,
                    modifier = Modifier
                        .width(if (compact) 68.dp else 112.dp)
                        .semantics { contentDescription = "$label board, $subtitle"; selected = isSelected }
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                if (isSelected) Brush.verticalGradient(
                                    listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.56f))
                                )
                                else Brush.verticalGradient(
                                    listOf(Color(0xFF162035), Color(0xFF0F172A))
                                )
                            )
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        MiniBoardDiagram(
                            rows = board.rows,
                            cols = board.cols,
                            accentColor = accent,
                            isHighlighted = isSelected,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isSelected) Color(0xFF07111F) else Color(0xFFE2E8F0)
                        )
                        if (!compact) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isSelected) Color(0xFF07111F).copy(alpha = 0.78f) else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }
    }
}
