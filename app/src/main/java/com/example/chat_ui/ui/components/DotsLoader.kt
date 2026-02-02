package com.example.chat_ui.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A reusable dots loader component using Compose Animation.
 * Shows 3 animated dots for loading states.
 *
 * @param modifier Modifier for the loader container
 * @param dotRadius Radius of each dot in dp (default: 8dp)
 * @param dotColor Color of the dots (default: gray)
 * @param animDuration Animation duration in ms (default: 300)
 */
@Composable
fun DotsLoader(
    modifier: Modifier = Modifier,
    dotRadius: Dp = 8.dp,
    dotColor: Color = Color(0xFF9E9E9E),
    animDuration: Int = 300
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dotsLoader")
    
    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(animDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    
    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(animDuration, delayMillis = 100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    
    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(animDuration, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LoaderDot(offset = dot1Offset, radius = dotRadius, color = dotColor)
        LoaderDot(offset = dot2Offset, radius = dotRadius, color = dotColor)
        LoaderDot(offset = dot3Offset, radius = dotRadius, color = dotColor)
    }
}

@Composable
private fun LoaderDot(offset: Float, radius: Dp, color: Color) {
    Box(
        modifier = Modifier
            .size(radius)
            .offset(y = offset.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * A themed dots loader that uses primary color.
 */
@Composable
fun ThemedDotsLoader(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF3B82F6),
    dotRadius: Dp = 10.dp
) {
    DotsLoader(
        modifier = modifier,
        dotRadius = dotRadius,
        dotColor = color,
        animDuration = 250
    )
}
