package com.kirankotla.connectfour.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class AvatarInfo(
    val id: String,
    val name: String,
    val title: String,
    val category: String, // Creatures, Cyber, Mythic, Cosmic
    val primaryColor: Color,
    val secondaryColor: Color,
    val badgeEmoji: String
)

val ALL_AVATARS: List<AvatarInfo> = listOf(
    AvatarInfo(
        id = "piko",
        name = "Piko the Fox",
        title = "Tactical Tactician",
        category = "Creatures",
        primaryColor = Color(0xFFFF6B6B),
        secondaryColor = Color(0xFFFF8E53),
        badgeEmoji = "🦊"
    ),
    AvatarInfo(
        id = "mavi",
        name = "Mavi the Dolphin",
        title = "Swift Flow",
        category = "Creatures",
        primaryColor = Color(0xFF06B6D4),
        secondaryColor = Color(0xFF3B82F6),
        badgeEmoji = "🐬"
    ),
    AvatarInfo(
        id = "bolt",
        name = "Bolt Cyber Bunny",
        title = "Electric Strategist",
        category = "Cyber",
        primaryColor = Color(0xFF8B5CF6),
        secondaryColor = Color(0xFFEC4899),
        badgeEmoji = "⚡"
    ),
    AvatarInfo(
        id = "ember",
        name = "Ember the Dragon",
        title = "Blazing Aggressor",
        category = "Mythic",
        primaryColor = Color(0xFFEF4444),
        secondaryColor = Color(0xFFF59E0B),
        badgeEmoji = "🐲"
    ),
    AvatarInfo(
        id = "flora",
        name = "Flora Forest Sprite",
        title = "Serene Guardian",
        category = "Mythic",
        primaryColor = Color(0xFF10B981),
        secondaryColor = Color(0xFF84CC16),
        badgeEmoji = "🌸"
    ),
    AvatarInfo(
        id = "luna",
        name = "Luna Night Owl",
        title = "Wise Mind",
        category = "Creatures",
        primaryColor = Color(0xFF6366F1),
        secondaryColor = Color(0xFF14B8A6),
        badgeEmoji = "🦉"
    ),
    AvatarInfo(
        id = "cosmo",
        name = "Cosmo Star Voyager",
        title = "Cosmic Explorer",
        category = "Cosmic",
        primaryColor = Color(0xFF38BDF8),
        secondaryColor = Color(0xFF6366F1),
        badgeEmoji = "🚀"
    ),
    AvatarInfo(
        id = "titan",
        name = "Titan Golem",
        title = "Steadfast Defender",
        category = "Mythic",
        primaryColor = Color(0xFFF59E0B),
        secondaryColor = Color(0xFF78716C),
        badgeEmoji = "🗿"
    )
)

fun getAvatarById(id: String): AvatarInfo {
    return ALL_AVATARS.find { it.id.equals(id, ignoreCase = true) } ?: ALL_AVATARS.first()
}

/**
 * Renders a crisp vector-based avatar with custom geometric art,
 * radiant gradients, glowing rings, and mascot badge.
 */
