package com.kirankotla.connectfour.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.kirankotla.connectfour.auth.*
import com.kirankotla.connectfour.data.PlayerPreferences
import com.kirankotla.connectfour.data.ProgressionProcessor
import com.kirankotla.connectfour.data.WalletBalance
import com.kirankotla.connectfour.leaderboard.repository.LeaderboardRepository
import com.kirankotla.connectfour.leaderboard.ui.LeaderboardScreen
import com.kirankotla.connectfour.model.AiDifficulty
import com.kirankotla.connectfour.model.GameMode
import com.kirankotla.connectfour.model.GameState
import com.kirankotla.connectfour.model.TurnRule
import com.kirankotla.connectfour.model.reward.RewardContext
import com.kirankotla.connectfour.model.reward.RewardTable
import com.kirankotla.connectfour.multiplayer.repository.DefaultMultiplayerRepository
import com.kirankotla.connectfour.multiplayer.ui.LobbyScreen
import com.kirankotla.connectfour.multiplayer.ui.OnlineGameScreen
import com.kirankotla.connectfour.multiplayer.viewmodel.MultiplayerViewModel
import com.kirankotla.connectfour.network.NetworkMonitor
import com.kirankotla.connectfour.rules.GameEngine
import com.kirankotla.connectfour.ui.screens.*
import com.kirankotla.connectfour.viewmodel.SetupState
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Onboarding : Screen("onboarding")
    object BoxDemo : Screen("box_demo")
    object Home : Screen("home")
    object Setup : Screen("setup/{mode}") {
        fun createRoute(mode: GameMode) = "setup/${mode.name}"
    }
    object Game : Screen("game/{mode}") {
        fun createRoute(mode: GameMode) = "game/${mode.name}"
    }
    object Campaign : Screen("campaign")
    object Tutorial : Screen("tutorial")
    object Profile : Screen("profile")
    object Statistics : Screen("statistics")
    object Settings : Screen("settings")
    object MoreGames : Screen("more_games")
    object Lobby : Screen("lobby/{tab}") {
        fun createRoute(tab: Int) = "lobby/$tab"
    }
    object OnlineGame : Screen("online_game")
    object Leaderboard : Screen("leaderboard")
}

