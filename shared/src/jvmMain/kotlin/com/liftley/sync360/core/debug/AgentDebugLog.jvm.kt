package com.liftley.sync360.core.debug

import java.io.File

private const val DEBUG_LOG_PATH = "c:\\Users\\4444444\\IdeaProjects\\Sync360\\debug-ab564f.log"
private const val INGEST_URL = "http://127.0.0.1:7702/ingest/533aacb6-cd8a-49e8-82c0-1ed213920a7a"
private const val SESSION_ID = "ab564f"

actual fun agentDebugLog(
    location: String,
    message: String,
    hypothesisId: String,
    data: Map<String, String>,
    runId: String
) {
    val line = buildNdjsonLine(location, message, hypothesisId, data, runId)
    // #region agent log
    try {
        File(DEBUG_LOG_PATH).appendText(line + "\n")
    } catch (_: Exception) {
    }
    Thread {
        try {
            val conn = java.net.URL(INGEST_URL).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-Debug-Session-Id", SESSION_ID)
            conn.doOutput = true
            conn.outputStream.use { it.write(line.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {
        }
    }.start()
    // #endregion
}

private fun buildNdjsonLine(
    location: String,
    message: String,
    hypothesisId: String,
    data: Map<String, String>,
    runId: String
): String {
    val dataJson = data.entries.joinToString(",") { (k, v) ->
        """"${escapeJson(k)}":"${escapeJson(v)}""""
    }
    val dataField = if (dataJson.isEmpty()) "{}" else "{$dataJson}"
    return """{"sessionId":"$SESSION_ID","hypothesisId":"$hypothesisId","location":"${escapeJson(location)}","message":"${escapeJson(message)}","data":$dataField,"timestamp":${System.currentTimeMillis()},"runId":"$runId"}"""
}

private fun escapeJson(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
