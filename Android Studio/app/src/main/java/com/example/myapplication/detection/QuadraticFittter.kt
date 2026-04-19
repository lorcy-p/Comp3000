package com.example.myapplication.detection

import androidx.compose.ui.geometry.Offset
import org.apache.commons.math3.fitting.PolynomialCurveFitter
import org.apache.commons.math3.fitting.WeightedObservedPoints
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.pow

data class QuadraticCurve(
    val a: Double,
    val b: Double,
    val c: Double,
    val rSquared: Double,
    val entryAngleDeg: Float,
    val apexNormX: Float,
    val apexNormY: Float
)

object QuadraticFitter {

    const val MIN_R_SQUARED = 0.4

    fun fit(
        positions: List<BallPosition>,
        hoopPosition: Offset
    ): QuadraticCurve? {

        android.util.Log.d("FITTER", "--- Fitting ${positions.size} points ---")

        if (positions.size < 3) {
            android.util.Log.d("FITTER", "REJECTED: ${positions.size} points")
            return null
        }

        // Positions are already filtered to above-hoop by the segmenter,
        // but enforce it here as a safety net
        val aboveHoop = positions.filter { it.normY < hoopPosition.y }

        android.util.Log.d("FITTER", "Above hoop: ${aboveHoop.size} points")

        if (aboveHoop.size < 3) {
            android.util.Log.d("FITTER", "REJECTED: only ${aboveHoop.size} above hoop")
            return null
        }

        // Sort by distance from hoop
        val hoopX = hoopPosition.x

        // Determine travel direction and cut off anything past the hoop X
        val firstX       = aboveHoop.first().normX
        val lastX        = aboveHoop.last().normX
        val travelRight  = lastX > firstX

        // Remove any points that have already passed the hoop X —
        // these are post-entry and corrupt the entry angle
        val beforeHoop = if (travelRight) {
            aboveHoop.filter { it.normX <= hoopX }
        } else {
            aboveHoop.filter { it.normX >= hoopX }
        }

        android.util.Log.d("FITTER",
            "Before hoop X: ${beforeHoop.size} points " +
                    "(travelRight=$travelRight hoopX=$hoopX)")

        // Fall back to full above-hoop set if clipping left too few
        val workingSet = when {
            beforeHoop.size >= 3 -> beforeHoop
            aboveHoop.size  >= 3 -> aboveHoop
            else -> {
                android.util.Log.d("FITTER", "REJECTED: not enough points after clipping")
                return null
            }
        }

        android.util.Log.d("FITTER", "Working set: ${workingSet.size} points")

        // Scale to 0..1000 for numerical stability
        val xs = workingSet.map { it.normX.toDouble() * 1000.0 }
        val ys = workingSet.map { it.normY.toDouble() * 1000.0 }

        val xRange = xs.max() - xs.min()
        android.util.Log.d("FITTER", "xRange=$xRange")

        if (xRange < 5.0) {
            android.util.Log.d("FITTER", "REJECTED: xRange too small ($xRange)")
            return null
        }

        // Weight points by proximity to hoop X — points closer to the hoop
        // get higher weight so the curve is most accurate near entry
        val obs = WeightedObservedPoints()
        val hoopXScaled = hoopX.toDouble() * 1000.0
        for (i in xs.indices) {
            val distFromHoop = abs(xs[i] - hoopXScaled)
            // Weight = 1 / (1 + distance) so closest points dominate
            val weight = 1.0 / (1.0 + distFromHoop / 100.0)
            obs.add(weight, xs[i], ys[i])
        }

        val coefficients = try {
            PolynomialCurveFitter.create(2).fit(obs.toList())
        } catch (e: Exception) {
            android.util.Log.d("FITTER", "REJECTED: fitter threw ${e.message}")
            return null
        }

        // Commons Math returns [c, b, a]
        val c = coefficients[0]
        val b = coefficients[1]
        val a = coefficients[2]

        android.util.Log.d("FITTER", "a=$a b=$b c=$c")

        // R²
        val meanY = ys.average()
        var ssTot = 0.0
        var ssRes = 0.0
        for (i in xs.indices) {
            val predicted = a * xs[i].pow(2) + b * xs[i] + c
            ssTot += (ys[i] - meanY).pow(2)
            ssRes += (ys[i] - predicted).pow(2)
        }
        val rSquared = if (ssTot > 1e-6) (1.0 - ssRes / ssTot) else 0.0

        android.util.Log.d("FITTER", "rSquared=$rSquared")

        if (rSquared < MIN_R_SQUARED) {
            android.util.Log.d("FITTER", "REJECTED: rSquared=$rSquared")
            return null
        }

        val closestToHoop = workingSet.minByOrNull { abs(it.normX - hoopX) }!!
        val entryXScaled  = closestToHoop.normX.toDouble() * 1000.0
        val slope         = 2.0 * a * entryXScaled + b
        val angleDeg      = Math.toDegrees(atan(abs(slope))).toFloat()

        android.util.Log.d("FITTER",
            "Entry at normX=${closestToHoop.normX} slope=$slope angleDeg=$angleDeg")

        // Apex
        val apexXScaled = if (abs(a) > 1e-10) -b / (2.0 * a) else 500.0
        val apexYScaled = a * apexXScaled.pow(2) + b * apexXScaled + c
        val apexX       = (apexXScaled / 1000.0).toFloat().coerceIn(0f, 1f)
        val apexY       = (apexYScaled / 1000.0).toFloat().coerceIn(0f, 1f)

        android.util.Log.d("FITTER",
            "SUCCESS angle=${angleDeg}° R²=$rSquared apex=($apexX,$apexY)")

        return QuadraticCurve(
            a             = a,
            b             = b,
            c             = c,
            rSquared      = rSquared,
            entryAngleDeg = angleDeg,
            apexNormX     = apexX,
            apexNormY     = apexY
        )
    }

    fun angleQuality(angleDeg: Float): Pair<String, androidx.compose.ui.graphics.Color> {
        val a = abs(angleDeg)
        return when {
            a >= 45f && a <= 60f -> "Ideal"     to androidx.compose.ui.graphics.Color(0xFF34C759)
            a >= 35f && a < 45f  -> "Flat"      to androidx.compose.ui.graphics.Color(0xFFFF9500)
            a > 60f && a <= 75f  -> "Steep"     to androidx.compose.ui.graphics.Color(0xFFFF9500)
            a < 35f              -> "Too Flat"   to androidx.compose.ui.graphics.Color(0xFFFF3B30)
            else                 -> "Too Steep"  to androidx.compose.ui.graphics.Color(0xFFFF3B30)
        }
    }
}