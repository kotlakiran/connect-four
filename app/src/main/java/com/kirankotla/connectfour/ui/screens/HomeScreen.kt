package com.kirankotla.connectfour.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirankotla.connectfour.data.PlayerProfileEntity
import com.kirankotla.connectfour.data.PlayerStatsEntity
import com.kirankotla.connectfour.data.ThemeMode
import com.kirankotla.connectfour.model.GameMode
import com.kirankotla.connectfour.ui.UiTestTags
import com.kirankotla.connectfour.ui.components.InteractiveTutorialOverlay
import com.kirankotla.connectfour.ui.components.MiniBoardDiagram
import com.kirankotla.connectfour.ui.components.VectorAvatar
import com.kirankotla.connectfour.ui.components.getAvatarById
import com.kirankotla.connectfour.ui.theme.*
import com.kirankotla.connectfour.util.HapticManager
import com.kirankotla.connectfour.util.SoundManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    profile: PlayerProfileEntity,
    stats: PlayerStatsEntity,
    hasSavedGame: Boolean,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onToggleThemeMode: ((ThemeMode) -> Unit)? = null,
    onStartGameMode: (GameMode) -> Unit,
    onResumeGame: () -> Unit,
    onOpenCampaign: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenStatistics: () -> Unit = {},
    firstTimeTutorialNeeded: Boolean = false,
    onCompleteTutorial: () -> Unit = {},
    onOpenMultiplayer: () -> Unit = {},
    onOpenPrivateRoom: () -> Unit = {},
    onOpenNearbyP2P: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
    onOpenMoreGames: () -> Unit = {},
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val soundManager = remember(context) { SoundManager(context) }
    val hapticManager = remember(context) { HapticManager(context) }

    var showInteractiveTutorial by remember(firstTimeTutorialNeeded) {
        mutableStateOf(firstTimeTutorialNeeded)
    }

    var showQuestDialog by remember { mutableStateOf(false) }
    var isQuestClaimed by remember { mutableStateOf(false) }

    if (showInteractiveTutorial) {
        InteractiveTutorialOverlay(
            onDismiss = {
                showInteractiveTutorial = false
                onCompleteTutorial()
            }
        )
    }

    // Ambient pulse for stage lighting
    val infiniteTransition = rememberInfiniteTransition(label = "stage_glow")
    val stagePulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stagePulse"
    )

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            Surface(
                color = Color(0xFF10132B).copy(alpha = 0.95f),
                tonalElevation = 16.dp,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                border = BorderStroke(1.5.dp, Color(0xFF2E376E).copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .testTag("home_bottom_navigation")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play Tab (Active Hero 3D Pill)
                    Surface(
                        color = Color(0xFF2563EB),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.2.dp, CyberCyan.copy(alpha = 0.8f)),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.SportsEsports,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "PLAY",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Inactive tabs stay icon-only so the selected tab owns the label.
                    IconButton(
                        onClick = {
                            soundManager.playUiTap()
                            onOpenCampaign()
                        },
                        modifier = Modifier.testTag("nav_campaign_button")
                    ) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = "Saga",
                            tint = Color(0xFF8E9BB5),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            soundManager.playUiTap()
                            onOpenStatistics()
                        },
                        modifier = Modifier.testTag("nav_statistics_button")
                    ) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = "Stats",
                            tint = Color(0xFF8E9BB5),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            soundManager.playUiTap()
                            onOpenProfile()
                        },
                        modifier = Modifier.testTag("nav_profile_button")
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = "Ranks",
                            tint = Color(0xFF8E9BB5),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            soundManager.playUiTap()
                            onOpenMoreGames()
                        },
                        modifier = Modifier.testTag("nav_more_games_button")
                    ) {
                        Icon(
                            Icons.Default.Apps,
                            contentDescription = "More Games",
                            tint = Color(0xFF8E9BB5),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            soundManager.playUiTap()
                            onOpenSettings()
                        },
                        modifier = Modifier.testTag(UiTestTags.HOME_SETTINGS_BUTTON)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFF8E9BB5),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // MATCH MASTERS STAGE BACKGROUND WITH SPOTLIGHT & STAR DUST
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            StageBackgroundTop,
                            StageBackgroundMid,
                            StageBackgroundBottom
                        )
                    )
                )
        ) {
            // Stage Overhead Spotlight Bloom
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Radial overhead spotlight
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF9333EA).copy(alpha = 0.28f * stagePulse),
                            Color(0xFF3B82F6).copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(canvasWidth * 0.5f, 0f),
                        radius = canvasWidth * 0.9f
                    ),
                    radius = canvasWidth * 0.9f,
                    center = androidx.compose.ui.geometry.Offset(canvasWidth * 0.5f, 0f)
                )

                // Background arcade floating diamond stars
                val starPoints = listOf(
                    androidx.compose.ui.geometry.Offset(canvasWidth * 0.15f, canvasHeight * 0.12f),
                    androidx.compose.ui.geometry.Offset(canvasWidth * 0.85f, canvasHeight * 0.18f),
                    androidx.compose.ui.geometry.Offset(canvasWidth * 0.08f, canvasHeight * 0.40f),
                    androidx.compose.ui.geometry.Offset(canvasWidth * 0.92f, canvasHeight * 0.48f),
                    androidx.compose.ui.geometry.Offset(canvasWidth * 0.22f, canvasHeight * 0.65f),
                    androidx.compose.ui.geometry.Offset(canvasWidth * 0.80f, canvasHeight * 0.72f)
                )
                for (pt in starPoints) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.18f * stagePulse),
                        radius = 4.dp.toPx(),
                        center = pt
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ====================================================
                // 1. MATCH MASTERS ARCADE GAME TOP HUD (CURRENCY / BADGES)
                // ====================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile Chip with 3D Ring Avatar
                    Surface(
                        onClick = {
                            soundManager.playUiTap()
                            onOpenProfile()
                        },
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1A1F3D),
                        border = BorderStroke(1.5.dp, Color(0xFF4C5896)),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                VectorAvatar(
                                    avatar = getAvatarById(profile.avatarId),
                                    size = 44.dp,
                                    showRing = true,
                                    ringColor = ArcadeGoldTop
                                )
                                Surface(
                                    color = Color(0xFFF59E0B),
                                    shape = CircleShape,
                                    border = BorderStroke(1.5.dp, Color.White),
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${profile.level}",
                                            color = Color.Black,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = profile.name.ifBlank { "Player 1" },
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        color = Color(0xFF10B981),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "PRO",
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Lvl ${profile.level} Master",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA5B4FC)
                                )
                            }
                        }
                    }

                    // Right Side: 3D Currency / Streak Badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Trophies / Stars 3D Pill Badge
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1F1D36),
                            border = BorderStroke(1.5.dp, ArcadeGoldTop.copy(alpha = 0.8f)),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("🏆", fontSize = 14.sp)
                                Text(
                                    text = "${stats.wins * 35 + 120}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = ArcadeGoldTop
                                )
                            }
                        }

                        // Hot Streak 3D Badge
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF2C151B),
                            border = BorderStroke(1.5.dp, BrandCoral.copy(alpha = 0.8f)),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("🔥", fontSize = 14.sp)
                                Text(
                                    text = "${stats.currentStreak}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = Color(0xFFFF6B6B)
                                )
                            }
                        }

                        // Settings Gear
                        IconButton(
                            onClick = {
                                soundManager.playUiTap()
                                onOpenSettings()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E2445))
                                .border(1.dp, Color(0xFF4C5896), CircleShape)
                                .testTag("settings_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Level XP Mini Progress Strip
                val currentXp = profile.lifetimeXp % 1000
                val nextXp = 1000
                val progressFraction = (currentXp.toFloat() / nextXp.toFloat()).coerceIn(0.1f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0F132E))
                        .border(1.dp, Color(0xFF2E376E), RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(ArcadeGoldTop, ArcadeGoldMid)
                                )
                            )
                    )
                }

                // ====================================================
                // 2. RESUME MATCH BANNER (IF ACTIVE)
                // ====================================================
                if (hasSavedGame) {
                    Surface(
                        onClick = {
                            soundManager.playUiTap()
                            hapticManager.performUiTap()
                            onResumeGame()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("resume_match_card"),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E143B),
                        border = BorderStroke(2.dp, ArcadePurpleTop),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF4C1D95).copy(alpha = 0.6f), Color(0xFF1E143B))
                                    )
                                )
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(ArcadePurpleTop, ArcadePurpleBottom)
                                            )
                                        )
                                        .border(1.5.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "CONTINUE BATTLE",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = Color.White,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "Your active board is waiting",
                                        fontSize = 12.sp,
                                        color = Color(0xFFD8B4FE)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = ArcadePurpleTop
                            )
                        }
                    }
                }

            // ====================================================
            // 3. DAILY QUEST BANNER (MATCH MASTERS ARCADE REWARD CARD)
            // ====================================================
            Surface(
                onClick = {
                    soundManager.playUiTap()
                    hapticManager.performUiTap()
                    showQuestDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_daily_quest_banner"),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF1F1838),
                border = BorderStroke(
                    1.8.dp,
                    if (isQuestClaimed) Color(0xFF10B981) else ArcadeGoldTop
                ),
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                if (isQuestClaimed) listOf(Color(0xFF064E3B), Color(0xFF132A26))
                                else listOf(Color(0xFF451A03), Color(0xFF1F1838))
                            )
                        )
                ) {
                    // Top gloss layer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.18f), Color.Transparent)
                                ),
                                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                            )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isQuestClaimed) Brush.verticalGradient(listOf(ArcadeGreenTop, ArcadeGreenBottom))
                                        else Brush.verticalGradient(listOf(ArcadeGoldTop, ArcadeGoldBottom))
                                    )
                                    .border(1.5.dp, Color.White, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isQuestClaimed) Icons.Default.CheckCircle else Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = if (isQuestClaimed) Color.White else Color(0xFF451A03),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isQuestClaimed) "QUEST COMPLETED" else "DAILY TACTICAL QUEST",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isQuestClaimed) Color(0xFF34D399) else ArcadeGoldTop,
                                        letterSpacing = 0.5.sp
                                    )
                                    Surface(
                                        color = if (isQuestClaimed) Color(0xFF10B981) else ArcadeGoldTop,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (isQuestClaimed) "CLAIMED" else "+150 XP",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isQuestClaimed) Color.White else Color(0xFF451A03),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isQuestClaimed) "Reward +150 XP added to player profile!" else "Connect 4 chips in any match • Tap to inspect",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                        Text(if (isQuestClaimed) "✅" else "🎯", fontSize = 22.sp)
                    }
                }
            }

            // ====================================================
            // 4. MAIN BATTLE ARENAS (WORLD-CLASS HERO GRAPHIC CARDS)
            // ====================================================
            Text(
                text = "Battle Arenas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            // ARENA 1: Quick Match (Online Arena)
            WorldClassHeroModeCard(
                title = "Live Arena",
                subtitle = "Fast online matchmaking vs worldwide players",
                badgeText = "ONLINE LIVE ⚡",
                actionText = "Battle Online",
                icon = Icons.Default.Public,
                gradient = QuickMatchGradient,
                accentGlow = CyberCyan,
                previewCols = 4,
                previewRows = 4,
                tag = "mode_online_multiplayer",
                onClick = {
                    soundManager.playUiTap()
                    onOpenMultiplayer()
                }
            )

            // Compact Multiplayer Room Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WorldClassCompactTile(
                    title = "Private Room",
                    subtitle = "Join with Room Code",
                    actionText = "Host / Join",
                    icon = Icons.Default.MeetingRoom,
                    accentColor = BrandTeal,
                    tag = "mode_private_room",
                    onClick = {
                        soundManager.playUiTap()
                        onOpenPrivateRoom()
                    }
                )
                WorldClassCompactTile(
                    title = "Nearby P2P",
                    subtitle = "Face-to-face Wireless",
                    actionText = "Connect",
                    icon = Icons.Default.Bluetooth,
                    accentColor = BrandAmber,
                    tag = "mode_nearby_p2p",
                    onClick = {
                        soundManager.playUiTap()
                        onOpenNearbyP2P()
                    }
                )
            }

            // ARENA 2: Solo vs AI
            WorldClassHeroModeCard(
                title = "Solo vs AI",
                subtitle = "5 personalities: Beginner to Ruthless Grandmaster",
                badgeText = "5 AI ROBO-BRAINS 🤖",
                actionText = "Start Battle",
                icon = Icons.Default.SmartToy,
                gradient = VsAiGradient,
                accentGlow = NeonViolet,
                previewCols = 4,
                previewRows = 4,
                tag = "mode_vs_ai",
                onClick = {
                    soundManager.playUiTap()
                    onStartGameMode(GameMode.VS_AI)
                }
            )

            // ====================================================
            // 5. MORE MODES: TABLETOP & PUZZLE SAGA
            // ====================================================
            Text(
                text = "More Ways to Play",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WorldClassCompactTile(
                    title = "Pass & Play",
                    subtitle = "2 players • 1 device",
                    actionText = "Pass & Play",
                    icon = Icons.Default.People,
                    accentColor = RubyRed,
                    tag = "mode_pass_and_play",
                    onClick = {
                        soundManager.playUiTap()
                        onStartGameMode(GameMode.PASS_AND_PLAY)
                    }
                )
                WorldClassCompactTile(
                    title = "Dice Mode",
                    subtitle = "Roll for chips allowance",
                    actionText = "Roll & Drop",
                    icon = Icons.Default.Casino,
                    accentColor = BrandPurple,
                    tag = "mode_dice_mode",
                    onClick = {
                        soundManager.playUiTap()
                        onStartGameMode(GameMode.DICE_MODE)
                    }
                )
            }

            // Campaign Adventure Saga Card
            WorldClassHeroModeCard(
                title = "Puzzle Campaign",
                subtitle = "20+ strategic puzzle stages with 3-star ratings",
                badgeText = "EPIC SAGA 🗺️",
                actionText = "Enter Campaign",
                icon = Icons.Default.Map,
                gradient = CampaignGradient,
                accentGlow = EmeraldGlow,
                previewCols = 5,
                previewRows = 5,
                tag = "mode_campaign",
                onClick = {
                    soundManager.playUiTap()
                    onOpenCampaign()
                }
            )

            // ====================================================
            // 6. COMMUNITY & TELEMETRY HUB
            // ====================================================
            Text(
                text = "Leaderboard & Stats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            WorldClassActionCard(
                title = "Global Leaderboard",
                subtitle = "International rankings, weekly podium & trophy rush",
                badgeText = "RANKS 🏆",
                icon = Icons.Default.EmojiEvents,
                badgeColor = GoldenTrophy,
                tag = "home_leaderboard_card",
                onClick = {
                    soundManager.playUiTap()
                    onOpenLeaderboard()
                }
            )

            WorldClassActionCard(
                title = "Visual Game Statistics",
                subtitle = "Win/loss telemetry & duration curves across difficulties",
                badgeText = "CHARTS 📊",
                icon = Icons.Default.BarChart,
                badgeColor = BrandIndigo,
                tag = "home_visual_statistics_card",
                onClick = {
                    soundManager.playUiTap()
                    onOpenStatistics()
                }
            )

            WorldClassActionCard(
                title = "Learn How To Play",
                subtitle = "Master the 4th-wall trap & double-cross sacrifice tactics",
                badgeText = "STRATEGY GUIDE 💡",
                icon = Icons.Default.Lightbulb,
                badgeColor = BrandTeal,
                tag = "tutorial_banner_card",
                onClick = {
                    soundManager.playUiTap()
                    showInteractiveTutorial = true
                }
            )

            Spacer(modifier = Modifier.height(64.dp))
        }

        // ====================================================
        // DAILY TACTICAL QUEST DETAIL DIALOG (MATCH MASTERS STYLE)
        // ====================================================
        if (showQuestDialog) {
            AlertDialog(
                onDismissRequest = { showQuestDialog = false },
                shape = RoundedCornerShape(26.dp),
                containerColor = Color(0xFF0F172A),
                icon = {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                if (isQuestClaimed) Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF10B981)))
                                else Brush.linearGradient(GoldenTrophyGradient)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isQuestClaimed) Icons.Default.CheckCircle else Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = if (isQuestClaimed) "Quest Completed!" else "Daily Tactical Quest",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isQuestClaimed)
                                "Awesome work! You claimed +150 XP for completing today's strategic challenge. New tactical quests refresh tomorrow!"
                            else
                                "Mission Objective: Connect 4 chips horizontally, vertically, or diagonally in any Solo vs AI, Live Online, or Pass & Play match.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, GoldenTrophy.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("🏆", fontSize = 20.sp)
                                    Column {
                                        Text("REWARD", fontSize = 10.sp, fontWeight = FontWeight.Black, color = GoldenTrophy)
                                        Text("+150 XP Bonus", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                    }
                                }
                                Surface(
                                    color = if (isQuestClaimed) Color(0xFF10B981).copy(alpha = 0.2f) else GoldenTrophy.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(100.dp)
                                ) {
                                    Text(
                                        text = if (isQuestClaimed) "COMPLETED" else "READY TO CLAIM",
                                        color = if (isQuestClaimed) Color(0xFF10B981) else GoldenTrophy,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!isQuestClaimed) {
                        Button(
                            onClick = {
                                soundManager.playVictory()
                                hapticManager.performVictory()
                                isQuestClaimed = true
                                showQuestDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldenTrophy),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("CLAIM REWARD (+150 XP)", fontWeight = FontWeight.Black, color = Color.Black)
                        }
                    } else {
                        Button(
                            onClick = {
                                soundManager.playUiTap()
                                showQuestDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("DONE", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    if (!isQuestClaimed) {
                        TextButton(onClick = { showQuestDialog = false }) {
                            Text("LATER", color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    }
}
}

@Composable
private fun WorldClassHeroModeCard(
    title: String,
    subtitle: String,
    badgeText: String,
    actionText: String,
    icon: ImageVector,
    gradient: List<Color>,
    accentGlow: Color,
    previewRows: Int,
    previewCols: Int,
    tag: String,
    onClick: () -> Unit
) {
    // Living arcade animation: diagonal shine sweep + pulsing accent halo
    val cardAnim = rememberInfiniteTransition(label = "heroCard")
    val shine by cardAnim.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shine"
    )
    val haloPulse by cardAnim.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo"
    )

    // 3D Embossed Match Masters Squircle Card
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        shadowElevation = 10.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            gradient.first(),
                            gradient.last()
                        )
                    )
                )
                // 3D bottom bevel shadow
                .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.45f)), RoundedCornerShape(26.dp))
        ) {
            // Animated diagonal shine sweep (glossy arcade highlight)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            start = Offset(shine * 900f, 0f),
                            end = Offset(shine * 900f + 260f, 320f)
                        )
                    )
            )

            // Card Gloss Top Reflection Layer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.28f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pill Badge with 3D Gloss
                    Surface(
                        color = Color.Black.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = ArcadeGoldTop,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = badgeText,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }

                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        letterSpacing = 0.5.sp
                    )

                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 3D MATCH MASTERS CANDY ACTION BUTTON
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ArcadeGoldBottom,
                        shadowElevation = 6.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Brush.verticalGradient(
                                        listOf(ArcadeGoldTop, ArcadeGoldMid)
                                    )
                                )
                                .border(1.2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = actionText.uppercase(),
                                    color = Color(0xFF451A03),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFF451A03),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Mini Board Graphic preview with 3D Ring Frame
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black.copy(alpha = 0.22f))
                        .border(2.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing accent glow halo behind the board
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        accentGlow.copy(alpha = haloPulse),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    MiniBoardDiagram(
                        rows = previewRows,
                        cols = previewCols,
                        accentColor = Color.White,
                        isHighlighted = true,
                        modifier = Modifier.size(68.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.WorldClassCompactTile(
    title: String,
    subtitle: String,
    actionText: String,
    icon: ImageVector,
    accentColor: Color,
    tag: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tilePress"
    )
    val iconGlow by rememberInfiniteTransition(label = "tileGlow").animateFloat(
        initialValue = 0.25f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconGlow"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 158.dp)
            .scale(pressScale)
            .testTag(tag),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF141A38),
        border = BorderStroke(1.8.dp, accentColor.copy(alpha = 0.7f)),
        shadowElevation = if (pressed) 2.dp else 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.22f),
                            Color(0xFF10142C)
                        )
                    )
                )
        ) {
            // Subtle top gloss
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                        ),
                        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 3D Chunky Icon Button Squircle
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(accentColor, accentColor.copy(alpha = 0.75f))
                            )
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.radialGradient(
                                    listOf(Color.White.copy(alpha = iconGlow), Color.Transparent)
                                )
                            )
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = actionText.uppercase(),
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldClassActionCard(
    title: String,
    subtitle: String,
    badgeText: String,
    icon: ImageVector,
    badgeColor: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0D1527),
        border = BorderStroke(1.2.dp, Color(0xFF1E2D4A)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(badgeColor.copy(alpha = 0.20f))
                        .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = badgeColor.copy(alpha = 0.20f),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = badgeColor,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF64748B)
            )
        }
    }
}
