package com.example.myapplication.analysis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SessionStorage {

    private const val FILENAME = "shot_sessions.json"

    private fun getFile(context: Context): File {
        return File(context.filesDir, FILENAME)
    }

    fun saveSessions(context: Context, sessions: List<ShotSession>) {
        val jsonArray = JSONArray()
        sessions.forEach { jsonArray.put(it.toJson()) }
        getFile(context).writeText(jsonArray.toString())
    }

    fun loadSessions(context: Context): List<ShotSession> {
        val file = getFile(context)
        if (!file.exists()) return emptyList()

        return try {
            val jsonArray = JSONArray(file.readText())
            (0 until jsonArray.length()).map {
                ShotSession.fromJson(jsonArray.getJSONObject(it))
            }.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addSession(context: Context, session: ShotSession) {
        val sessions = loadSessions(context).toMutableList()
        sessions.add(0, session)
        saveSessions(context, sessions)
    }

    fun deleteSession(context: Context, sessionId: String) {
        val sessions = loadSessions(context).filter { it.id != sessionId }
        saveSessions(context, sessions)
    }
}
