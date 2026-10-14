package com.kirankotla.connectfour.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirankotla.connectfour.ui.components.VectorAvatar
import com.kirankotla.connectfour.ui.components.MiniBoardDiagram
import com.kirankotla.connectfour.ui.components.getAvatarById
import com.kirankotla.connectfour.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun ConnectFourLoadingScreen() {
    val transition = rememberInfiniteTransition(label = "loadingArena")
    val boardScale by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "boardScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(208.dp)
                    .scale(boardScale),
                contentAlignment = Alignment.Center
            ) {
                LoadingBoardPreview(modifier = Modifier.fillMaxSize())
                VectorAvatar(
                    avatar = getAvatarById("mavi"),
                    size = 54.dp,
                    ringColor = BrandCyan,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
                VectorAvatar(
                    avatar = getAvatarById("ember"),
                    size = 54.dp,
                    ringColor = BrandAmber,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
            Text(
                text = "CONNECT FOUR",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                letterSpacing = 3.sp
            )
            CircularProgressIndicator(color = BrandIndigoLight, strokeWidth = 3.dp)
            Text(
                text = "Loading your games\u2026",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LoadingBoardPreview(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF16243D),
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.22f))
    ) {
        Canvas(modifier = Modifier.padding(20.dp).fillMaxSize()) {
            val gridSize = minOf(size.width, size.height)
            val step = gridSize / 3.2f
            val origin = Offset(size.width * 0.1f, size.height * 0.1f)
            val cyan = Color(0xFF19D3E6)
            val amber = Color(0xFFFFC857)

            for (row in 0..3) {
                for (col in 0..3) {
                    val point = Offset(origin.x + col * step, origin.y + row * step)
                    drawCircle(Color.Black.copy(alpha = 0.35f), radius = 8.dp.toPx(), center = point + Offset(0f, 4.dp.toPx()))
                    drawCircle(Color.White, radius = 6.dp.toPx(), center = point)
                }
            }

            fun line(start: Offset, end: Offset, color: Color) {
                drawLine(color.copy(alpha = 0.22f), start, end, strokeWidth = 14.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(color, start, end, strokeWidth = 8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.45f), start, end, strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }

            line(Offset(origin.x, origin.y + step), Offset(origin.x + step, origin.y + step), cyan)
            line(Offset(origin.x + step, origin.y + step * 2), Offset(origin.x + step * 2, origin.y + step * 2), amber)
            line(Offset(origin.x + step * 2, origin.y), Offset(origin.x + step * 2, origin.y + step), cyan)
        }
    }
}

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1300)
        onSplashComplete()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splashPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative background grid dots
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 48.dp.toPx()
            val cols = (size.width / step).toInt()
            val rows = (size.height / step).toInt()
            for (r in 0..rows) {
                for (c in 0..cols) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = 2.5.dp.toPx(),
                        center = Offset(c * step + (size.width % step) / 2f, r * step + (size.height % step) / 2f)
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.scale(pulseScale)
        ) {
            // Reuse the playable board motif so splash and game surfaces share one identity.
            Surface(
                color = Color(0xFF101A36),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MiniBoardDiagram(
                        rows = 4,
                        cols = 4,
                        accentColor = CyberCyan,
                        isHighlighted = true,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Text(
                text = "CONNECT FOUR",
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 4.sp
            )

            Surface(
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "4-IN-A-ROW ARENA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BrandIndigoLight,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
