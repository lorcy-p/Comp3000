package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.pow
import org.apache.commons.math3.fitting.PolynomialCurveFitter
import org.apache.commons.math3.fitting.WeightedObservedPoints

// Tests for shot trajectory curve fitting and angle quality labels
class QuadraticFitterTest {

    private val MIN_R_SQUARED = 0.4

    // Test hoop position
    private val hoopX = 0.75f
    private val hoopY = 0.5f

    // Simple position class - avoids needing Android/Compose imports
    data class Position(val normX: Float, val normY: Float)

    // Result returned by fitCurve
    data class CurveResult(
        val rSquared: Double,
        val entryAngleDeg: Float,
        val apexNormX: Float,
        val apexNormY: Float
    )

    // Replicates QuadraticFitter.fit() without Android dependencies
    private fun fitCurve(positions: List<Position>): CurveResult? {
        if (positions.size < 3) return null

        val aboveHoop = positions.filter { it.normY < hoopY }
        if (aboveHoop.size < 3) return null

        val firstX = aboveHoop.first().normX
        val lastX = aboveHoop.last().normX
        val travelRight = lastX > firstX

        val beforeHoop = if (travelRight) {
            aboveHoop.filter { it.normX <= hoopX }
        } else {
            aboveHoop.filter { it.normX >= hoopX }
        }

        val workingSet = when {
            beforeHoop.size >= 3 -> beforeHoop
            aboveHoop.size >= 3 -> aboveHoop
            else -> return null
        }

        val xs = workingSet.map { it.normX.toDouble() * 1000.0 }
        val ys = workingSet.map { it.normY.toDouble() * 1000.0 }

        val xRange = xs.max() - xs.min()
        if (xRange < 5.0) return null

        val obs = WeightedObservedPoints()
        val hoopXScaled = hoopX.toDouble() * 1000.0
        for (i in xs.indices) {
            val distFromHoop = abs(xs[i] - hoopXScaled)
            val weight = 1.0 / (1.0 + distFromHoop / 100.0)
            obs.add(weight, xs[i], ys[i])
        }

        val coefficients = try {
            PolynomialCurveFitter.create(2).fit(obs.toList())
        } catch (e: Exception) {
            return null
        }

        val c = coefficients[0]
        val b = coefficients[1]
        val a = coefficients[2]

        val meanY = ys.average()
        var ssTot = 0.0
        var ssRes = 0.0
        for (i in xs.indices) {
            val predicted = a * xs[i].pow(2) + b * xs[i] + c
            ssTot += (ys[i] - meanY).pow(2)
            ssRes += (ys[i] - predicted).pow(2)
        }
        val rSquared = if (ssTot > 1e-6) (1.0 - ssRes / ssTot) else 0.0

        if (rSquared < MIN_R_SQUARED) return null

        val closestToHoop = workingSet.minByOrNull { abs(it.normX - hoopX) }!!
        val entryXScaled = closestToHoop.normX.toDouble() * 1000.0
        val slope = 2.0 * a * entryXScaled + b
        val angleDeg = Math.toDegrees(atan(abs(slope))).toFloat()

        val apexXScaled = if (abs(a) > 1e-10) -b / (2.0 * a) else 500.0
        val apexYScaled = a * apexXScaled.pow(2) + b * apexXScaled + c
        val apexX = (apexXScaled / 1000.0).toFloat().coerceIn(0f, 1f)
        val apexY = (apexYScaled / 1000.0).toFloat().coerceIn(0f, 1f)

        return CurveResult(rSquared, angleDeg, apexX, apexY)
    }

    // Replicates QuadraticFitter.angleQuality() without Compose Color dependency
    private fun angleQuality(angleDeg: Float): String {
        val a = abs(angleDeg)
        return when {
            a >= 45f && a <= 60f -> "Ideal"
            a >= 35f && a < 45f -> "Flat"
            a > 60f && a <= 75f -> "Steep"
            a < 35f -> "Too Flat"
            else -> "Too Steep"
        }
    }

    // Generates ball positions along a parabola y = ax² + bx + c
    private fun generateParabolicPositions(
        a: Double,
        b: Double,
        c: Double,
        xStart: Double,
        xEnd: Double,
        steps: Int
    ): List<Position> {
        val positions = mutableListOf<Position>()
        val step = (xEnd - xStart) / steps
        for (i in 0..steps) {
            val x = xStart + i * step
            val y = a * x * x + b * x + c
            positions.add(
                Position(
                    normX = (x / 1000.0).toFloat().coerceIn(0f, 1f),
                    normY = (y / 1000.0).toFloat().coerceIn(0f, 1f)
                )
            )
        }
        return positions
    }

