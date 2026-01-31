package com.example.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onRecordClick: () -> Unit = {},
    onUploadClick: () -> Unit = {},
    onViewSessionsClick: () -> Unit = {}
) {
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




            // Buttons
            HomeActionButton(
                text = "Record New Video",
                icon = android.R.drawable.ic_menu_camera,
                onClick = onRecordClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            HomeActionButton(
                text = "Upload Video",
                icon = android.R.drawable.ic_menu_slideshow,
                onClick = onUploadClick
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
