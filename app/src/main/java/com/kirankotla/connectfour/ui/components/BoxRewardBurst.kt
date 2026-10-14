package com.kirankotla.connectfour.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirankotla.connectfour.model.reward.BoxReward
import com.kirankotla.connectfour.model.reward.RewardKind
import kotlinx.coroutines.delay

@Composable
fun BoxRewardBurst(
    reward: BoxReward?,
    eventId: Int,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    var visible by remember(eventId) { mutableStateOf(reward != null) }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.72f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "rewardBurstScale"
    )

    LaunchedEffect(eventId) {
        if (reward != null) {
            visible = true
            delay(if (reduceMotion) 900L else 1800L)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible && reward != null,
        enter = fadeIn() + scaleIn(),
        modifier = modifier
    ) {
        val current = reward ?: return@AnimatedVisibility
        val icon = when (current.kind) {
            RewardKind.COIN -> Icons.Default.MonetizationOn
            RewardKind.GEM -> Icons.Default.Diamond
            RewardKind.JOKER -> Icons.Default.Style
            RewardKind.POWER_UP -> Icons.Default.AutoAwesome
            RewardKind.COLLECTIBLE -> Icons.Default.CollectionsBookmark
            RewardKind.NOTHING -> Icons.Default.AutoAwesome
        }
        val accent = when (current.kind) {
            RewardKind.COIN -> Color(0xFFFFC107)
            RewardKind.GEM -> Color(0xFF67E8F9)
            RewardKind.JOKER, RewardKind.POWER_UP -> Color(0xFFC084FC)
            RewardKind.COLLECTIBLE -> Color(0xFFF9A8D4)
            RewardKind.NOTHING -> Color(0xFF94A3B8)
        }
        Surface(
            color = Color(0xFF101C35).copy(alpha = 0.96f),
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 10.dp,
            modifier = Modifier.scale(scale)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = accent)
                Text(
                    text = when (current.kind) {
                        RewardKind.COIN -> "+${current.amount} coins"
                        RewardKind.GEM -> "+${current.amount} gems"
                        RewardKind.JOKER -> "${current.id.replace('_', ' ')} joker"
                        RewardKind.POWER_UP -> "Power-up"
                        RewardKind.COLLECTIBLE -> "New collectible"
                        RewardKind.NOTHING -> "Keep the chain going"
                    },
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}