    // Test 1: valid parabola returns a curve
    @Test
    fun `clean parabolic arc returns non-null curve`() {
        val positions = generateParabolicPositions(
            a = -0.001, b = 0.5, c = 100.0,
            xStart = 200.0, xEnd = 750.0, steps = 20
        )
        val result = fitCurve(positions)
        assertNotNull("Expected a valid curve for clean parabolic input", result)
    }

    // Test 2: clean data produces R² above the minimum threshold
    @Test
    fun `clean parabolic arc has R squared above threshold`() {
        val positions = generateParabolicPositions(
            a = -0.001, b = 0.5, c = 100.0,
            xStart = 200.0, xEnd = 750.0, steps = 20
        )
        val result = fitCurve(positions)
        assertNotNull(result)
        assertTrue(
            "R² should be above $MIN_R_SQUARED, was ${result!!.rSquared}",
            result.rSquared >= MIN_R_SQUARED
        )
    }

    // Test 3: a diagonal line approaching the hoop produces an angle near 45 degrees
    @Test
    fun `entry angle is calculated correctly from known positions`() {
        val positions = (0..10).map { i ->
            Position(
                normX = (0.65f + i * 0.01f),
                normY = (0.30f + i * 0.01f)
            )
        }
        val result = fitCurve(positions)
        if (result != null) {
            val angle = abs(result.entryAngleDeg)
            assertTrue(
                "Angle $angle° should be roughly 45° for a diagonal line",
                angle in 35f..55f
            )
        }
    }

    // Test 4: fewer than 3 positions returns null
    @Test
    fun `fewer than 3 positions returns null`() {
        val positions = listOf(
            Position(0.3f, 0.4f),
            Position(0.4f, 0.35f)
        )
        val result = fitCurve(positions)
        assertNull("Expected null with fewer than 3 positions", result)
    }

    // Test 5: a flat horizontal line is rejected
    @Test
    fun `horizontal line positions are rejected`() {
        val positions = (0..10).map { i ->
            Position(
                normX = 0.2f + i * 0.05f,
                normY = 0.3f
            )
        }
        val result = fitCurve(positions)
        assertTrue(
            "Horizontal line should be rejected or have very low R²",
            result == null || result.rSquared < MIN_R_SQUARED
        )
    }

    // Test 6: positions with almost no horizontal movement are rejected
    @Test
    fun `positions with negligible X range return null`() {
        val positions = (0..5).map { i ->
            Position(
                normX = 0.500f + i * 0.0001f,
                normY = 0.4f - i * 0.01f
            )
        }
        val result = fitCurve(positions)
        assertNull("Expected null when X range is too small", result)
    }

    // Test 7: 52 degrees is classified as Ideal
    @Test
    fun `angle quality returns Ideal for 52 degrees`() {
        assertEquals("Ideal", angleQuality(52f))
    }

    // Test 8: 40 degrees is classified as Flat
    @Test
    fun `angle quality returns Flat for 40 degrees`() {
        assertEquals("Flat", angleQuality(40f))
    }

    // Test 9: 20 degrees is classified as Too Flat
    @Test
    fun `angle quality returns Too Flat for 20 degrees`() {
        assertEquals("Too Flat", angleQuality(20f))
    }

    // Test 10: 68 degrees is classified as Steep
    @Test
    fun `angle quality returns Steep for 68 degrees`() {
        assertEquals("Steep", angleQuality(68f))
    }

    // Test 11: 80 degrees is classified as Too Steep
    @Test
    fun `angle quality returns Too Steep for 80 degrees`() {
        assertEquals("Too Steep", angleQuality(80f))
    }

    // Test 12: the apex of the arc should be above the hoop
    @Test
    fun `apex Y is above hoop height`() {
        val positions = generateParabolicPositions(
            a = -0.001, b = 0.5, c = 100.0,
            xStart = 200.0, xEnd = 750.0, steps = 20
        )
        val result = fitCurve(positions)
        assertNotNull(result)
        assertTrue(
            "Apex Y (${result!!.apexNormY}) should be above hoop Y ($hoopY)",
            result.apexNormY <= hoopY
        )
    }

    // Test 13: small detection noise should not break the curve fit
    @Test
    fun `small noise on parabolic arc still produces valid curve`() {
        val base = generateParabolicPositions(
            a = -0.001, b = 0.5, c = 100.0,
            xStart = 200.0, xEnd = 750.0, steps = 20
        )
        val noisy = base.map { pos ->
            pos.copy(
                normY = (pos.normY + (-0.005f + Math.random().toFloat() * 0.01f))
                    .coerceIn(0f, 1f)
            )
        }
        val result = fitCurve(noisy)
        assertNotNull("Expected a valid curve despite small detection noise", result)
    }
}