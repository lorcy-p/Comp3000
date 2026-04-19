package com.example.myapplication.detection

import androidx.compose.ui.geometry.Offset

data class BallPosition(
    val timestampMs: Long,
    val normX: Float,
    val normY: Float
)

data class ShotAttempt(
    val positions: List<BallPosition>,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val curve: QuadraticCurve? = null
)

object ShotSegmenter {

    private const val MIN_RISE_ABOVE_HOOP = 0.08f
    private const val MIN_RISING_FRAMES   = 3
    private const val MIN_TOTAL_FRAMES    = 6
    private const val SMOOTH_WINDOW       = 3

    private enum class Phase { IDLE, RISING, FALLING }

    fun segment(
        frames: List<FrameDetection>,
        hoopPosition: Offset
    ): List<ShotAttempt> {

        val ballPositions = extractBallPositions(frames)
        if (ballPositions.size < MIN_TOTAL_FRAMES) return emptyList()

        val smoothed = smoothPositions(ballPositions)
        val shots    = mutableListOf<ShotAttempt>()

        var phase        = Phase.IDLE
        var shotStart    = 0
        var apexY        = 1f
        var risingFrames = 0

        for (i in 1 until smoothed.size) {
            val prev = smoothed[i - 1]
            val curr = smoothed[i]

            val movingUp   = curr.normY < prev.normY
            val movingDown = curr.normY > prev.normY

            when (phase) {

                Phase.IDLE -> {
                    if (movingUp && curr.normY <= hoopPosition.y + 0.15f) {
                        phase        = Phase.RISING
                        shotStart    = i - 1
                        apexY        = curr.normY
                        risingFrames = 1
                    }
                }

                Phase.RISING -> {
                    when {
                        movingUp -> {
                            apexY = minOf(apexY, curr.normY)
                            risingFrames++
                        }
                        movingDown -> {
                            val roseAboveHoop = apexY < (hoopPosition.y - MIN_RISE_ABOVE_HOOP)
                            if (roseAboveHoop && risingFrames >= MIN_RISING_FRAMES) {
                                phase = Phase.FALLING
                            } else {
                                android.util.Log.d("SEGMENTER",
                                    "Discarded rising: apexY=$apexY hoopY=${hoopPosition.y} frames=$risingFrames")
                                phase = Phase.IDLE
                            }
                        }
                    }
                }

                Phase.FALLING -> {
                    val ballBelowHoop = curr.normY > hoopPosition.y + 0.05f
                    val isLast        = i == smoothed.size - 1

                    if (ballBelowHoop || isLast) {
                        // End shot — clip to above-hoop only before saving
                        val rawWindow = ballPositions.filter { pos ->
                            pos.timestampMs >= smoothed[shotStart].timestampMs &&
                                    pos.timestampMs <= curr.timestampMs
                        }
                        saveIfValid(rawWindow, hoopPosition, shots)
                        phase = Phase.IDLE

                    } else if (movingUp) {
                        // Possible rim bounce — end current shot
                        val rawWindow = ballPositions.filter { pos ->
                            pos.timestampMs >= smoothed[shotStart].timestampMs &&
                                    pos.timestampMs <= prev.timestampMs
                        }
                        saveIfValid(rawWindow, hoopPosition, shots)
                        phase = Phase.IDLE
                    }
                }
            }
        }

        android.util.Log.d("SEGMENTER", "Total shots: ${shots.size}")
        return shots
    }

    /**
     * Validates a candidate shot window and adds it to the list if it passes.
     * Filters to only above-hoop positions and checks ball is moving TOWARD
     * the hoop horizontally (not away from it).
     */
    private fun saveIfValid(
        rawPositions: List<BallPosition>,
        hoopPosition: Offset,
        shots: MutableList<ShotAttempt>
    ) {
        if (rawPositions.size < MIN_TOTAL_FRAMES) return

        // ── Filter 1: only keep positions above the hoop ─────────────────────
        val aboveHoop = rawPositions.filter { it.normY < hoopPosition.y }

        android.util.Log.d("SEGMENTER",
            "saveIfValid: ${rawPositions.size} raw → ${aboveHoop.size} above hoop")

        if (aboveHoop.size < MIN_TOTAL_FRAMES) return

        // ── Filter 2: ball must be moving TOWARD hoop horizontally ───────────
        // Compare the average X of the first third of positions vs the last third.
        // If the ball is moving away from the hoop X, discard.
        val third      = (aboveHoop.size / 3).coerceAtLeast(1)
        val earlyAvgX  = aboveHoop.take(third).map { it.normX }.average().toFloat()
        val lateAvgX   = aboveHoop.takeLast(third).map { it.normX }.average().toFloat()
        val hoopX      = hoopPosition.x

        // Distance from hoop X at start vs end — end should be closer
        val earlyDist  = kotlin.math.abs(earlyAvgX - hoopX)
        val lateDist   = kotlin.math.abs(lateAvgX  - hoopX)

        val movingTowardHoop = lateDist < earlyDist

        android.util.Log.d("SEGMENTER",
            "earlyX=$earlyAvgX lateX=$lateAvgX hoopX=$hoopX " +
                    "earlyDist=$earlyDist lateDist=$lateDist toward=$movingTowardHoop")

        if (!movingTowardHoop) {
            android.util.Log.d("SEGMENTER", "Discarded: moving away from hoop")
            return
        }

        shots.add(
            ShotAttempt(
                positions        = aboveHoop,
                startTimestampMs = aboveHoop.first().timestampMs,
                endTimestampMs   = aboveHoop.last().timestampMs
            )
        )

        android.util.Log.d("SEGMENTER", "Shot saved with ${aboveHoop.size} points")
    }

    private fun smoothPositions(positions: List<BallPosition>): List<BallPosition> {
        if (positions.size <= SMOOTH_WINDOW) return positions
        return positions.mapIndexed { i, pos ->
            val windowStart = maxOf(0, i - SMOOTH_WINDOW / 2)
            val windowEnd   = minOf(positions.size - 1, i + SMOOTH_WINDOW / 2)
            val window      = positions.subList(windowStart, windowEnd + 1)
            pos.copy(normY  = window.map { it.normY }.average().toFloat())
        }
    }

    private fun extractBallPositions(frames: List<FrameDetection>): List<BallPosition> {
        val positions = mutableListOf<BallPosition>()
        for (frame in frames) {
            if (frame.detections.isEmpty()) continue
            val best = frame.detections.maxByOrNull { it.category.confidence } ?: continue
            val box  = best.boundingBox
            val cx   = ((box.left  + box.right)  / 2f) / frame.frameWidth
            val cy   = ((box.top   + box.bottom) / 2f) / frame.frameHeight
            positions.add(
                BallPosition(
                    timestampMs = frame.timestampMs,
                    normX       = cx.coerceIn(0f, 1f),
                    normY       = cy.coerceIn(0f, 1f)
                )
            )
        }
        return positions
    }
}