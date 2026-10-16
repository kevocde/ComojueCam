package dev.polyrics.comojuecam.telemetry

import android.content.Context
import java.security.SecureRandom

class TelemetrySettings(context: Context) {
    private val prefs = context.getSharedPreferences("telemetry", Context.MODE_PRIVATE)
    var enabled: Boolean = prefs.getBoolean(KEY_ENABLED, false)
    var sendIntervalMinutes: Int = prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL).coerceIn(1, 5)
    var sampleSeconds: Int = prefs.getInt(KEY_SAMPLE, DEFAULT_SAMPLE).coerceIn(2, 30)
    val token: String = prefs.getString(KEY_TOKEN, null)
        ?: generateToken().also { prefs.edit().putString(KEY_TOKEN, it).apply() }

    fun save() {
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_INTERVAL, sendIntervalMinutes)
            .putInt(KEY_SAMPLE, sampleSeconds)
            .apply()
    }

    private fun generateToken(): String {
        val chars = ('a'..'z') + ('0'..'9')
        val random = SecureRandom()
        fun part() = (1..3).map { chars[random.nextInt(chars.size)] }.joinToString("")
        return "${part()}-${part()}"
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_INTERVAL = "send_interval_minutes"
        const val KEY_SAMPLE = "sample_seconds"
        const val KEY_TOKEN = "token"
        const val DEFAULT_INTERVAL = 1
        const val DEFAULT_SAMPLE = 10
    }
}
