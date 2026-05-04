package com.example.myapplication.analysis

import kotlin.math.sqrt

object ShotAnalysis {

    // Optimal angle ranges aligned with QuadraticFitter.angleQuality
    // Free throws: 45-50° - optimal angle reducing total distance travelled and cross section of hoop
    // Three pointers: 43-47° — lower arc dues to further distance needed
    private val OPTIMAL_RANGES = mapOf(
        "free_throw" to (45.0..55.0),
        "three_pointer" to (43.0..47.0)
    )

    private const val GOOD_CONSISTENCY = 3.0
    private const val MODERATE_CONSISTENCY = 6.0

    fun calculateMean(angles: List<Double>): Double {
        if (angles.isEmpty()) return 0.0
        return angles.sum() / angles.size
    }

    fun calculateStdDev(angles: List<Double>): Double {
        if (angles.size < 2) return 0.0
        val mean = calculateMean(angles)
        val variance = angles.sumOf { (it - mean) * (it - mean) } / (angles.size - 1)
        return sqrt(variance)
    }

    fun generateSuggestions(
        meanAngle: Double,
        stdDev: Double,
        shotType: String
    ): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        val optimalRange = OPTIMAL_RANGES[shotType] ?: OPTIMAL_RANGES["free_throw"]!!
        val shotLabel = if (shotType == "free_throw") "free throws" else "three pointers"

        // Angle feedback
        when {
            meanAngle < optimalRange.start -> {
                val deficit = optimalRange.start - meanAngle
                suggestions.add(
                    Suggestion(
                        type = SuggestionType.ANGLE_LOW,
                        title = "Shot angle too low",
                        message = "Your average entry angle of %.1f° is %.1f° below the optimal range for %s (%.0f°–%.0f°). A higher arc gives the ball a larger target area when entering the hoop. Try releasing the ball with more upward motion at the point of release.".format(
                            meanAngle, deficit, shotLabel, optimalRange.start, optimalRange.endInclusive
                        )
                    )
                )
            }
            meanAngle > optimalRange.endInclusive -> {
                val excess = meanAngle - optimalRange.endInclusive
                suggestions.add(
                    Suggestion(
                        type = SuggestionType.ANGLE_HIGH,
                        title = "Shot angle too high",
                        message = "Your average entry angle of %.1f° is %.1f° above the optimal range for %s (%.0f°–%.0f°). An excessively high arc reduces accuracy and requires more energy. Try a slightly flatter release to improve consistency.".format(
                            meanAngle, excess, shotLabel, optimalRange.start, optimalRange.endInclusive
                        )
                    )
                )
            }
            else -> {
                suggestions.add(
                    Suggestion(
                        type = SuggestionType.ANGLE_GOOD,
                        title = "Shot angle is good",
                        message = "Your average entry angle of %.1f° is within the optimal range for %s (%.0f°–%.0f°). This gives the ball a good entry angle into the hoop.".format(
                            meanAngle, shotLabel, optimalRange.start, optimalRange.endInclusive
                        )
                    )
                )
            }
        }

        // Consistency feedback
        when {
            stdDev <= GOOD_CONSISTENCY -> {
                suggestions.add(
                    Suggestion(
                        type = SuggestionType.CONSISTENCY_GOOD,
                        title = "Consistent release",
                        message = "Your standard deviation of %.1f° shows strong consistency across shots. This indicates a repeatable shooting form.".format(stdDev)
                    )
                )
            }
            stdDev <= MODERATE_CONSISTENCY -> {
                suggestions.add(
                    Suggestion(
                        type = SuggestionType.CONSISTENCY_MODERATE,
                        title = "Moderate consistency",
                        message = "Your standard deviation of %.1f° shows moderate variation between shots. Focus on maintaining the same elbow position and follow-through on each attempt to improve repeatability.".format(stdDev)
                    )
                )
            }
            else -> {
                suggestions.add(
                    Suggestion(
                        type = SuggestionType.CONSISTENCY_POOR,
                        title = "Inconsistent release angle",
                        message = "Your standard deviation of %.1f° indicates significant variation between shots. This suggests your shooting form is changing between attempts. Work on a consistent set point, elbow alignment, and follow-through to reduce this variation.".format(stdDev)
                    )
                )
            }
        }

        return suggestions
    }
}

data class Suggestion(
    val type: SuggestionType,
    val title: String,
    val message: String
)

enum class SuggestionType {
    ANGLE_LOW,
    ANGLE_HIGH,
    ANGLE_GOOD,
    CONSISTENCY_GOOD,
    CONSISTENCY_MODERATE,
    CONSISTENCY_POOR
}
