package com.kirankotla.connectfour.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kirankotla.connectfour.data.*
import com.kirankotla.connectfour.ui.components.AvatarSelectionDialog
import com.kirankotla.connectfour.ui.components.VectorAvatar
import com.kirankotla.connectfour.ui.components.getAvatarById
import com.kirankotla.connectfour.ui.theme.*
import com.kirankotla.connectfour.util.SoundManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: PlayerProfileEntity?,
    stats: PlayerStatsEntity?,
    achievements: List<AchievementEntity>,
    summaries: List<MatchSummaryEntity>,
    onBack: () -> Unit,
    onOpenVisualStatistics: (() -> Unit)? = null,
    onUpdateAvatar: ((String) -> Unit)? = null,
    onOpenAuth: (() -> Unit)? = null,
    authProfile: com.kirankotla.connectfour.auth.PlayerProfile? = null,
    onEditProfileName: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val soundManager = remember(context) { SoundManager(context) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Overview" to Icons.Default.Person,
        "Records" to Icons.Default.Timeline,
        "Badges" to Icons.Default.EmojiEvents,
        "History" to Icons.Default.History
    )

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
                                text = "HALL OF FAME",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "Player Profile",
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
                        modifier = Modifier.testTag("profile_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (onOpenVisualStatistics != null) {
                        IconButton(
                            onClick = {
                                soundManager.playUiTap()
                                onOpenVisualStatistics()
                            },
                            modifier = Modifier.testTag("profile_open_charts_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = "Visual Charts",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
            ) {
                tabs.forEachIndexed { index, (title, icon) ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            soundManager.playUiTap()
                            selectedTab = index
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(title, fontWeight = if (selectedTab == index) FontWeight.Black else FontWeight.Medium)
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> OverviewTab(profile, stats, onUpdateAvatar, onOpenAuth, authProfile, onEditProfileName)
                1 -> RecordsTab(stats)
                2 -> AchievementsTab(achievements)
                3 -> MatchHistoryTab(summaries)
            }
        }
    }
}

