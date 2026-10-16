package dev.polyrics.comojuecam.telemetry

class TelemetrySender(private val endpoint: String) {
    private var connection: WebSocketConnection? = null

    fun send(text: String): Boolean {
        if (attempt(text)) return true
        reconnect()
        return attempt(text)
    }

    fun close() {
        connection?.close()
        connection = null
    }

    private fun attempt(text: String) = try {
        val active = connection?.takeIf { it.isOpen }
            ?: WebSocketConnection().apply { open(endpoint) }.also { connection = it }
        active.sendText(text)
        true
    } catch (_: Exception) {
        false
    }

    private fun reconnect() {
        connection?.close()
        connection = null
    }
}
