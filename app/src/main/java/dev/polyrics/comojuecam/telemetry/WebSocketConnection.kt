package dev.polyrics.comojuecam.telemetry

import android.util.Base64
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import javax.net.ssl.SSLContext

class WebSocketConnection {
    private var socket: Socket? = null
    private var output: java.io.OutputStream? = null
    @Volatile var isOpen = false
        private set

    fun open(endpoint: String) {
        val uri = URI(endpoint)
        val secure = uri.scheme.equals("wss", ignoreCase = true)
        val port = if (uri.port > 0) uri.port else if (secure) 443 else 80
        val created = if (secure) {
            SSLContext.getDefault().socketFactory.createSocket(uri.host, port)
        } else {
            Socket(uri.host, port)
        }
        created.tcpNoDelay = true
        created.soTimeout = HANDSHAKE_TIMEOUT_MS
        handshake(created, uri)
        socket = created
        output = created.getOutputStream()
        isOpen = true
    }

    fun sendText(text: String) {
        val stream = output ?: throw IOException("WebSocket is closed")
        val payload = text.toByteArray()
        val mask = ByteArray(4).also { SecureRandom().nextBytes(it) }
        val masked = ByteArray(payload.size) {
            (payload[it].toInt() xor mask[it % 4].toInt()).toByte()
        }
        synchronized(this) {
            stream.write(header(payload.size) + mask)
            stream.write(masked)
            stream.flush()
        }
    }

    fun close() {
        try {
            output?.write(closeFrame())
        } catch (_: IOException) {
        }
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        output = null
        isOpen = false
    }

    private fun handshake(socket: Socket, uri: URI) {
        val key = Base64.encodeToString(
            ByteArray(16).also { SecureRandom().nextBytes(it) },
            Base64.NO_WRAP
        )
        val path = uri.rawPath.ifEmpty { "/" } + if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
        val request = "GET $path HTTP/1.1\r\n" +
            "Host: ${uri.host}\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $key\r\n" +
            "Sec-WebSocket-Version: 13\r\n\r\n"
        socket.getOutputStream().apply {
            write(request.toByteArray())
            flush()
        }
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val status = reader.readLine() ?: throw IOException("Empty handshake response")
        if (!status.startsWith("HTTP/1.1 101")) throw IOException("Bad handshake: $status")
        while (reader.readLine()?.isNotEmpty() == true) Unit
        socket.soTimeout = 0
    }

    private fun header(length: Int): ByteArray {
        val base = byteArrayOf(0x81.toByte(), 0)
        return when {
            length < 126 -> byteArrayOf(base[0], (length or 0x80).toByte())
            length < 65536 -> byteArrayOf(
                base[0], (126 or 0x80).toByte(),
                (length shr 8).toByte(), length.toByte()
            )
            else -> byteArrayOf(base[0], (127 or 0x80).toByte()) +
                ByteArray(4) + byteArrayOf(
                    (length shr 24).toByte(), (length shr 16).toByte(),
                    (length shr 8).toByte(), length.toByte()
                )
        }
    }

    private fun closeFrame(): ByteArray {
        val mask = ByteArray(4).also { SecureRandom().nextBytes(it) }
        return byteArrayOf(0x88.toByte(), 0x80.toByte()) + mask
    }

    private companion object {
        const val HANDSHAKE_TIMEOUT_MS = 8000
    }
}
