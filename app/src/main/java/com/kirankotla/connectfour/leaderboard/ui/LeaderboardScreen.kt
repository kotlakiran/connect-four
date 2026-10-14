package com.kirankotla.connectfour.leaderboard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirankotla.connectfour.auth.PlayerProfile
import com.kirankotla.connectfour.leaderboard.model.LeaderboardEntry
import com.kirankotla.connectfour.leaderboard.model.LeaderboardTimeframe
import com.kirankotla.connectfour.leaderboard.repository.LeaderboardRepository
import com.kirankotla.connectfour.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    currentProfile: PlayerProfile,
    repository: LeaderboardRepository = remember { LeaderboardRepository() },
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTimeframe by remember { mutableStateOf(LeaderboardTimeframe.ALL_TIME) }
    var showOnlyFriends by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedTimeframe, showOnlyFriends) {
        isLoading = true
        val list = repository.getLeaderboard(
            timeframe = selectedTimeframe,
            currentUid = currentProfile.uid,
            currentUserScore = currentProfile.score,
            currentUserName = currentProfile.displayName,
            currentUserAvatar = currentProfile.avatarId
        )
        entries = if (showOnlyFriends) {
            list.filter { it.isCurrentUser || it.displayName in listOf("Alex", "Priya", "Rahul", "Marcus", "Elena", "Maya", "Sora") }
        } else {
            list
        }
        isLoading = false
    }

    val currentUserEntry = entries.find { it.isCurrentUser }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Leaderboard",
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
                .testTag("leaderboard_screen")
        ) {
            // Timeframe Tabs
            SecondaryTabRow(
                selectedTabIndex = selectedTimeframe.ordinal,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                LeaderboardTimeframe.values().forEach { timeframe ->
                    Tab(
                        selected = selectedTimeframe == timeframe,
                        onClick = { selectedTimeframe = timeframe },
                        text = {
                            Text(
                                text = timeframe.displayName,
                                fontWeight = if (selectedTimeframe == timeframe) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Filter Toggle Row (Global vs Friends)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !showOnlyFriends,
                    onClick = { showOnlyFriends = false },
                    label = { Text("Global") },
                    leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = showOnlyFriends,
                    onClick = { showOnlyFriends = true },
                    label = { Text("Friends") },
                    leadingIcon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandIndigo)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(entries) { entry ->
                        LeaderboardRow(entry = entry)
                    }
                }
            }

            // Sticky Current Player Banner at Bottom
            if (currentUserEntry != null) {
                Surface(
                    color = BrandIndigo.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = BrandIndigo,
                                shape = CircleShape,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "#${currentUserEntry.rank}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Your Standing",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currentUserEntry.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Text(
                            text = "${currentUserEntry.score} pts",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = BrandIndigo
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry) {
    val isTop3 = entry.rank in 1..3
    val rankBadgeColor = when (entry.rank) {
        1 -> GoldenTrophy
        2 -> Color(0xFF94A3B8)
        3 -> Color(0xFFD97706)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val cardBorder = when (entry.rank) {
        1 -> BorderStroke(1.5.dp, GoldenTrophy.copy(alpha = 0.6f))
        2 -> BorderStroke(1.dp, Color(0xFF94A3B8).copy(alpha = 0.5f))
        3 -> BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.5f))
        else -> if (entry.isCurrentUser) BorderStroke(1.5.dp, BrandIndigo)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (entry.isCurrentUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.surface,
        shadowElevation = if (isTop3) 3.dp else 1.dp,
        border = cardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Rank Badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(rankBadgeColor.copy(alpha = if (isTop3) 0.2f else 1f))
                        .border(1.dp, rankBadgeColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isTop3) {
                        Text(
                            text = when (entry.rank) {
                                1 -> "🥇"
                                2 -> "🥈"
                                else -> "🥉"
                            },
                            fontSize = 18.sp
                        )
                    } else {
                        Text(
                            text = "${entry.rank}",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Avatar and Display Name
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = entry.displayName,
                            fontWeight = if (entry.isCurrentUser) FontWeight.Black else FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (entry.isCurrentUser) {
                            Surface(
                                color = BrandIndigo,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "YOU",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${entry.wins} wins",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Score Pill
            Surface(
                color = if (isTop3) GoldenTrophy.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "%,d pts".format(entry.score),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = if (isTop3) GoldenTrophy else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
