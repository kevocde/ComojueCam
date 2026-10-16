package dev.polyrics.comojuecam.telemetry

class TelemetrySender(private val endpoint: String) {
    fun send(text: String): Boolean = try {
        val connection = WebSocketConnection()
        try {
            connection.open(endpoint)
            connection.sendText(text)
            true
        } finally {
            connection.close()
        }
    } catch (_: Exception) {
        false
    }
}
