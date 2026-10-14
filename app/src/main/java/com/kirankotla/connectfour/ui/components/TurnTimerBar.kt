package com.kirankotla.connectfour.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirankotla.connectfour.model.Player

@Composable
fun TurnTimerBar(
    activePlayer: Player,
    secondsRemaining: Int,
    totalSeconds: Int,
    isTimerEnabled: Boolean,
    onSelectDuration: (Int) -> Unit,
    modifier: Modifier = Modifier,
    highContrast: Boolean = false,
    reduceMotion: Boolean = false
) {
    if (!isTimerEnabled) return

    val progress = if (totalSeconds > 0) {
        (secondsRemaining.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val isCritical = secondsRemaining <= 3
    val isWarning = secondsRemaining in 4..6

    // Pulsing animation for critical seconds
    val infiniteTransition = rememberInfiniteTransition(label = "timerPulse")
    val pulseScale by if (isCritical && !reduceMotion) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val activePlayerColor = runCatching {
        Color(android.graphics.Color.parseColor(activePlayer.colorHex))
    }.getOrDefault(Color(0xFFFF5252))

    // Dynamic timer color
    val timerColor by animateColorAsState(
        targetValue = when {
            highContrast && isCritical -> Color(0xFFFF1744)
            highContrast -> Color(0xFF00E676)
            isCritical -> Color(0xFFDC2626)
            isWarning -> Color(0xFFF59E0B)
            else -> activePlayerColor
        },
        animationSpec = tween(300),
        label = "timerColor"
    )

    var showDurationMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("turn_timer_bar"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCritical) {
                timerColor.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCritical) 4.dp else 1.dp),
        border = if (isCritical) {
            androidx.compose.foundation.BorderStroke(1.5.dp, timerColor.copy(alpha = 0.8f))
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top Row: Active Player info + Countdown digits + Quick settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Active player tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(timerColor)
                    )
                    Text(
                        text = "${activePlayer.name}'s Turn",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isCritical) {
                        Surface(
                            color = timerColor,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.scale(pulseScale)
                        ) {
                            Text(
                                text = "HURRY!",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Countdown Number + Quick Timer Menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isCritical) Icons.Default.HourglassBottom else Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = timerColor,
                        modifier = Modifier
                            .size(18.dp)
                            .scale(if (isCritical) pulseScale else 1f)
                    )

                    Text(
                        text = "${secondsRemaining}s",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Monospace,
                        color = timerColor,
                        modifier = Modifier
                            .scale(if (isCritical) pulseScale else 1f)
                            .testTag("turn_timer_text")
                    )

                    Box {
                        IconButton(
                            onClick = { showDurationMenu = true },
                            modifier = Modifier.size(28.dp).testTag("timer_duration_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Timer Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showDurationMenu,
                            onDismissRequest = { showDurationMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("10s Blitz ⚡") },
                                onClick = {
                                    onSelectDuration(10)
                                    showDurationMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("15s Standard ⏱️") },
                                onClick = {
                                    onSelectDuration(15)
                                    showDurationMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("30s Strategic 🧠") },
                                onClick = {
                                    onSelectDuration(30)
                                    showDurationMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Unlimited (Off) ⏸️") },
                                onClick = {
                                    onSelectDuration(0)
                                    showDurationMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Smooth Progress Bar
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(
                    durationMillis = if (reduceMotion) 0 else 300,
                    easing = LinearEasing
                ),
                label = "timerProgress"
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = timerColor,
                trackColor = timerColor.copy(alpha = 0.15f)
            )
        }
    }
}
