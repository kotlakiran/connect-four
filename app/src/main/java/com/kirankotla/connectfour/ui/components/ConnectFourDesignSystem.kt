package com.kirankotla.connectfour.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirankotla.connectfour.util.HapticManager
import com.kirankotla.connectfour.util.SoundManager

/**
 * Material 3 Unified Surface Card with standard 20dp radius and subtle tonal border.
 */
@Composable
fun ConnectFourCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = 1.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Standard 54dp Primary CTA button complying with thumb reachability and 48dp+ target rules.
 */
@Composable
fun ConnectFourPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tag: String = "connectfour_primary_button"
) {
    val context = LocalContext.current
    val soundManager = SoundManager(context)
    val hapticManager = HapticManager(context)

    Button(
        onClick = {
            soundManager.playUiTap()
            hapticManager.performUiTap()
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * Standard 48dp Tonal Secondary Action Button.
 */
@Composable
fun ConnectFourTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tag: String = "connectfour_tonal_button"
) {
    val context = LocalContext.current
    val soundManager = SoundManager(context)

    FilledTonalButton(
        onClick = {
            soundManager.playUiTap()
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(tag),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * Standard 48dp Outlined Button for secondary non-intrusive actions.
 */
@Composable
fun ConnectFourOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tag: String = "connectfour_outlined_button"
) {
    val context = LocalContext.current
    val soundManager = SoundManager(context)

    OutlinedButton(
        onClick = {
            soundManager.playUiTap()
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(tag),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * Material 3 Status / Rank Pill Badge.
 */
@Composable
fun ConnectFourStatusBadge(
    text: String,
    badgeColor: Color,
    modifier: Modifier = Modifier,
    textColor: Color = badgeColor
) {
    Surface(
        modifier = modifier,
        color = badgeColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Modern Segmented Button Row for Grid sizes and AI Difficulties.
 */
@Composable
fun <T> ConnectFourSegmentedRow(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundManager = SoundManager(context)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable {
                            if (!isSelected) {
                                soundManager.playUiTap()
                                onItemSelected(item)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * World-Class Gaming Hero Card with vibrant gradient, layered depth, and specular glow.
 */
@Composable
fun GamingHeroCard(
    modifier: Modifier = Modifier,
    gradient: List<Color>,
    onClick: (() -> Unit)? = null,
    borderAlpha: Float = 0.25f,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Surface(
        modifier = clickableModifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 6.dp,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradient))
                .border(
                    BorderStroke(1.5.dp, Color.White.copy(alpha = borderAlpha)),
                    RoundedCornerShape(24.dp)
                )
                .padding(contentPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

/**
 * World-Class Gaming Pill Badge with vibrant color background and micro icon support.
 */
@Composable
fun GamingPillBadge(
    text: String,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    textColor: Color = Color.White
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(100.dp)),
        shape = RoundedCornerShape(100.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(gradient))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * World-Class Tactile Gradient CTA Button with 56dp height and glowing elevation.
 */
@Composable
fun TactileGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    gradient: List<Color> = listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6)),
    enabled: Boolean = true,
    tag: String = "tactile_gradient_button"
) {
    val context = LocalContext.current
    val soundManager = SoundManager(context)
    val hapticManager = HapticManager(context)

    Surface(
        onClick = {
            if (enabled) {
                soundManager.playUiTap()
                hapticManager.performUiTap()
                onClick()
            }
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .testTag(tag),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = if (enabled) 6.dp else 0.dp,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (enabled) Brush.horizontalGradient(gradient)
                    else Brush.horizontalGradient(listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.4f)))
                )
                .border(
                    BorderStroke(1.dp, if (enabled) Color.White.copy(alpha = 0.3f) else Color.Transparent),
                    RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = text,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * Visual Mini-Grid Diagram showing dots, edges, and captured squares for board size preview.
 */
@Composable
fun MiniBoardDiagram(
    rows: Int,
    cols: Int,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF00E5FF),
    isHighlighted: Boolean = false
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val pad = size.width * 0.08f
        val gridW = size.width - pad * 2f
        val gridH = size.height - pad * 2f
        val stepX = gridW / cols.toFloat()
        val stepY = gridH / rows.toFloat()
        val radius = minOf(stepX, stepY) * 0.36f

        // Draw chassis plate
        drawRoundRect(
            color = if (isHighlighted) Color(0xFF1E3A8A) else Color(0xFF111D3B),
            topLeft = androidx.compose.ui.geometry.Offset(pad - 2f, pad - 2f),
            size = androidx.compose.ui.geometry.Size(gridW + 4f, gridH + 4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
        )

        // Draw circular slots
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cx = pad + c * stepX + stepX / 2f
                val cy = pad + r * stepY + stepY / 2f
                drawCircle(
                    color = if (r == rows - 1 && c == cols / 2) accentColor else Color(0xFF060B18),
                    radius = radius,
                    center = androidx.compose.ui.geometry.Offset(cx, cy)
                )
            }
        }
    }
}
