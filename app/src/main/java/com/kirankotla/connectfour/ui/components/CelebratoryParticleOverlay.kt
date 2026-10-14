package com.kirankotla.connectfour.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kirankotla.connectfour.model.Player
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class ParticleShape {
    RECTANGLE,
    CIRCLE,
    STAR,
    DIAMOND
}

private data class ConfettiParticle(
    val startX: Float,        // Fraction of screen width
    val startY: Float,        // Fraction of screen height
    val vx: Float,            // Fraction of screen width per sec
    val vy: Float,            // Fraction of screen height per sec
    val gravity: Float,       // Fraction of screen height per sec^2
    val color: Color,
    val size: Float,          // Pixels
    val shape: ParticleShape,
    val rotationSpeed: Float, // Rad/sec
    val wobbleSpeed: Float,
    val wobbleAmplitude: Float,
    val delaySec: Float,
    val lifetimeSec: Float
)

@Composable
fun CelebratoryParticleOverlay(
    winnerPlayer: Player?,
    isDraw: Boolean,
    finalScores: List<Player>,
    onPlayAgain: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    stats: Map<Int, com.kirankotla.connectfour.rules.MoveInsights.PlayerMatchStats>? = null
) {
    var animationKey by remember { mutableIntStateOf(0) }

    // Loop animation from 0s to 7s
    val animTime = remember(animationKey) { Animatable(0f) }
    LaunchedEffect(animationKey) {
        animTime.animateTo(
            targetValue = 7.0f,
            animationSpec = tween(durationMillis = 7000, easing = LinearEasing)
        )
    }

    // Trophy bounce scale
    val trophyScale = remember(animationKey) { Animatable(0.2f) }
    LaunchedEffect(animationKey) {
        trophyScale.animateTo(
            targetValue = 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // Generate confetti particle set
    val particles = remember(animationKey, winnerPlayer) {
        generateConfettiParticles(winnerPlayer)
    }

    // Pre-create star path for reuse
    val starPath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("celebration_overlay")
    ) {
        // Full screen particle simulation canvas
        if (!reduceMotion) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("celebration_canvas")
            ) {
                val elapsed = animTime.value
                val w = size.width
                val h = size.height

                // Festive background ambient victory glow
                val glowAlpha = (1f - (elapsed / 7f)).coerceIn(0f, 0.35f)
                val glowColor = winnerPlayer?.colorHex?.let {
                    try {
                        Color(android.graphics.Color.parseColor(it))
                    } catch (_: Exception) {
                        Color(0xFFFFD700)
                    }
                } ?: Color(0xFFFFD700)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = glowAlpha),
                            Color.Transparent
                        ),
                        center = Offset(w / 2f, h * 0.4f),
                        radius = minOf(w, h) * 0.8f
                    ),
                    radius = minOf(w, h) * 0.8f,
                    center = Offset(w / 2f, h * 0.4f)
                )

                // Draw each particle
                particles.forEach { p ->
                    if (elapsed >= p.delaySec) {
                        val activeT = elapsed - p.delaySec
                        if (activeT < p.lifetimeSec) {
                            // Compute position
                            val x = (p.startX + p.vx * activeT + p.wobbleAmplitude * sin(p.wobbleSpeed * activeT)) * w
                            val y = (p.startY + p.vy * activeT + 0.5f * p.gravity * activeT * activeT) * h

                            // Fade out towards end of lifetime
                            val lifeFraction = activeT / p.lifetimeSec
                            val alpha = if (lifeFraction > 0.8f) (1f - lifeFraction) * 5f else 1f
                            val pColor = p.color.copy(alpha = alpha.coerceIn(0f, 1f))

                            val angleDegrees = (p.rotationSpeed * activeT * 57.2958f) % 360f
                            val flip3D = cos(p.wobbleSpeed * activeT)

                            rotate(degrees = angleDegrees, pivot = Offset(x, y)) {
                                when (p.shape) {
                                    ParticleShape.RECTANGLE -> {
                                        val ribbonW = p.size * abs(flip3D).coerceAtLeast(0.15f)
                                        val ribbonH = p.size * 1.8f
                                        drawRect(
                                            color = pColor,
                                            topLeft = Offset(x - ribbonW / 2f, y - ribbonH / 2f),
                                            size = Size(ribbonW, ribbonH)
                                        )
                                    }
                                    ParticleShape.CIRCLE -> {
                                        drawCircle(
                                            color = pColor,
                                            radius = p.size * 0.55f,
                                            center = Offset(x, y)
                                        )
                                    }
                                    ParticleShape.DIAMOND -> {
                                        val dPath = Path().apply {
                                            moveTo(x, y - p.size)
                                            lineTo(x + p.size * 0.65f, y)
                                            lineTo(x, y + p.size)
                                            lineTo(x - p.size * 0.65f, y)
                                            close()
                                        }
                                        drawPath(dPath, color = pColor)
                                    }
                                    ParticleShape.STAR -> {
                                        val sPath = Path().apply {
                                            val step = (Math.PI / 5.0).toFloat()
                                            val outer = p.size * 0.9f
                                            val inner = outer * 0.45f
                                            for (i in 0 until 10) {
                                                val r = if (i % 2 == 0) outer else inner
                                                val a = i * step - (Math.PI / 2.0).toFloat()
                                                val px = x + r * cos(a)
                                                val py = y + r * sin(a)
                                                if (i == 0) moveTo(px, py) else lineTo(px, py)
                                            }
                                            close()
                                        }
                                        drawPath(sPath, color = pColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Celebratory Dialog / Modal Overlay
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Trophy / Crown Icon with bounce animation
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .scale(if (reduceMotion) 1f else trophyScale.value)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFFD54F),
                                        Color(0xFFFFA000)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Victory Trophy",
                            tint = Color(0xFF5D4037),
                            modifier = Modifier.size(46.dp)
                        )
                    }

                    // Title
                    val title = when {
                        isDraw -> "MATCH DRAW!"
                        winnerPlayer != null -> "${winnerPlayer.name.uppercase()} WINS!"
                        else -> "MATCH COMPLETE!"
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isDraw) "All slots filled! Perfectly tied!" else "Connect 4 achieved! Outstanding strategy!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Score summary cards
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        finalScores.forEach { player ->
                            val isWinner = player.id == winnerPlayer?.id
                            val cardBg = if (isWinner) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                            val textColor = if (isWinner) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = cardBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (isWinner) {
                                                Text("👑", fontSize = 16.sp)
                                            }
                                            Text(
                                                text = player.name,
                                                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Medium,
                                                color = textColor
                                            )
                                        }

                                        Text(
                                            text = if (isDraw) "🤝 Tied" else if (isWinner) "🏆 Winner" else "Runner-up",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = textColor
                                        )
                                    }

                                    val pStats = stats?.get(player.id)
                                    if (pStats != null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Text(
                                                text = "🎯 Chips: ${pStats.chipsPlaced}",
                                                fontSize = 11.sp,
                                                color = textColor.copy(alpha = 0.85f),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "🔥 Streak: ${pStats.longestStreak} in a row",
                                                fontSize = 11.sp,
                                                color = textColor.copy(alpha = 0.85f),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Re-trigger Confetti Button (Playful interaction)
                    if (!reduceMotion) {
                        TextButton(
                            onClick = { animationKey++ },
                            modifier = Modifier.testTag("retrigger_confetti_button")
                        ) {
                            Icon(Icons.Default.Celebration, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Blast More Confetti! 🎉")
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBackToHome,
                            modifier = Modifier
                                .weight(1.1f)
                                .height(48.dp)
                                .testTag("home_button"),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Home", maxLines = 1)
                        }

                        Button(
                            onClick = onPlayAgain,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp)
                                .testTag("play_again_button"),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play Again", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun generateConfettiParticles(winner: Player?): List<ConfettiParticle> {
    val rng = Random(42)
    val list = mutableListOf<ConfettiParticle>()

    val festiveColors = mutableListOf(
        Color(0xFFFFD700), // Gold
        Color(0xFFFF5722), // Orange/Red
        Color(0xFF10B981), // Emerald
        Color(0xFF3B82F6), // Blue
        Color(0xFFA855F7), // Purple
        Color(0xFFEC4899), // Pink
        Color(0xFF06B6D4), // Cyan
        Color(0xFFFF9800)  // Amber
    )

    winner?.colorHex?.let {
        try {
            festiveColors.add(Color(android.graphics.Color.parseColor(it)))
        } catch (_: Exception) {}
    }

    val shapes = ParticleShape.values()

    // 1. Left Cannon (Bursts upward and rightward)
    for (i in 0 until 45) {
        val speed = 0.85f + rng.nextFloat() * 0.65f
        val angleRad = (rng.nextFloat() * 40f + 35f) * (Math.PI / 180f).toFloat() // 35 to 75 deg
        val vx = speed * cos(angleRad)
        val vy = -speed * sin(angleRad)

        list.add(
            ConfettiParticle(
                startX = 0.05f + rng.nextFloat() * 0.08f,
                startY = 0.85f + rng.nextFloat() * 0.1f,
                vx = vx,
                vy = vy,
                gravity = 1.15f + rng.nextFloat() * 0.4f,
                color = festiveColors.random(rng),
                size = 14f + rng.nextFloat() * 14f,
                shape = shapes.random(rng),
                rotationSpeed = (rng.nextFloat() - 0.5f) * 12f,
                wobbleSpeed = 4f + rng.nextFloat() * 6f,
                wobbleAmplitude = 0.02f + rng.nextFloat() * 0.03f,
                delaySec = rng.nextFloat() * 0.3f,
                lifetimeSec = 4.5f + rng.nextFloat() * 1.5f
            )
        )
    }

    // 2. Right Cannon (Bursts upward and leftward)
    for (i in 0 until 45) {
        val speed = 0.85f + rng.nextFloat() * 0.65f
        val angleRad = (rng.nextFloat() * 40f + 35f) * (Math.PI / 180f).toFloat()
        val vx = -speed * cos(angleRad)
        val vy = -speed * sin(angleRad)

        list.add(
            ConfettiParticle(
                startX = 0.87f + rng.nextFloat() * 0.08f,
                startY = 0.85f + rng.nextFloat() * 0.1f,
                vx = vx,
                vy = vy,
                gravity = 1.15f + rng.nextFloat() * 0.4f,
                color = festiveColors.random(rng),
                size = 14f + rng.nextFloat() * 14f,
                shape = shapes.random(rng),
                rotationSpeed = (rng.nextFloat() - 0.5f) * 12f,
                wobbleSpeed = 4f + rng.nextFloat() * 6f,
                wobbleAmplitude = 0.02f + rng.nextFloat() * 0.03f,
                delaySec = rng.nextFloat() * 0.3f,
                lifetimeSec = 4.5f + rng.nextFloat() * 1.5f
            )
        )
    }

    // 3. Sky Fluttering Confetti Shower (Raining down from top)
    for (i in 0 until 60) {
        list.add(
            ConfettiParticle(
                startX = rng.nextFloat(),
                startY = -0.05f - rng.nextFloat() * 0.15f,
                vx = (rng.nextFloat() - 0.5f) * 0.18f,
                vy = 0.18f + rng.nextFloat() * 0.28f,
                gravity = 0.25f + rng.nextFloat() * 0.2f,
                color = festiveColors.random(rng),
                size = 12f + rng.nextFloat() * 12f,
                shape = shapes.random(rng),
                rotationSpeed = (rng.nextFloat() - 0.5f) * 10f,
                wobbleSpeed = 3f + rng.nextFloat() * 5f,
                wobbleAmplitude = 0.03f + rng.nextFloat() * 0.04f,
                delaySec = 0.2f + rng.nextFloat() * 1.8f,
                lifetimeSec = 4.8f + rng.nextFloat() * 1.8f
            )
        )
    }

    return list
}