@Composable
private fun OverviewTab(
    profile: PlayerProfileEntity?,
    stats: PlayerStatsEntity?,
    onUpdateAvatar: ((String) -> Unit)? = null,
    onOpenAuth: (() -> Unit)? = null,
    authProfile: com.kirankotla.connectfour.auth.PlayerProfile? = null,
    onEditProfileName: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val soundManager = remember(context) { SoundManager(context) }
    val p = profile ?: PlayerProfileEntity()
    val s = stats ?: PlayerStatsEntity()
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showNameEditDialog by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf(p.name) }

    if (showAvatarPicker) {
        AvatarSelectionDialog(
            currentAvatarId = p.avatarId,
            title = "Customize Profile Mascot",
            onAvatarSelected = { newId ->
                onUpdateAvatar?.invoke(newId)
            },
            onDismiss = { showAvatarPicker = false }
        )
    }

    if (showNameEditDialog) {
        AlertDialog(
            onDismissRequest = { showNameEditDialog = false },
            title = { Text("Edit Player Name", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editNameInput,
                    onValueChange = { editNameInput = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        soundManager.playUiTap()
                        if (editNameInput.isNotBlank()) {
                            onEditProfileName?.invoke(editNameInput.trim())
                        }
                        showNameEditDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNameEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ====================================================
        // HERO PLAYER CARD (WORLD-CLASS PROFILE BANNER)
        // ====================================================
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                shadowElevation = 6.dp,
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(HeroArenaGradient))
                        .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)), RoundedCornerShape(26.dp))
                        .padding(22.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Interactive Vector Avatar with Edit Ring
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    soundManager.playUiTap()
                                    showAvatarPicker = true
                                }
                                .testTag("profile_avatar_hero"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (authProfile?.photoUrl != null) {
                                AsyncImage(
                                    model = authProfile.photoUrl,
                                    contentDescription = "Google profile photo",
                                    modifier = Modifier
                                        .size(88.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, CyberCyan, CircleShape)
                                )
                            } else {
                                VectorAvatar(
                                    avatar = getAvatarById(p.avatarId),
                                    size = 88.dp,
                                    showRing = true,
                                    ringColor = CyberCyan
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(1.dp, BrandIndigo, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Avatar",
                                    tint = BrandIndigo,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Player Name (Click to edit)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable {
                                soundManager.playUiTap()
                                editNameInput = p.name
                                showNameEditDialog = true
                            }
                        ) {
                            Text(
                                text = p.name,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Name",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Surface(
                            color = Color.White.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(100.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "LEVEL ${p.level} • MASTER TACTICIAN 👑",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }

                        // XP Progress Bar
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Lifetime XP: ${p.lifetimeXp}",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Next Tier: ${(p.level) * 500} XP",
                                    color = GoldenTrophy,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            val progress = ((p.lifetimeXp % 500) / 500f).coerceIn(0.1f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.3f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(Brush.horizontalGradient(GoldenTrophyGradient))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Account access card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrandIndigo.copy(alpha = 0.12f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = BrandIndigo,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = if (authProfile != null && authProfile.uid != "local_player") authProfile.displayName else "Cloud Account & Sync",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (authProfile != null && authProfile.uid != "local_player") "Profile verified & synced" else "Sync trophies across Android devices",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            soundManager.playUiTap()
                            onOpenAuth?.invoke()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (authProfile != null && authProfile.uid != "local_player") "Account" else "Sign In",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Quick Highlights Section
        item {
            Text(
                text = "Combat Telemetry",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Battles Fought",
                    value = "${s.gamesCompleted}",
                    icon = Icons.Default.SportsEsports,
                    accentColor = BrandIndigo,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Total Victories",
                    value = "${s.wins}",
                    icon = Icons.Default.EmojiEvents,
                    accentColor = BrandTeal,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val winRate = if (s.gamesCompleted > 0) (s.wins * 100 / s.gamesCompleted) else 0
                StatCard(
                    title = "Victory Ratio",
                    value = "$winRate%",
                    icon = Icons.Default.PieChart,
                    accentColor = BrandAmber,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Max Win Streak",
                    value = "${s.bestStreak}",
                    icon = Icons.Default.LocalFireDepartment,
                    accentColor = BrandCoral,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(BrandPurple.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CropSquare,
                            contentDescription = null,
                            tint = BrandPurple,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Chips Dropped",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${s.boxesCaptured} Total Chips Placed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsTab(stats: PlayerStatsEntity?) {
    val s = stats ?: PlayerStatsEntity()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { RecordItem("Games Started", "${s.gamesStarted}", Icons.Default.PlayCircle) }
        item { RecordItem("Games Completed", "${s.gamesCompleted}", Icons.Default.CheckCircle) }
        item { RecordItem("Total Wins", "${s.wins}", Icons.Default.EmojiEvents) }
        item { RecordItem("Total Defeats", "${s.losses}", Icons.Default.Close) }
        item { RecordItem("Drawn Matches", "${s.draws}", Icons.Default.Balance) }
        item { RecordItem("Chips Dropped", "${s.boxesCaptured}", Icons.Default.Circle) }
        item { RecordItem("Active Win Streak", "${s.currentStreak}", Icons.Default.LocalFireDepartment) }
        item { RecordItem("All-time Best Streak", "${s.bestStreak}", Icons.Default.Star) }
        item { RecordItem("Largest Victory Margin", "${s.largestMargin} chips", Icons.AutoMirrored.Filled.TrendingUp) }
    }
}

@Composable
private fun AchievementsTab(unlockedEntities: List<AchievementEntity>) {
    val unlockedMap = unlockedEntities.associateBy { it.achievementId }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(AchievementCatalog.ALL_ACHIEVEMENTS, key = { it.id }) { def ->
            val entity = unlockedMap[def.id]
            val isUnlocked = entity?.isUnlocked == true
            val progress = entity?.progress ?: 0

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = if (isUnlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shadowElevation = if (isUnlocked) 3.dp else 0.dp,
                border = BorderStroke(
                    1.dp,
                    if (isUnlocked) BrandTeal.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val rarityColor = Color(def.rarityColorHex)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isUnlocked) rarityColor.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            )
                            .border(
                                2.dp,
                                if (isUnlocked) rarityColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUnlocked) {
                            Text(def.badgeIcon, fontSize = 24.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(def.title, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            Surface(
                                color = rarityColor.copy(alpha = if (isUnlocked) 0.2f else 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = def.rarityLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = rarityColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = def.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isUnlocked && def.targetProgress > 1) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (progress.toFloat() / def.targetProgress.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }

                    Surface(
                        color = BrandAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "+${def.xpReward} XP",
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandAmber,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchHistoryTab(summaries: List<MatchSummaryEntity>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (summaries.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HistoryEdu,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "No Matches Recorded Yet",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Play your first Quick Play or AI match to see battle records here.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(summaries) { match ->
                val isWin = match.result == "WIN"
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, if (isWin) BrandTeal.copy(alpha = 0.35f) else BrandCoral.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "${match.mode} • ${match.rows}x${match.cols} Grid",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Score: ${match.userScore} - ${match.opponentScore}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            color = if (isWin) BrandTeal.copy(alpha = 0.15f) else BrandCoral.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = match.result,
                                fontWeight = FontWeight.Black,
                                color = if (isWin) BrandTeal else BrandCoral,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RecordItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = label,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = value,
                fontWeight = FontWeight.Black,
                color = BrandIndigo,
                fontSize = 16.sp
            )
        }
    }
}
