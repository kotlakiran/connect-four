package com.kirankotla.connectfour.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.EmojiEvents
import coil.compose.AsyncImage
import com.kirankotla.connectfour.ui.components.ConnectFourEmblem
import com.kirankotla.connectfour.ui.components.MiniBoardDiagram
import com.kirankotla.connectfour.ui.theme.*

@Composable
fun OnboardingScreen(
    initialName: String? = null,
    profilePhotoUrl: String? = null,
    onFinishOnboarding: (name: String, age: Int?) -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var playerName by remember { mutableStateOf(initialName.orEmpty()) }
    var playerAgeStr by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BrandIndigo.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Step Indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val stepNum = index + 1
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (step == stepNum) 36.dp else 12.dp, 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (step == stepNum) Brush.horizontalGradient(listOf(BrandIndigo, CyberCyan))
                                    else if (step > stepNum) Brush.horizontalGradient(listOf(BrandTeal, BrandTeal))
                                    else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant))
                                )
                        )
                    }
                }

                // Step Content
                AnimatedContent(
                    targetState = step,
                    label = "onboardingStep"
                ) { targetStep ->
                    when (targetStep) {
                        1 -> OnboardingProfileStep(
                            playerName = playerName,
                            profilePhotoUrl = profilePhotoUrl,
                            onNameChange = { playerName = it },
                            playerAgeStr = playerAgeStr,
                            onAgeChange = { playerAgeStr = it.filter(Char::isDigit).take(3) }
                        )
                        2 -> OnboardingGameplayGuideStep()
                        else -> OnboardingReadyStep(playerName = playerName)
                    }
                }

                // Bottom Navigation Button
                Surface(
                    onClick = {
                        if (step < 3) {
                            step++
                        } else {
                            val age = playerAgeStr.toIntOrNull()
                            val name = playerName.ifBlank { "Player 1" }
                            onFinishOnboarding(name, age)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 6.dp,
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(BrandIndigo, CyberCyan)))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (step == 3) "Start Playing Connect Four!" else "Continue",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = if (step == 3) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingProfileStep(
    playerName: String,
    profilePhotoUrl: String?,
    onNameChange: (String) -> Unit,
    playerAgeStr: String,
    onAgeChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (profilePhotoUrl != null) {
            AsyncImage(
                model = profilePhotoUrl,
                contentDescription = "Google profile photo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(2.dp, CyberCyan, CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(HeroArenaGradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Welcome to Connect Four!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Personalize your tactician profile before stepping into the arena.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = playerName,
                    onValueChange = onNameChange,
                    label = { Text("Your Tactician Name") },
                    placeholder = { Text("e.g. MasterStrategist") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = BrandIndigo) },
                    trailingIcon = {
                        if (playerName.isNotEmpty()) {
                            IconButton(onClick = { onNameChange("") }) {
                                Icon(Icons.Default.Check, contentDescription = "Clear", tint = BrandTeal)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                OutlinedTextField(
                    value = playerAgeStr,
                    onValueChange = onAgeChange,
                    label = { Text("Your Age (Optional)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }
    }
}

@Composable
private fun OnboardingGameplayGuideStep() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Connect Four Board Emblem
        ConnectFourEmblem(
            size = 88.dp,
            animated = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "How to Win at Connect Four",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GuideItem(
                    emoji = "🔴",
                    title = "Drop Your Chips",
                    description = "Tap any column header to drop your chip with realistic gravity physics!"
                )

                GuideItem(
                    emoji = "🎯",
                    title = "Connect 4 in a Row",
                    description = "Align 4 of your colored chips horizontally, vertically, or diagonally to win!"
                )

                GuideItem(
                    emoji = "🛡️",
                    title = "Block Your Opponent",
                    description = "Watch your opponent's moves closely and block their streaks before they connect 4!"
                )
            }
        }
    }
}

@Composable
private fun GuideItem(emoji: String, title: String, description: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BrandIndigo.copy(alpha = 0.1f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 16.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OnboardingReadyStep(playerName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(GoldenTrophyGradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = "Championship Trophy",
                modifier = Modifier.size(52.dp),
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Ready for Battle,\n${playerName.ifBlank { "Player 1" }}! ⚔️",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🌟 Live Online Matches vs Real Opponents",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "🤖 5 AI Personalities (Beginner to Grandmaster)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "📱 Local Pass & Play on a Single Device",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "🎲 Dice Roll Mode with Allowance Chains",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "🗺️ 20+ Stage Adventure Campaign with Stars",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
