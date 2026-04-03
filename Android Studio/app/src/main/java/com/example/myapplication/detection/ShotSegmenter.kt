package com.example.myapplication.detection

import androidx.compose.ui.geometry.Offset

data class BallPosition(
    val timestampMs: Long,
    val normX: Float,   // 0..1 relative to frame width
    val normY: Float    // 0..1 relative to frame height
)

data class ShotAttempt(
    val positions: List<BallPosition>,
    val startTimestampMs: Long,
    val endTimestampMs: Long
)

object ShotSegmenter {

    // Minimum number of frames to count as a real shot, filters out noise
    private const val MIN_SHOT_FRAMES = 5

    fun segment(
        frames: List<FrameDetection>,
        hoopPosition: Offset  // normalised 0..1
    ): List<ShotAttempt> {

        val hoopIsInBottomHalf = hoopPosition.y > 0.5f

        // Extract normalised ball centre positions from detections
        val ballPositions = extractBallPositions(frames)

        if (ballPositions.isEmpty()) return emptyList()

        val shots = mutableListOf<ShotAttempt>()
        var currentShot = mutableListOf<BallPosition>()
        var inShot = false

        for (pos in ballPositions) {
            val ballInOppositeHalf = if (hoopIsInBottomHalf) {
                pos.normY < 0.5f   // hoop bottom, ball in top half = shot territory
            } else {
                pos.normY > 0.5f   // hoop top, ball in bottom half = shot territory
            }

            when {
                // Ball has moved into opposite half — start or continue a shot
                ballInOppositeHalf -> {
                    inShot = true
                    currentShot.add(pos)
                }

                // Ball has returned to hoop's half — end of this shot attempt
                inShot && !ballInOppositeHalf -> {
                    if (currentShot.size >= MIN_SHOT_FRAMES) {
                        shots.add(
                            ShotAttempt(
                                positions = currentShot.toList(),
                                startTimestampMs = currentShot.first().timestampMs,
                                endTimestampMs = currentShot.last().timestampMs
                            )
                        )
                    }
                    currentShot = mutableListOf()
                    inShot = false
                }
            }
        }

        // Capture any shot still in progress at end of video
        if (inShot && currentShot.size >= MIN_SHOT_FRAMES) {
            shots.add(
                ShotAttempt(
                    positions = currentShot.toList(),
                    startTimestampMs = currentShot.first().timestampMs,
                    endTimestampMs = currentShot.last().timestampMs
                )
            )
        }

        return shots
    }

    private fun extractBallPositions(frames: List<FrameDetection>): List<BallPosition> {
        val positions = mutableListOf<BallPosition>()

        for (frame in frames) {
            if (frame.detections.isEmpty()) continue

            // If multiple detections, use the most confident one
            val best = frame.detections.maxByOrNull { it.category.confidence } ?: continue

            val box = best.boundingBox

            // Normalise centre point to 0..1
            val centreX = ((box.left + box.right) / 2f) / frame.frameWidth
            val centreY = ((box.top + box.bottom) / 2f) / frame.frameHeight

            positions.add(
                BallPosition(
                    timestampMs = frame.timestampMs,
                    normX = centreX.coerceIn(0f, 1f),
                    normY = centreY.coerceIn(0f, 1f)
                )
            )
        }

        return positions
    }
}