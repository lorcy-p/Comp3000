package com.example.app.ui.processing

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun VideoProcessingScreen(
    videoUriString: String,
    navController: NavController? = null
) {
    var processingProgress by remember { mutableStateOf(0f) }
    var processingStage by remember { mutableStateOf("Uploading video...") }
    var isProcessing by remember { mutableStateOf(true) }

    // Decode the URI
    val videoUri = remember { Uri.decode(videoUriString) }

    // Video processing logic
    LaunchedEffect(videoUri) {
        try {


            //todo : edit this to actually represent progress
            processingStage = "Processing shot data..."
            for (i in 0..100) {
                if (!isProcessing) break
                processingProgress = i / 100f
                kotlinx.coroutines.delay(60)
            }

            // Complete
            if (isProcessing) {
                processingStage = "Complete!"
                kotlinx.coroutines.delay(500)

                // Navigate back to home or to results screen
                // TODO: Replace home with computer vision screen when complete
                navController?.navigate("home") {
                    popUpTo("home") { inclusive = false }
                }
            }
        } catch (e: Exception) {
            // Handle errors
            processingStage = "Error: ${e.message}"
            kotlinx.coroutines.delay(2000)
            navController?.navigateUp()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "Processing Video",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Circular Progress Indicator
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                CircularProgressIndicator(
                    progress = processingProgress,
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF007AFF),
                    strokeWidth = 8.dp,
                    trackColor = Color(0xFF1C1C1E)
                )

                Text(
                    text = "${(processingProgress * 100).toInt()}%",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Processing stage text
            Text(
                text = processingStage,
                fontSize = 16.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Linear progress bar
            LinearProgressIndicator(
                progress = processingProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFF007AFF),
                trackColor = Color(0xFF1C1C1E),
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Cancel button
            OutlinedButton(
                onClick = {
                    isProcessing = false
                    navController?.navigateUp()
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}