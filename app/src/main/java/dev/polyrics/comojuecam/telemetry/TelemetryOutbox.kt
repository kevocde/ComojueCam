package dev.polyrics.comojuecam.telemetry

import android.content.Context
import java.io.File

class TelemetryOutbox(context: Context) {
    private val directory = File(context.filesDir, "telemetry_outbox").apply { mkdirs() }
    private var sequence = 0

    fun hasPending(): Boolean = pendingFiles().isNotEmpty()

    fun save(json: String) {
        val name = "out-${System.currentTimeMillis()}-${sequence++}.json"
        val target = File(directory, name)
        val temporary = File(directory, "$name.tmp")
        temporary.writeText(json)
        if (!temporary.renameTo(target)) {
            temporary.delete()
            target.writeText(json)
        }
        removeOldestOverflow()
    }

    fun readPending(): List<PendingPacket> =
        pendingFiles().map { PendingPacket(it, it.readText()) }

    fun delete(packet: PendingPacket) {
        packet.file.delete()
    }

    private fun pendingFiles(): List<File> =
        directory
            .listFiles { file -> file.name.startsWith("out-") && file.name.endsWith(".json") }
            ?.sortedBy { it.name }
            ?: emptyList()

    private fun removeOldestOverflow() {
        val files = pendingFiles()
        if (files.size <= MAX_FILES) return
        files.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    data class PendingPacket(val file: File, val content: String)

    private companion object {
        const val MAX_FILES = 288
    }
}
