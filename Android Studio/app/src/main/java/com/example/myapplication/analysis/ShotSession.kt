package com.example.myapplication.analysis

import org.json.JSONArray
import org.json.JSONObject

data class ShotSession(
    val id: String = System.currentTimeMillis().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val shotType: String = "free_throw", // "free_throw" or "three_pointer"
    val shotAngles: List<Double> = emptyList(),
    val meanAngle: Double = 0.0,
    val stdDeviation: Double = 0.0,
    val shotCount: Int = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("timestamp", timestamp)
            put("shotType", shotType)
            put("shotAngles", JSONArray(shotAngles))
            put("meanAngle", meanAngle)
            put("stdDeviation", stdDeviation)
            put("shotCount", shotCount)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ShotSession {
            val anglesArray = json.getJSONArray("shotAngles")
            val angles = (0 until anglesArray.length()).map { anglesArray.getDouble(it) }
            return ShotSession(
                id = json.getString("id"),
                timestamp = json.getLong("timestamp"),
                shotType = json.optString("shotType", "free_throw"),
                shotAngles = angles,
                meanAngle = json.getDouble("meanAngle"),
                stdDeviation = json.getDouble("stdDeviation"),
                shotCount = json.getInt("shotCount")
            )
        }
    }
}
