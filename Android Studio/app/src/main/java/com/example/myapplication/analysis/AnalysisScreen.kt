package com.example.myapplication.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    shotAngles: List<Double>,
    navController: NavController? = null,
    initialShotType: String = "free_throw"
) {
    val context = LocalContext.current
    var selectedShotType by remember { mutableStateOf(initialShotType) }
    var saved by remember { mutableStateOf(false) }

    val meanAngle = remember(shotAngles) { ShotAnalysis.calculateMean(shotAngles) }
    val stdDev = remember(shotAngles) { ShotAnalysis.calculateStdDev(shotAngles) }
    val suggestions = remember(meanAngle, stdDev, selectedShotType) {
        ShotAnalysis.generateSuggestions(meanAngle, stdDev, selectedShotType)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shot Analysis", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController?.navigateUp() }) {
                        Text("←", color = Color.White, fontSize = 20.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1C1C1E))
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Shot type selector
            ShotTypeSelector(
                selectedType = selectedShotType,
                onTypeSelected = { selectedShotType = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Stats cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Mean Angle",
                    value = "%.1f°".format(meanAngle),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Std Deviation",
                    value = "%.1f°".format(stdDev),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Shots",
                    value = "${shotAngles.size}",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Angle heatmap
            Text(
                text = "Shot Angle Distribution",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            AngleHeatmap(
                angles = shotAngles,
                shotType = selectedShotType,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Suggestions
            Text(
                text = "Suggestions",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            suggestions.forEach { suggestion ->
                SuggestionCard(suggestion = suggestion)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save session button
            Button(
                onClick = {
                    val session = ShotSession(
                        shotType = selectedShotType,
                        shotAngles = shotAngles,
                        meanAngle = meanAngle,
                        stdDeviation = stdDev,
                        shotCount = shotAngles.size
                    )
                    SessionStorage.addSession(context, session)
                    saved = true
                },
                enabled = !saved,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (saved) Color(0xFF2D5A27) else Color(0xFF0A84FF),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = if (saved) "Session Saved ✓" else "Save Session",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ShotTypeSelector(
    selectedType: String,
    onTypeSelected: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ShotTypeOption(
                text = "Free Throws",
                selected = selectedType == "free_throw",
                onClick = { onTypeSelected("free_throw") },
                modifier = Modifier.weight(1f)
            )
            ShotTypeOption(
                text = "3-Pointers",
                selected = selectedType == "three_pointer",
                onClick = { onTypeSelected("three_pointer") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ShotTypeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF0A84FF) else Color.Transparent,
            contentColor = if (selected) Color.White else Color.Gray
        ),
        modifier = modifier.height(44.dp)
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun AngleHeatmap(
    angles: List<Double>,
    shotType: String,
    modifier: Modifier = Modifier
) {
    val optimalRange = if (shotType == "free_throw") 45.0..55.0 else 43.0..47.0

    val binWidth = 2.0
    val binStart = 20.0
    val binEnd = 80.0
    val binCount = ((binEnd - binStart) / binWidth).toInt()
    val bins = IntArray(binCount)

    angles.forEach { angle ->
        val binIndex = ((angle - binStart) / binWidth).toInt().coerceIn(0, binCount - 1)
        bins[binIndex]++
    }

    val maxCount = bins.maxOrNull()?.coerceAtLeast(1) ?: 1

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
        modifier = modifier
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val chartWidth = size.width
            val chartHeight = size.height - 30f
            val barWidth = chartWidth / binCount

            // Draw optimal range highlight
            val optStartX = ((optimalRange.start - binStart) / (binEnd - binStart) * chartWidth).toFloat()
            val optEndX = ((optimalRange.endInclusive - binStart) / (binEnd - binStart) * chartWidth).toFloat()
            drawRect(
                color = Color(0xFF0A84FF).copy(alpha = 0.15f),
                topLeft = Offset(optStartX, 0f),
                size = Size(optEndX - optStartX, chartHeight)
            )

            // Draw bars
            bins.forEachIndexed { index, count ->
                val intensity = count.toFloat() / maxCount
                val barHeight = intensity * chartHeight
                val x = index * barWidth

                val barColor = when {
                    intensity == 0f -> Color.Transparent
                    intensity < 0.25f -> Color(0xFF1A3A1A)
                    intensity < 0.5f -> Color(0xFF2D6A2D)
                    intensity < 0.75f -> Color(0xFFFF9500)
                    else -> Color(0xFFFF3B30)
                }

                drawRect(
                    color = barColor,
                    topLeft = Offset(x + 1f, chartHeight - barHeight),
                    size = Size(barWidth - 2f, barHeight)
                )
            }

            // Draw axis labels
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 24f
                textAlign = android.graphics.Paint.Align.CENTER
            }

            for (degree in listOf(20, 30, 40, 50, 60, 70, 80)) {
                val x = ((degree - binStart) / (binEnd - binStart) * chartWidth).toFloat()
                drawContext.canvas.nativeCanvas.drawText(
                    "${degree}°",
                    x,
                    chartHeight + 24f,
                    paint
                )
            }
        }
    }
}

@Composable
fun SuggestionCard(suggestion: Suggestion) {
    val (iconText, cardColor) = when (suggestion.type) {
        SuggestionType.ANGLE_GOOD, SuggestionType.CONSISTENCY_GOOD ->
            "✅" to Color(0xFF1A3A1A)
        SuggestionType.CONSISTENCY_MODERATE ->
            "⚠️" to Color(0xFF3A3A1A)
        SuggestionType.ANGLE_LOW, SuggestionType.ANGLE_HIGH, SuggestionType.CONSISTENCY_POOR ->
            "❌" to Color(0xFF3A1A1A)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = iconText,
                fontSize = 20.sp,
                modifier = Modifier.padding(end = 12.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = suggestion.message,
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
