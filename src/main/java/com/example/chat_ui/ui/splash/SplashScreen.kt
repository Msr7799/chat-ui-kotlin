package com.example.chat_ui.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.chat_ui.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }
    
    val progress by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 3000),
        label = "progress"
    )
    
    LaunchedEffect(Unit) {
        startAnimation = true
        delay(3000)
        onSplashComplete()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF212121)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.fulltext_logo_white_small3),
                contentDescription = "Logo",
                modifier = Modifier.width(450.dp),
                contentScale = ContentScale.FillWidth
            )
            
            Spacer(modifier = Modifier.height(50.dp))
            
            // Progress bar container (border)
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Transparent)
                    .padding(2.dp)
            ) {
                // Border
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Transparent)
                ) {
                    // Outer border
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFD54F).copy(alpha = 0.3f))
                    )
                    
                    // Inner background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF212121))
                    ) {
                        // Progress fill
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFFFD54F))
                        )
                    }
                }
            }
        }
    }
}