@Composable
fun ConnectFourNavHost(
    progressionProcessor: ProgressionProcessor,
    playerPreferences: PlayerPreferences,
    navController: NavHostController = rememberNavController(),
    deepLinkCode: String? = null,
    onClearDeepLink: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Wait for persisted settings before deciding the start route, so already
    // onboarded users are not briefly sent back to the name/onboarding screen.
    val loadedSettings by playerPreferences.settingsFlow.collectAsState(initial = null)
    if (loadedSettings == null) {
        ConnectFourLoadingScreen()
        return
    }
    val settings = loadedSettings!!

    val networkMonitor = remember { NetworkMonitor(context) }
    val authRepo = remember { FirebaseAuthRepository(context, progressionProcessor) }
    val authViewModel = remember { AuthViewModel(authRepo) }
    val multiplayerRepo = remember { DefaultMultiplayerRepository(networkMonitor) }
    val multiplayerViewModel = remember { MultiplayerViewModel(multiplayerRepo, authRepo, networkMonitor) }
    val leaderboardRepo = remember { LeaderboardRepository() }
    var showAuthDialog by remember { mutableStateOf(false) }

    val authState by authViewModel.authState.collectAsState()
    var profile by remember { mutableStateOf(com.kirankotla.connectfour.data.PlayerProfileEntity()) }
    var stats by remember { mutableStateOf(com.kirankotla.connectfour.data.PlayerStatsEntity()) }
    var activeSetupState by remember { mutableStateOf(SetupState()) }

    val currentAuthProfile = (authState as? AuthState.Authenticated)?.profile
        ?: PlayerProfile(uid = "local_player", displayName = profile.name.ifBlank { "Player 1" })

    LaunchedEffect(Unit) {
        profile = progressionProcessor.getProfile()
        stats = progressionProcessor.getStats()
    }

    LaunchedEffect(deepLinkCode) {
        val code = deepLinkCode?.trim()
        if (!code.isNullOrBlank()) {
            navController.navigate(Screen.Lobby.createRoute(1))
            multiplayerViewModel.joinPrivateGame(code)
            onClearDeepLink()
        }
    }

    val startDestination = if (settings.onboardingCompleted) {
        Screen.Home.route
    } else {
        Screen.Auth.route
    }

    val currentRoute by navController.currentBackStackEntryAsState()
    val showGlobalSettings = currentRoute?.destination?.route?.let { route ->
        route != Screen.Home.route && route != Screen.Game.route && route != Screen.Settings.route &&
            route != Screen.Auth.route && route != Screen.Onboarding.route
    } == true

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
        composable(Screen.BoxDemo.route) {
            BoxDemoScreen(
                onContinue = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.BoxDemo.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Auth.route) {
            WelcomeAuthScreen(
                authViewModel = authViewModel,
                onAuthSuccess = {
                    val dest = if (!settings.onboardingCompleted) Screen.Onboarding.route else Screen.Home.route
                    navController.navigate(dest) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                initialName = currentAuthProfile.displayName.takeUnless { it == "Player 1" },
                profilePhotoUrl = currentAuthProfile.photoUrl,
                onFinishOnboarding = { name, age ->
                    coroutineScope.launch {
                        progressionProcessor.updateProfile(name, age)
                        playerPreferences.setOnboardingCompleted(true)
                        profile = progressionProcessor.getProfile()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                profile = profile,
                stats = stats,
                hasSavedGame = false,
                themeMode = settings.themeMode,
                firstTimeTutorialNeeded = !settings.tutorialCompleted,
                onCompleteTutorial = {
                    coroutineScope.launch {
                        playerPreferences.setTutorialCompleted(true)
                    }
                },
                onToggleThemeMode = { mode ->
                    coroutineScope.launch {
                        playerPreferences.updateThemeMode(mode)
                    }
                },
                onStartGameMode = { mode ->
                    if (mode == GameMode.QUICK_PLAY) {
                        navController.navigate(Screen.Game.createRoute(GameMode.QUICK_PLAY))
                    } else {
                        navController.navigate(Screen.Setup.createRoute(mode))
                    }
                },
                onResumeGame = {
                    navController.navigate(Screen.Game.createRoute(GameMode.QUICK_PLAY))
                },
                onOpenCampaign = {
                    navController.navigate(Screen.Campaign.route)
                },
                onOpenTutorial = {
                    navController.navigate(Screen.Tutorial.route)
                },
                onOpenProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onOpenStatistics = {
                    navController.navigate(Screen.Statistics.route)
                },
                onOpenMultiplayer = {
                    navController.navigate(Screen.Lobby.createRoute(0))
                },
                onOpenPrivateRoom = {
                    navController.navigate(Screen.Lobby.createRoute(1))
                },
                onOpenNearbyP2P = {
                    navController.navigate(Screen.Lobby.createRoute(2))
                },
                onOpenLeaderboard = {
                    navController.navigate(Screen.Leaderboard.route)
                },
                onOpenMoreGames = {
                    navController.navigate(Screen.MoreGames.route)
                },
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.MoreGames.route) {
            MoreGamesScreen(
                selfPackage = "com.kirankotla.connectfour",
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Setup.route) { backStackEntry ->
            val modeName = backStackEntry.arguments?.getString("mode") ?: GameMode.QUICK_PLAY.name
            val mode = GameMode.valueOf(modeName)

            var setupState by remember(mode) {
                mutableStateOf(
                    SetupState(
                        mode = mode,
                        turnRule = if (mode == GameMode.DICE_MODE) TurnRule.MAX_LINES else TurnRule.STANDARD
                    )
                )
            }

            SetupScreen(
                setupState = setupState,
                onGridSizeChanged = { size -> setupState = setupState.copy(rows = size, cols = size) },
                onBoardDimensionsChanged = { r, c -> setupState = setupState.copy(rows = r, cols = c) },
                onDifficultyChanged = { diff -> setupState = setupState.copy(aiDifficulty = diff) },
                onTurnRuleChanged = { rule -> setupState = setupState.copy(turnRule = rule) },
                onToggleFirstPlayer = { isAiFirst -> setupState = setupState.copy(firstPlayerIsAi = isAiFirst) },
                onPlayer1NameChanged = { name -> setupState = setupState.copy(player1Name = name) },
                onPlayer2NameChanged = { name -> setupState = setupState.copy(player2Name = name) },
                onTogglePlayer2StartsFirst = { p2Starts -> setupState = setupState.copy(player2StartsFirst = p2Starts) },
                onPlayer1AvatarChanged = { avatarId -> setupState = setupState.copy(player1AvatarId = avatarId) },
                onPlayer2AvatarChanged = { avatarId -> setupState = setupState.copy(player2AvatarId = avatarId) },
                onTurnTimeChanged = { seconds -> setupState = setupState.copy(turnTimeSeconds = seconds) },
                onPowerUpModeChanged = { enabled -> setupState = setupState.copy(powerUpMode = enabled) },
                onStartMatch = {
                    activeSetupState = setupState
                    navController.navigate(Screen.Game.createRoute(mode))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Game.route) { backStackEntry ->
            val modeName = backStackEntry.arguments?.getString("mode") ?: GameMode.QUICK_PLAY.name
            val mode = GameMode.valueOf(modeName)

            val configuredRows = if (activeSetupState.mode == mode) activeSetupState.rows else 6
            val configuredCols = if (activeSetupState.mode == mode) activeSetupState.cols else 7
            val configuredRule = if (activeSetupState.mode == mode) activeSetupState.turnRule else TurnRule.STANDARD
            val configuredAiDiff = if (mode == GameMode.VS_AI || mode == GameMode.QUICK_PLAY || mode == GameMode.DICE_MODE) activeSetupState.aiDifficulty else null
            val powerUpEnabled = activeSetupState.mode == mode && activeSetupState.powerUpMode
            val simulatedOpponents = listOf(
                Pair("Alex", "mavi"),
                Pair("Priya", "bolt"),
                Pair("Marcus", "sprout"),
                Pair("Sora", "ember"),
                Pair("Elena", "luna"),
                Pair("Liam", "pixel")
            )
            val simOpponent = remember(mode, activeSetupState) { simulatedOpponents.random() }
            val aiOpponent = when (activeSetupState.aiDifficulty) {
                AiDifficulty.BEGINNER -> Pair("Rookie Bot", "sprout")
                AiDifficulty.EASY -> Pair("Easy Bot", "mavi")
                AiDifficulty.MEDIUM -> Pair("Tactical Bot", "bolt")
                AiDifficulty.HARD -> Pair("Hard Bot", "ember")
                AiDifficulty.EXPERT -> Pair("Master Bot", "koko")
            }
            val configuredOpponent = if (mode == GameMode.VS_AI || mode == GameMode.DICE_MODE) aiOpponent else simOpponent

            val configuredPlayers = if (mode == GameMode.PASS_AND_PLAY) {
                val p1 = com.kirankotla.connectfour.model.Player(id = 1, name = activeSetupState.player1Name.ifBlank { "Player 1" }, colorHex = "#FF5252", mascotId = activeSetupState.player1AvatarId.ifBlank { "piko" }, isAi = false)
                val p2 = com.kirankotla.connectfour.model.Player(id = 2, name = activeSetupState.player2Name.ifBlank { "Player 2" }, colorHex = "#06B6D4", mascotId = activeSetupState.player2AvatarId.ifBlank { "mavi" }, isAi = false)
                if (activeSetupState.player2StartsFirst) listOf(p2, p1) else listOf(p1, p2)
            } else if (mode == GameMode.VS_AI && activeSetupState.firstPlayerIsAi) {
                listOf(
                    com.kirankotla.connectfour.model.Player(id = 2, name = configuredOpponent.first, colorHex = "#06B6D4", mascotId = configuredOpponent.second, isAi = true),
                    com.kirankotla.connectfour.model.Player(id = 1, name = profile.name.ifBlank { "Player 1" }, colorHex = "#FF5252", mascotId = activeSetupState.player1AvatarId.ifBlank { profile.avatarId }, isAi = false)
                )
            } else if (mode == GameMode.VS_AI || mode == GameMode.QUICK_PLAY || mode == GameMode.DICE_MODE) {
                listOf(
                    com.kirankotla.connectfour.model.Player(id = 1, name = profile.name.ifBlank { "Player 1" }, colorHex = "#FF5252", mascotId = activeSetupState.player1AvatarId.ifBlank { profile.avatarId }, isAi = false),
                    com.kirankotla.connectfour.model.Player(id = 2, name = configuredOpponent.first, colorHex = "#06B6D4", mascotId = configuredOpponent.second, isAi = true)
                )
            } else {
                GameEngine.defaultPlayers(mode)
            }

            var gameState by remember(mode, activeSetupState) {
                mutableStateOf(
                    GameEngine.createInitialState(
                        rows = configuredRows,
                        cols = configuredCols,
                        mode = mode,
                        turnRule = configuredRule,
                        aiDifficulty = configuredAiDiff,
                        players = configuredPlayers
                    ).let { if (powerUpEnabled) com.kirankotla.connectfour.rules.PowerUpEngine.enable(it) else it }
                )
            }
            var gameUiReady by remember(mode, activeSetupState) { mutableStateOf(false) }
            LaunchedEffect(mode, activeSetupState) {
                gameUiReady = false
                kotlinx.coroutines.yield()
                kotlinx.coroutines.delay(250L)
                gameUiReady = true
            }
            var hintEdgeId by remember { mutableStateOf<String?>(null) }
            var boxReward by remember(mode, activeSetupState) { mutableStateOf<com.kirankotla.connectfour.model.reward.BoxReward?>(null) }
            var rewardEventId by remember(mode, activeSetupState) { mutableIntStateOf(0) }
            var rewardBoxId by remember(mode, activeSetupState) { mutableStateOf<String?>(null) }
            var rewardHistory by remember(mode, activeSetupState) { mutableStateOf<List<com.kirankotla.connectfour.model.reward.BoxReward>>(emptyList()) }
            val walletBalance by progressionProcessor.walletFlow().collectAsState(initial = WalletBalance())
            var ownedJokerIds by remember(mode, activeSetupState) { mutableStateOf<List<String>>(emptyList()) }
            LaunchedEffect(mode, activeSetupState) {
                if (powerUpEnabled) ownedJokerIds = progressionProcessor.ownedJokerIds()
            }

            val rewardSoundManager = remember(context) { com.kirankotla.connectfour.util.SoundManager(context) }
            rewardSoundManager.isEnabled = settings.soundEnabled

            fun awardBoxRewards(previous: GameState, next: GameState) {
                val completedBoxes = next.boxOwners.keys - previous.boxOwners.keys
                if (completedBoxes.isNotEmpty()) {
                    val chainLength = com.kirankotla.connectfour.rules.MoveInsights.trailingChainCount(next.moveHistory)
                    val context = when {
                        chainLength >= 3 -> RewardContext.CHAIN
                        completedBoxes.size >= 2 -> RewardContext.DOUBLE_CAPTURE
                        else -> RewardContext.NORMAL_BOX
                    }
                    val seed = next.gameId.hashCode().toLong() xor next.moveHistory.size.toLong()
                    val moveDurationMs = System.currentTimeMillis() - previous.updatedAtEpochMs
                    val reward = RewardTable.roll(seed, context, moveDurationMs, completedBoxes.size)
                    boxReward = reward
                    rewardBoxId = completedBoxes.lastOrNull()
                    rewardHistory = (rewardHistory + reward).takeLast(6)
                    rewardEventId++
                    rewardSoundManager.playRewardSound(reward.type?.soundProfile)
                    coroutineScope.launch {
                        progressionProcessor.awardReward(reward)
                        if (powerUpEnabled) ownedJokerIds = progressionProcessor.ownedJokerIds()
                    }
                }
            }

            fun applyLocalMove(edgeId: String) {
                val current = gameState ?: return
                if (
                    current.isGameOver ||
                    current.currentPlayer.isAi ||
                    current.isEdgeClaimed(edgeId) ||
                    (current.mode == GameMode.DICE_MODE && current.diceState?.hasRolledThisTurn != true)
                ) return
                val result = com.kirankotla.connectfour.rules.PowerUpEngine.applyMove(current, edgeId)
                gameState = result.newState
                hintEdgeId = null
                awardBoxRewards(current, result.newState)
            }

            // Automatic AI Turn Trigger
            LaunchedEffect(gameState?.currentPlayerIndex, gameState?.moveHistory?.size, gameState?.isGameOver) {
                val state = gameState ?: return@LaunchedEffect
                if (state.isGameOver) return@LaunchedEffect
                if (state.currentPlayer.isAi) {
                    kotlinx.coroutines.delay(650L)
                    val active = gameState ?: return@LaunchedEffect
                    if (active.isGameOver || !active.currentPlayer.isAi) return@LaunchedEffect
                    if (active.mode == GameMode.DICE_MODE && active.diceState?.hasRolledThisTurn == false) {
                        gameState = GameEngine.rollDice(active)
                        kotlinx.coroutines.delay(400L)
                    }
                    val currentActive = gameState ?: return@LaunchedEffect
                    if (currentActive.isGameOver || !currentActive.currentPlayer.isAi) return@LaunchedEffect
                    val move = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        com.kirankotla.connectfour.ai.AiPlayer.chooseMove(
                            currentActive,
                            currentActive.aiDifficulty ?: AiDifficulty.MEDIUM
                        )
                    }
                    if (move.isNotBlank() && !currentActive.isEdgeClaimed(move)) {
                        val res = com.kirankotla.connectfour.rules.PowerUpEngine.applyMove(currentActive, move)
                        gameState = res.newState
                        hintEdgeId = null
                        awardBoxRewards(currentActive, res.newState)
                    }
                }
            }

            if (gameUiReady) {
                GameScreen(
                gameState = gameState,
                hintEdgeId = hintEdgeId,
                highContrast = settings.highContrast,
                reduceMotion = settings.reduceMotion,
                soundEnabled = settings.soundEnabled,
                onToggleSound = { enabled -> coroutineScope.launch { playerPreferences.updateSound(enabled) } },
                hapticEnabled = settings.hapticEnabled,
                onToggleHaptic = { enabled -> coroutineScope.launch { playerPreferences.updateHaptic(enabled) } },
                musicEnabled = settings.musicEnabled,
                onToggleMusic = { enabled -> coroutineScope.launch { playerPreferences.updateMusic(enabled) } },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                turnTimerEnabled = true,
                turnTimerSeconds = if (activeSetupState.mode == mode) activeSetupState.turnTimeSeconds else 15,
                onTurnTimeOut = {
                    val activeState = gameState
                    if (activeState != null && !activeState.isGameOver && !activeState.currentPlayer.isAi) {
                        if (activeState.mode == GameMode.DICE_MODE && activeState.diceState?.hasRolledThisTurn == false) {
                            val rolled = GameEngine.rollDice(activeState)
                            val legal = GameEngine.getLegalEdges(rolled)
                            val randomMove = legal.randomOrNull()
                            if (randomMove != null) {
                                val res = GameEngine.applyMove(rolled, randomMove)
                                gameState = res.newState
                                hintEdgeId = null
                            } else {
                                gameState = rolled
                            }
                        } else {
                            val legal = GameEngine.getLegalEdges(activeState)
                            val randomMove = legal.randomOrNull()
                            if (randomMove != null) {
                                val res = GameEngine.applyMove(activeState, randomMove)
                                gameState = res.newState
                                hintEdgeId = null
                            }
                        }
                    }
                },
                onEdgeTap = { edgeId ->
                    applyLocalMove(edgeId)
                },
                reward = boxReward,
                rewardEventId = rewardEventId,
                rewardBoxId = rewardBoxId,
                rewardHistory = rewardHistory,
                walletBalance = walletBalance ?: WalletBalance(),
                ownedJokerIds = ownedJokerIds,
                onUseJoker = { jokerId ->
                    val current = gameState
                    if (powerUpEnabled && current != null && !current.currentPlayer.isAi &&
                        jokerId in ownedJokerIds
                    ) {
                        val updated = com.kirankotla.connectfour.rules.PowerUpEngine.useJoker(current, jokerId)
                        if (updated != null) {
                            gameState = updated
                            hintEdgeId = null
                            ownedJokerIds = ownedJokerIds - jokerId
                            coroutineScope.launch {
                                progressionProcessor.consumeJoker(jokerId)
                                ownedJokerIds = progressionProcessor.ownedJokerIds()
                            }
                        }
                    }
                },
                onDiceRollTap = {
                    val activeState = gameState
                    if (
                        activeState != null &&
                        activeState.mode == GameMode.DICE_MODE &&
                        !activeState.isGameOver &&
                        !activeState.currentPlayer.isAi &&
                        activeState.diceState?.hasRolledThisTurn == false
                    ) {
                        gameState = GameEngine.rollDice(activeState)
                    }
                },
                onEndTurnEarlyTap = {
                    val activeState = gameState
                    if (
                        activeState != null &&
                        activeState.mode == GameMode.DICE_MODE &&
                        !activeState.isGameOver &&
                        !activeState.currentPlayer.isAi &&
                        activeState.diceState?.hasRolledThisTurn == true
                    ) {
                        gameState = GameEngine.endTurnEarlyInDiceMode(activeState)
                    }
                },
                onRequestHint = {
                    val state = gameState
                    if (state != null && !state.isGameOver) {
                        hintEdgeId = com.kirankotla.connectfour.ai.AiPlayer.chooseMove(state, AiDifficulty.HARD)
                    }
                },
                onUndoMove = {
                    val current = gameState
                    if (current != null && current.moveHistory.isNotEmpty() && current.powerUpState == null) {
                        val movesToDrop = if (current.mode == GameMode.VS_AI && current.moveHistory.size >= 2) 2 else 1
                        val newHistory = current.moveHistory.dropLast(movesToDrop)
                        var reconstructed = GameEngine.createInitialState(
                            gameId = current.gameId,
                            rows = current.rows,
                            cols = current.cols,
                            mode = current.mode,
                            turnRule = current.turnRule,
                            aiDifficulty = current.aiDifficulty,
                            players = current.players.map { it.copy(score = 0) }
                        )
                        for (record in newHistory) {
                            reconstructed = GameEngine.applyMove(reconstructed, record.edgeId).newState
                        }
                        gameState = reconstructed
                        hintEdgeId = null
                    }
                },
                onRestartGame = {
                    if (gameState != null) {
                        gameState = GameEngine.createInitialState(
                            rows = configuredRows,
                            cols = configuredCols,
                            mode = mode,
                            turnRule = configuredRule,
                            aiDifficulty = configuredAiDiff,
                            players = configuredPlayers.map { it.copy(score = 0) }
                        ).let { if (powerUpEnabled) com.kirankotla.connectfour.rules.PowerUpEngine.enable(it) else it }
                        hintEdgeId = null
                    }
                },
                    onBackToHome = {
                        val popped = navController.popBackStack(Screen.Home.route, false)
                        if (!popped) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                )
            } else {
                GameLoadingScreen()
            }
        }

        composable(Screen.Campaign.route) {
            CampaignScreen(
                onSelectLevel = { level ->
                    navController.navigate(Screen.Game.createRoute(GameMode.CAMPAIGN))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Tutorial.route) {
            TutorialScreen(
                onBack = { navController.popBackStack() },
                onCompleteTutorial = {
                    coroutineScope.launch {
                        playerPreferences.setTutorialCompleted(true)
                    }
                }
            )
        }

        composable(Screen.Profile.route) {
            val summaries by progressionProcessor.dao.getRecentMatchSummariesFlow().collectAsState(initial = emptyList())
            val achievements by progressionProcessor.dao.getAllAchievementsFlow().collectAsState(initial = emptyList())
            ProfileScreen(
                profile = profile,
                stats = stats,
                achievements = achievements,
                summaries = summaries,
                onBack = { navController.popBackStack() },
                onOpenVisualStatistics = { navController.navigate(Screen.Statistics.route) },
                onUpdateAvatar = { newAvatarId ->
                    coroutineScope.launch {
                        progressionProcessor.updateAvatar(newAvatarId)
                        profile = progressionProcessor.getProfile()
                    }
                },
                onOpenAuth = { showAuthDialog = true },
                authProfile = currentAuthProfile,
                onEditProfileName = { newName ->
                    coroutineScope.launch {
                        progressionProcessor.updateProfile(newName, profile.age)
                        profile = progressionProcessor.getProfile()
                        authViewModel.updateProfile(newName, profile.avatarId)
                    }
                }
            )
        }

        composable(Screen.Statistics.route) {
            val summaries by progressionProcessor.dao.getRecentMatchSummariesFlow().collectAsState(initial = emptyList())
            VisualStatisticsScreen(
                matchSummaries = summaries,
                playerStats = stats,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Lobby.route) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getString("tab")?.toIntOrNull() ?: 0
            LobbyScreen(
                profile = currentAuthProfile,
                multiplayerViewModel = multiplayerViewModel,
                onGameStart = {
                    navController.navigate(Screen.OnlineGame.route)
                },
                onBack = { navController.popBackStack() },
                initialTab = initialTab
            )
        }

        composable(Screen.OnlineGame.route) {
            OnlineGameScreen(
                multiplayerViewModel = multiplayerViewModel,
                onOpenLeaderboard = {
                    multiplayerViewModel.leaveSession()
                    navController.navigate(Screen.Leaderboard.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                soundEnabled = settings.soundEnabled,
                onToggleSound = { enabled -> coroutineScope.launch { playerPreferences.updateSound(enabled) } },
                hapticEnabled = settings.hapticEnabled,
                onToggleHaptic = { enabled -> coroutineScope.launch { playerPreferences.updateHaptic(enabled) } },
                musicEnabled = settings.musicEnabled,
                onToggleMusic = { enabled -> coroutineScope.launch { playerPreferences.updateMusic(enabled) } },
                onLeaveToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Leaderboard.route) {
            LeaderboardScreen(
                currentProfile = currentAuthProfile,
                repository = leaderboardRepo,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
                onSaveProfile = { name, age ->
                    coroutineScope.launch {
                        progressionProcessor.updateProfile(name, age)
                        profile = progressionProcessor.getProfile()
                    }
                },
                onToggleSound = { enabled -> coroutineScope.launch { playerPreferences.updateSound(enabled) } },
                onToggleMusic = { enabled -> coroutineScope.launch { playerPreferences.updateMusic(enabled) } },
                onToggleHaptic = { enabled -> coroutineScope.launch { playerPreferences.updateHaptic(enabled) } },
                onToggleReduceMotion = { enabled -> coroutineScope.launch { playerPreferences.updateReduceMotion(enabled) } },
                onToggleHighContrast = { enabled -> coroutineScope.launch { playerPreferences.updateHighContrast(enabled) } },
                onToggleAutoRoll = { enabled -> coroutineScope.launch { playerPreferences.updateAutoRollDice(enabled) } },
                onUpdateThemeMode = { mode -> coroutineScope.launch { playerPreferences.updateThemeMode(mode) } }
            )
        }
        }

        if (showGlobalSettings) {
            IconButton(
                onClick = { navController.navigate(Screen.Settings.route) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(Color(0xDD10132B))
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
    }

    if (showAuthDialog) {
        AuthDialog(
            authViewModel = authViewModel,
            onDismiss = { showAuthDialog = false }
        )
    }
}
