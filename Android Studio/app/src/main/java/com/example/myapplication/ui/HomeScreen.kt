package com.example.app.ui.home

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    navController: NavController? = null,
    onViewSessionsClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // Video picker launcher - navigates directly to detection screen
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            navController?.navigate("detection/${Uri.encode(it.toString())}")
        }
    }

    // Video recording support
    var recordedVideoUri by remember { mutableStateOf<Uri?>(null) }

    val videoRecordLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && recordedVideoUri != null) {
            val encoded = Uri.encode(recordedVideoUri.toString())
            navController?.navigate("detection/$encoded")
        } else {
            // Recording was cancelled or failed — clean up the empty URI
            recordedVideoUri?.let { uri ->
                context.contentResolver.delete(uri, null, null)
            }
            recordedVideoUri = null
        }
    }

    // Camera permission launcher — once granted, launch the recorder
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createVideoUri(context)
            if (uri != null) {
                recordedVideoUri = uri
                videoRecordLauncher.launch(uri)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Title
            Text(
                text = "ShotTrack",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Basketball Shot Analysis",
                fontSize = 16.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Record button - requests permission then opens native camera
            HomeActionButton(
                text = "Record New Video",
                icon = android.R.drawable.ic_menu_camera,
                onClick = {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Upload button - opens gallery picker
            HomeActionButton(
                text = "Upload Video",
                icon = android.R.drawable.ic_menu_slideshow,
                onClick = { videoPickerLauncher.launch("video/*") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Divider
            Text(
                text = "—  or  —",
                color = Color.Gray,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            HomeActionButton(
                text = "View Saved Sessions",
                icon = null,
                onClick = onViewSessionsClick
            )
        }
    }
}

/**
 * Creates a content URI for a new video file in the device's MediaStore.
 * The native camera app will write the recorded video to this location.
 */
private fun createVideoUri(context: Context): Uri? {
    val contentValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "shottrack_${System.currentTimeMillis()}.mp4")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
    }
    return context.contentResolver.insert(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        contentValues
    )
}

@Composable
fun HomeActionButton(
    text: String,
    icon: Int?,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1C1C1E),
            contentColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}