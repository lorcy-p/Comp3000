package com.example.myapplication.utils

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProcessingUI(
    progress: Float,
    stage: String,
    currentFrame: Int,
    totalFrames: Int,
    samplingRate: String = "",
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Processing Video",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(48.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF007AFF),
                strokeWidth = 8.dp,
                trackColor = Color(0xFF1C1C1E)
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = stage, fontSize = 16.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(8.dp))

        if (totalFrames > 0) {
            Text(
                text = "Frame $currentFrame / $totalFrames",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
        
        if (samplingRate.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sampling: ",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = samplingRate,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (samplingRate) {
                        "High" -> Color(0xFF34C759)
                        "Mid" -> Color(0xFFFF9500)
                        else -> Color(0xFF8E8E93)
                    }
                )
            }
        }
    }
}