@Composable
fun VectorAvatar(
    avatar: AvatarInfo,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier,
    showRing: Boolean = true,
    ringColor: Color? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .testTag("vector_avatar_${avatar.id}"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = this.size.width
            val height = this.size.height
            val center = Offset(width / 2f, height / 2f)
            val radius = minOf(width, height) / 2f

            // 1. Outer Glow / Aura Ring
            if (showRing) {
                val ringPrimary = ringColor ?: avatar.primaryColor
                val ringSecondary = ringColor?.copy(alpha = 0.7f) ?: avatar.secondaryColor
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            ringPrimary.copy(alpha = 0.6f),
                            ringSecondary.copy(alpha = 0.9f),
                            ringPrimary.copy(alpha = 0.6f)
                        ),
                        center = center
                    ),
                    radius = radius,
                    style = Stroke(width = maxOf(2.5f, radius * 0.10f))
                )
            }

            // 2. Inner Radial Gradient Base
            val innerRadius = if (showRing) radius * 0.88f else radius
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        avatar.primaryColor.copy(alpha = 0.85f),
                        avatar.secondaryColor.copy(alpha = 0.95f),
                        Color(0xFF0F172A)
                    ),
                    center = Offset(center.x - innerRadius * 0.25f, center.y - innerRadius * 0.25f),
                    radius = innerRadius * 1.3f
                ),
                radius = innerRadius
            )

            // 3. Vector Geometric Accents based on Avatar ID
            when (avatar.id) {
                "piko" -> { // Fox ears
                    val earPath = Path().apply {
                        moveTo(center.x - innerRadius * 0.6f, center.y - innerRadius * 0.1f)
                        lineTo(center.x - innerRadius * 0.7f, center.y - innerRadius * 0.85f)
                        lineTo(center.x - innerRadius * 0.2f, center.y - innerRadius * 0.5f)
                        close()

                        moveTo(center.x + innerRadius * 0.6f, center.y - innerRadius * 0.1f)
                        lineTo(center.x + innerRadius * 0.7f, center.y - innerRadius * 0.85f)
                        lineTo(center.x + innerRadius * 0.2f, center.y - innerRadius * 0.5f)
                        close()
                    }
                    drawPath(earPath, color = Color.White.copy(alpha = 0.35f))
                }
                "bolt" -> { // Lightning bolts
                    val boltPath = Path().apply {
                        moveTo(center.x - innerRadius * 0.3f, center.y - innerRadius * 0.6f)
                        lineTo(center.x + innerRadius * 0.1f, center.y - innerRadius * 0.1f)
                        lineTo(center.x - innerRadius * 0.05f, center.y - innerRadius * 0.1f)
                        lineTo(center.x + innerRadius * 0.3f, center.y + innerRadius * 0.5f)
                        lineTo(center.x, center.y)
                        lineTo(center.x + innerRadius * 0.15f, center.y)
                        close()
                    }
                    drawPath(boltPath, color = Color.Yellow.copy(alpha = 0.45f))
                }
                "mavi" -> { // Waves
                    drawArc(
                        color = Color.White.copy(alpha = 0.25f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(center.x - innerRadius * 0.7f, center.y + innerRadius * 0.1f),
                        size = Size(innerRadius * 1.4f, innerRadius * 0.6f),
                        style = Stroke(width = maxOf(2f, innerRadius * 0.08f))
                    )
                }
                "cosmo" -> { // Planetary Orbit Ring
                    drawOval(
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(center.x - innerRadius * 0.85f, center.y - innerRadius * 0.3f),
                        size = Size(innerRadius * 1.7f, innerRadius * 0.6f),
                        style = Stroke(width = maxOf(1.5f, innerRadius * 0.06f))
                    )
                }
                else -> { // Subtle diamond star
                    val starPath = Path().apply {
                        moveTo(center.x, center.y - innerRadius * 0.6f)
                        lineTo(center.x + innerRadius * 0.2f, center.y)
                        lineTo(center.x, center.y + innerRadius * 0.6f)
                        lineTo(center.x - innerRadius * 0.2f, center.y)
                        close()
                    }
                    drawPath(starPath, color = Color.White.copy(alpha = 0.2f))
                }
            }

            // 4. Gloss Highlight
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(center.x - innerRadius * 0.3f, center.y - innerRadius * 0.4f),
                    radius = innerRadius * 0.6f
                ),
                topLeft = Offset(center.x - innerRadius * 0.6f, center.y - innerRadius * 0.7f),
                size = Size(innerRadius * 1.2f, innerRadius * 0.7f)
            )
        }

        // Mascot Badge Center Emoji
        Text(
            text = avatar.badgeEmoji,
            fontSize = (size.value * 0.46f).sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Player Profile Card with editable avatar preview.
 */
@Composable
fun PlayerAvatarSelectorCard(
    avatarId: String,
    playerName: String,
    label: String,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val avatar = remember(avatarId) { getAvatarById(avatarId) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onOpenPicker() }
            .testTag("avatar_card_${avatar.id}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.5.dp, avatar.primaryColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                VectorAvatar(
                    avatar = avatar,
                    size = 52.dp
                )

                Column {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = avatar.primaryColor
                    )
                    Text(
                        text = playerName,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${avatar.name} • ${avatar.title}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onOpenPicker,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(avatar.primaryColor.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Change Avatar",
                    tint = avatar.primaryColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Comprehensive interactive modal dialog for selecting player avatars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarSelectionDialog(
    currentAvatarId: String,
    title: String = "Select Your Avatar",
    onAvatarSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by remember { mutableStateOf(currentAvatarId) }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Creatures", "Cyber", "Mythic", "Cosmic")

    val filteredAvatars = remember(selectedCategory) {
        if (selectedCategory == "All") ALL_AVATARS
        else ALL_AVATARS.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    val activeAvatar = remember(selectedId) { getAvatarById(selectedId) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(16.dp)
                .testTag("avatar_selection_dialog"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .testTag("avatar_dialog_card"),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Choose a vector character to represent you",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    // Active Avatar Spotlight Banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = activeAvatar.primaryColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.5.dp, activeAvatar.primaryColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            VectorAvatar(
                                avatar = activeAvatar,
                                size = 64.dp
                            )

                            Column {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = activeAvatar.primaryColor
                                ) {
                                    Text(
                                        text = activeAvatar.category.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = activeAvatar.name,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = activeAvatar.primaryColor
                                )
                                Text(
                                    text = activeAvatar.title,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Category Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }

                    // Grid of Colorful Vector Avatars
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredAvatars) { item ->
                            val isSelected = selectedId == item.id
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { selectedId = item.id }
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) item.primaryColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .testTag("avatar_option_${item.id}"),
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) item.primaryColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    VectorAvatar(
                                        avatar = item,
                                        size = 48.dp,
                                        showRing = isSelected
                                    )

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(item.primaryColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Confirm Selection Button
                    Button(
                        onClick = {
                            onAvatarSelected(selectedId)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("confirm_avatar_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = activeAvatar.primaryColor)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "USE ${activeAvatar.name.uppercase()}",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}
