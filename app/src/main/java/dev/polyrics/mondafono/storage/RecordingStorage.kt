package dev.polyrics.mondafono.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import dev.polyrics.mondafono.recording.LoopRecording

class RecordingStorage(private val context: Context) {
    fun requiresLegacyPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    fun save(recording: LoopRecording): Boolean {
        return if (requiresLegacyPermission()) saveLegacy(recording) else saveWithMediaStore(recording)
    }

    private fun saveLegacy(recording: LoopRecording): Boolean {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "Mondafono"
        )
        directory.mkdirs()
        recording.saveTo(File(directory, recording.fileName))
        return true
    }

    private fun saveWithMediaStore(recording: LoopRecording): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, recording.fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Mondafono")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val descriptor = resolver.openFileDescriptor(uri, "w")
                ?: error("Unable to open video destination")
            descriptor.use { recording.saveTo(it.fileDescriptor) }
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }, null, null)
            true
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
