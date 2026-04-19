package com.example.myapplication.detection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.pow

@Composable
fun ShotArcOverlay(
    shot: ShotAttempt?,
    hoopPosition: Offset,
    videoWidth: Int,
    videoHeight: Int,
    modifier: Modifier = Modifier
) {
    // Nothing to draw if no active shot or no curve was fitted
    val curve = shot?.curve ?: return

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasW = size.width
        val canvasH = size.height

        // Calculate the actual rect the video occupies inside the canvas.
        val videoRect = calculateVideoRect(canvasW, canvasH, videoWidth, videoHeight)

        val videoL = videoRect.first
        val videoT = videoRect.second
        val videoDispW = videoRect.third
        val videoDispH = videoRect.fourth

        // Helper: map normalised video coordinate tocanvas pixel
        fun normToCanvas(normX: Float, normY: Float): Offset {
            return Offset(
                x = videoL + normX * videoDispW,
                y = videoT + normY * videoDispH
            )
        }

        // Draw the fitted arc by sampling the quadratic at many points
        drawArc(curve, shot.positions, canvasW, ::normToCanvas)

        // Draw tracked ball positions as small dots
        drawBallPositions(shot.positions, ::normToCanvas)

        // Draw the hoop marker
        drawHoopMarker(hoopPosition, ::normToCanvas)

        // Draw the entry angle indicator at the last tracked position
        drawAngleIndicator(curve, shot.positions, ::normToCanvas)
    }
}

// ── Drawing functions ────────────────────────────────────────────────────────

private fun DrawScope.drawArc(
    curve: QuadraticCurve,
    positions: List<BallPosition>,
    canvasW: Float,
    normToCanvas: (Float, Float) -> Offset
) {
    if (positions.isEmpty()) return

    val minX = positions.minOf { it.normX }
    val maxX = positions.maxOf { it.normX }

    // Sample 60 points along the x range of the tracked positions
    val steps = 60
    val path = Path()
    var firstPoint = true

    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val normX = minX + t * (maxX - minX)
        val scaledX = normX.toDouble() * 1000.0

        // y = ax² + bx + c (in scaled space)
        val scaledY = curve.a * scaledX.pow(2) + curve.b * scaledX + curve.c
        val normY = (scaledY / 1000.0).toFloat()

        // Skip points outside visible frame
        if (normY < -0.1f || normY > 1.1f) continue

        val point = normToCanvas(normX, normY)

        if (firstPoint) {
            path.moveTo(point.x, point.y)
            firstPoint = false
        } else {
            path.lineTo(point.x, point.y)
        }
    }

    // Outer glow
    drawPath(
        path = path,
        color = Color(0x40FF9500),
        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
    )

    // Main arc line
    drawPath(
        path = path,
        color = Color(0xFFFF9500),
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawBallPositions(
    positions: List<BallPosition>,
    normToCanvas: (Float, Float) -> Offset
) {
    positions.forEach { pos ->
        val point = normToCanvas(pos.normX, pos.normY)

        // Outer ring
        drawCircle(
            color = Color(0x60FF9500),
            radius = 8.dp.toPx(),
            center = point
        )
        // Inner dot
        drawCircle(
            color = Color(0xFFFF9500),
            radius = 4.dp.toPx(),
            center = point
        )
    }
}

private fun DrawScope.drawHoopMarker(
    hoopPosition: Offset,
    normToCanvas: (Float, Float) -> Offset
) {
    val point = normToCanvas(hoopPosition.x, hoopPosition.y)
    val radius = 16.dp.toPx()
    val lineLen = 10.dp.toPx()

    // Ring
    drawCircle(
        color = Color(0xFFFF3B30),
        radius = radius,
        center = point,
        style = Stroke(width = 2.5.dp.toPx())
    )

    // Centre dot
    drawCircle(
        color = Color(0xFFFF3B30),
        radius = 4.dp.toPx(),
        center = point
    )

    // Crosshair
    drawLine(
        color = Color(0xFFFF3B30),
        start = Offset(point.x - radius - lineLen, point.y),
        end   = Offset(point.x - radius, point.y),
        strokeWidth = 2.dp.toPx()
    )
    drawLine(
        color = Color(0xFFFF3B30),
        start = Offset(point.x + radius, point.y),
        end   = Offset(point.x + radius + lineLen, point.y),
        strokeWidth = 2.dp.toPx()
    )
    drawLine(
        color = Color(0xFFFF3B30),
        start = Offset(point.x, point.y - radius - lineLen),
        end   = Offset(point.x, point.y - radius),
        strokeWidth = 2.dp.toPx()
    )
    drawLine(
        color = Color(0xFFFF3B30),
        start = Offset(point.x, point.y + radius),
        end   = Offset(point.x, point.y + radius + lineLen),
        strokeWidth = 2.dp.toPx()
    )
}

private fun DrawScope.drawAngleIndicator(
    curve: QuadraticCurve,
    positions: List<BallPosition>,
    normToCanvas: (Float, Float) -> Offset
) {
    if (positions.isEmpty()) return

    // Draw a short tangent line at the last tracked point to visualise entry angle
    val lastPos  = positions.last()
    val scaledX  = lastPos.normX.toDouble() * 1000.0
    val slope    = 2.0 * curve.a * scaledX + curve.b

    val origin   = normToCanvas(lastPos.normX, lastPos.normY)
    val lineLen  = 40.dp.toPx()

    // Direction vector from slope (normalised)
    val magnitude = Math.sqrt(1.0 + slope * slope).toFloat()
    val dx = lineLen / magnitude
    val dy = (lineLen * slope.toFloat()) / magnitude

    // Draw tangent line showing the angle of descent
    drawLine(
        color = Color(0xFF34C759),
        start = Offset(origin.x - dx, origin.y - dy),
        end   = Offset(origin.x + dx, origin.y + dy),
        strokeWidth = 2.5.dp.toPx(),
        cap   = StrokeCap.Round
    )

    // Endpoint dot
    drawCircle(
        color = Color(0xFF34C759),
        radius = 5.dp.toPx(),
        center = origin
    )
}

// ── Coordinate helpers ───────────────────────────────────────────────────────

/**
 * Calculates the letterboxed video rect within the canvas.
 * Returns (left, top, displayWidth, displayHeight).
 * Matches ExoPlayer's RESIZE_MODE_FIT behaviour.
 */
private fun calculateVideoRect(
    canvasW: Float,
    canvasH: Float,
    videoW: Int,
    videoH: Int
): Quadruple<Float, Float, Float, Float> {
    if (videoW <= 0 || videoH <= 0) {
        return Quadruple(0f, 0f, canvasW, canvasH)
    }

    val videoAspect = videoW.toFloat() / videoH.toFloat()
    val canvasAspect = canvasW / canvasH

    val (dispW, dispH) = if (videoAspect > canvasAspect) {
        // Pillarboxed — video fills width, letterboxed top/bottom
        val w = canvasW
        val h = canvasW / videoAspect
        Pair(w, h)
    } else {
        // Letterboxed — video fills height, pillarboxed left/right
        val h = canvasH
        val w = canvasH * videoAspect
        Pair(w, h)
    }

    val left = (canvasW - dispW) / 2f
    val top  = (canvasH - dispH) / 2f

    return Quadruple(left, top, dispW, dispH)
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)