package dev.polyrics.mondafono

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.util.Range
import android.provider.MediaStore
import java.io.File
import java.io.FileDescriptor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.ArrayDeque

class MainActivity : Activity() {
    private lateinit var camera: CameraController
    private lateinit var overlay: RetroOverlay
    private lateinit var savePanel: SavePanel
    private var pendingRecording: LoopRecording? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(8192 or 1024)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val preview = SurfaceView(this)
        overlay = RetroOverlay(this)
        val recordingSettings = RecordingSettings()
        val settings = SettingsPanel(this, recordingSettings)
        savePanel = SavePanel(this)
        settings.visibility = View.GONE
        savePanel.visibility = View.GONE
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(settings, FrameLayout.LayoutParams(-1, -1))
        root.addView(savePanel, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        camera = CameraController(this, preview.holder, recordingSettings) { recording ->
            overlay.setRecording(recording)
            overlay.settingsEnabled = !recording
            settings.isEnabled = !recording
            if (recording) settings.visibility = View.GONE
            overlay.invalidate()
        }
        camera.onCapabilitiesChanged = { runOnUiThread(settings::syncFromSettings) }
        camera.onRecordingReady = { recording -> runOnUiThread { showSavePanel(recording) } }
        overlay.onRecord = { camera.toggleRecording() }
        overlay.onSettings = { settings.visibility = View.VISIBLE }
        settings.onApply = { camera.applySettings() }
        settings.onClose = { settings.visibility = View.GONE }
        savePanel.onSave = { savePendingRecording() }
        savePanel.onDiscard = { discardPendingRecording() }
        preview.holder.addCallback(camera)

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 10)
        }
    }

    override fun onRequestPermissionsResult(request: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(request, permissions, results)
        if (request == 10 && results.firstOrNull() != PackageManager.PERMISSION_GRANTED) finish()
        if (request == 20 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            savePendingRecording()
        }
    }

    override fun onDestroy() {
        if (::camera.isInitialized) camera.close()
        pendingRecording?.release()
        super.onDestroy()
    }

    private fun showSavePanel(recording: LoopRecording) {
        pendingRecording?.release()
        pendingRecording = recording
        savePanel.durationSeconds = recording.durationSeconds
        savePanel.visibility = View.VISIBLE
    }

    private fun savePendingRecording() {
        val recording = pendingRecording ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 20)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(recording)
        } else {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "Mondafono"
            )
            directory.mkdirs()
            recording.saveTo(File(directory, recording.fileName))
            clearPendingRecording()
        }
    }

    private fun saveWithMediaStore(recording: LoopRecording) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, recording.fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Mondafono")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
        try {
            val descriptor = resolver.openFileDescriptor(uri, "w")
                ?: error("Unable to open video destination")
            descriptor.use { recording.saveTo(it.fileDescriptor) }
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }, null, null)
            clearPendingRecording()
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
        }
    }

    private fun clearPendingRecording() {
        pendingRecording?.release()
        pendingRecording = null
        savePanel.visibility = View.GONE
    }

    private fun discardPendingRecording() {
        clearPendingRecording()
    }
}

private class RetroOverlay(context: Context) : View(context) {
    private var recording = false
    var settingsEnabled = true
    var onRecord: (() -> Unit)? = null
    var onSettings: (() -> Unit)? = null
    private var recordingStartedAt = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val timerRefresh = object : Runnable {
        override fun run() {
            if (recording) {
                invalidate()
                postDelayed(this, 500L)
            }
        }
    }

    fun setRecording(value: Boolean) {
        if (recording == value) return
        recording = value
        if (value) {
            recordingStartedAt = SystemClock.elapsedRealtime()
            post(timerRefresh)
        } else {
            removeCallbacks(timerRefresh)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val amber = Color.rgb(255, 176, 0)

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(220, 0, 0, 0)
        canvas.drawRect(width - 150f, 24f, width - 24f, 70f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = if (settingsEnabled) amber else Color.DKGRAY
        canvas.drawRect(width - 150f, 24f, width - 24f, 70f, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 15f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("CONFIG", width - 87f, 53f, paint)

        val buttonWidth = 190f
        val buttonHeight = 64f
        val left = (width - buttonWidth) / 2f
        val top = height - buttonHeight - 28f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(220, 0, 0, 0)
        canvas.drawRect(left, top, left + buttonWidth, top + buttonHeight, paint)
        paint.style = Paint.Style.STROKE
        paint.color = if (recording) Color.RED else amber
        paint.strokeWidth = 3f
        canvas.drawRect(left, top, left + buttonWidth, top + buttonHeight, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 22f
        paint.textAlign = Paint.Align.CENTER
        paint.color = if (recording) Color.RED else amber
        canvas.drawText(if (recording) "STOP" else "REC", width / 2f, top + 40f, paint)

        if (recording) {
            val elapsedMillis = SystemClock.elapsedRealtime() - recordingStartedAt
            val elapsedSeconds = elapsedMillis / 1000L
            paint.textSize = 16f
            paint.textAlign = Paint.Align.LEFT
            if ((elapsedMillis / 500L) % 2L == 0L) {
                canvas.drawText("* RECORDING", 32f, 48f, paint)
            }
            canvas.drawText(formatElapsedTime(elapsedSeconds), 32f, 76f, paint)
        }
    }

    private fun formatElapsedTime(seconds: Long): String {
        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            seconds / 3_600,
            seconds / 60 % 60,
            seconds % 60
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            if (settingsEnabled && event.x > width - 170f && event.y < 90f) {
                onSettings?.invoke()
            } else if (event.y > height - 110f &&
                event.x > width / 2f - 110f && event.x < width / 2f + 110f) {
                onRecord?.invoke()
            }
        }
        return true
    }
}

private data class VideoSize(val width: Int, val height: Int) {
    override fun toString() = "${width}x${height}"
}

private class RecordingSettings {
    var supportedFrameRates = listOf(30)
    var frameRate = 30
    var supportedSizes = emptyList<VideoSize>()
    var size = VideoSize(1280, 720)
    var loopDurationSeconds = 30
}

private class SettingsPanel(
    context: Context,
    private val settings: RecordingSettings
) : View(context) {
    var onClose: (() -> Unit)? = null
    var onApply: (() -> Unit)? = null
    private val tabs = listOf("REC")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private var pendingFrameRate = settings.frameRate
    private var pendingSize = settings.size
    private var pendingLoopDuration = settings.loopDurationSeconds

    fun syncFromSettings() {
        pendingFrameRate = settings.frameRate
        pendingSize = settings.size
        pendingLoopDuration = settings.loopDurationSeconds
        invalidate()
    }

    private fun hasChanges() = pendingFrameRate != settings.frameRate ||
        pendingSize != settings.size || pendingLoopDuration != settings.loopDurationSeconds

    private fun applyChanges() {
        settings.frameRate = pendingFrameRate
        settings.size = pendingSize
        settings.loopDurationSeconds = pendingLoopDuration
        onApply?.invoke()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val amber = Color.rgb(255, 176, 0)
        canvas.drawColor(Color.argb(190, 0, 0, 0))

        val panelLeft = width * 0.12f
        val panelTop = height * 0.12f
        val panelRight = width * 0.88f
        val panelBottom = height * 0.88f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(8, 8, 8)
        canvas.drawRect(panelLeft, panelTop, panelRight, panelBottom, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = amber
        canvas.drawRect(panelLeft, panelTop, panelRight, panelBottom, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 22f
        canvas.drawText("CONFIG", panelLeft + 28f, panelTop + 42f, paint)

        val closeLeft = panelRight - 112f
        val closeTop = panelTop + 18f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(closeLeft, closeTop, panelRight - 18f, closeTop + 38f, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 14f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("CLOSE", (closeLeft + panelRight - 18f) / 2f, closeTop + 25f, paint)
        if (hasChanges()) {
            val applyRight = closeLeft - 10f
            val applyLeft = applyRight - 96f
            paint.style = Paint.Style.STROKE
            canvas.drawRect(applyLeft, closeTop, applyRight, closeTop + 38f, paint)
            paint.style = Paint.Style.FILL
            canvas.drawText("APPLY", (applyLeft + applyRight) / 2f, closeTop + 25f, paint)
        }

        drawTabs(canvas, panelLeft, panelTop + 72f, panelRight, amber)
    }

    private fun drawTabs(canvas: Canvas, left: Float, top: Float, right: Float, amber: Int) {
        val tabWidth = 92f
        tabs.forEachIndexed { index, title ->
            val tabLeft = left + 20f + index * (tabWidth + 8f)
            paint.style = Paint.Style.FILL
            paint.color = amber
            canvas.drawRect(tabLeft, top, tabLeft + tabWidth, top + 38f, paint)
            paint.color = Color.BLACK
            paint.textSize = 15f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(title, tabLeft + tabWidth / 2f, top + 25f, paint)
        }

        drawFrameRateControl(canvas, top + 70f, left)
        drawQualityControl(canvas, top + 126f, left)
        drawLoopControl(canvas, top + 182f, left)
    }

    private fun drawFrameRateControl(canvas: Canvas, top: Float, left: Float) {
        drawLabel(canvas, "FPS:", top, left)
        val controlLeft = left + 160f
        drawButton(canvas, "-", controlLeft, top, controlLeft + 42f)
        drawValue(canvas, pendingFrameRate.toString(), controlLeft + 50f, top, controlLeft + 146f)
        drawButton(canvas, "+", controlLeft + 154f, top, controlLeft + 196f)
    }

    private fun drawQualityControl(canvas: Canvas, top: Float, left: Float) {
        drawLabel(canvas, "QUALITY:", top, left)
        val controlLeft = left + 160f
        drawValue(canvas, pendingSize.toString(), controlLeft, top, controlLeft + 96f)
        drawButton(canvas, "CHANGE", controlLeft + 104f, top, controlLeft + 200f)
    }

    private fun drawLoopControl(canvas: Canvas, top: Float, left: Float) {
        drawLabel(canvas, "LOOP:", top, left)
        val controlLeft = left + 160f
        drawButton(canvas, "-", controlLeft, top, controlLeft + 42f)
        drawValue(canvas, "${pendingLoopDuration}s", controlLeft + 50f, top, controlLeft + 146f)
        drawButton(canvas, "+", controlLeft + 154f, top, controlLeft + 196f)
    }

    private fun drawLabel(canvas: Canvas, label: String, top: Float, left: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 176, 0)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 16f
        canvas.drawText(label, left + 28f, top + 28f, paint)
    }

    private fun drawButton(canvas: Canvas, label: String, left: Float, top: Float, right: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(left, top, right, top + 40f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = if (label == "CHANGE") 11f else 20f
        canvas.drawText(label, (left + right) / 2f, top + 27f, paint)
    }

    private fun drawValue(canvas: Canvas, value: String, left: Float, top: Float, right: Float) {
        paint.style = Paint.Style.STROKE
        canvas.drawRect(left, top, right, top + 40f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 15f
        canvas.drawText(value, (left + right) / 2f, top + 26f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val panelTop = height * 0.12f
            val panelRight = width * 0.88f
            val closeLeft = panelRight - 112f
            val closeTop = panelTop + 18f
            if (event.x in closeLeft..panelRight - 18f && event.y in closeTop..closeTop + 38f) {
                syncFromSettings()
                onClose?.invoke()
            } else if (hasChanges() && event.x in closeLeft - 106f..closeLeft - 10f &&
                event.y in closeTop..closeTop + 38f) {
                applyChanges()
            } else if (event.y in panelTop + 72f + 126f..panelTop + 72f + 126f + 40f) {
                val panelLeft = width * 0.12f
                val controlLeft = panelLeft + 160f
                if (event.x in controlLeft + 104f..controlLeft + 200f) {
                    if (settings.supportedSizes.isNotEmpty()) {
                        val index = settings.supportedSizes.indexOf(pendingSize)
                        val nextIndex = if (index < 0 || index == settings.supportedSizes.lastIndex) {
                            0
                        } else {
                            index + 1
                        }
                        pendingSize = settings.supportedSizes[nextIndex]
                    }
                    invalidate()
                }
            } else if (event.y in panelTop + 72f + 70f..panelTop + 72f + 70f + 40f) {
                val panelLeft = width * 0.12f
                val controlLeft = panelLeft + 160f
                if (event.x in controlLeft..controlLeft + 42f) {
                    val index = settings.supportedFrameRates.indexOf(pendingFrameRate)
                    pendingFrameRate = settings.supportedFrameRates[(index - 1).coerceAtLeast(0)]
                    invalidate()
                } else if (event.x in controlLeft + 154f..controlLeft + 196f) {
                    val index = settings.supportedFrameRates.indexOf(pendingFrameRate)
                    pendingFrameRate = settings.supportedFrameRates[(index + 1).coerceAtMost(
                        settings.supportedFrameRates.lastIndex
                    )]
                    invalidate()
                }
            } else if (event.y in panelTop + 72f + 182f..panelTop + 72f + 182f + 40f) {
                val panelLeft = width * 0.12f
                val controlLeft = panelLeft + 160f
                if (event.x in controlLeft..controlLeft + 42f) {
                    pendingLoopDuration = (pendingLoopDuration - 10).coerceAtLeast(20)
                    invalidate()
                } else if (event.x in controlLeft + 154f..controlLeft + 196f) {
                    pendingLoopDuration = (pendingLoopDuration + 10).coerceAtMost(120)
                    invalidate()
                }
            }
        }
        return true
    }
}

private class SavePanel(context: Context) : View(context) {
    var durationSeconds = 30
    var onSave: (() -> Unit)? = null
    var onDiscard: (() -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }

    override fun onDraw(canvas: Canvas) {
        val amber = Color.rgb(255, 176, 0)
        canvas.drawColor(Color.argb(200, 0, 0, 0))

        val left = width * 0.24f
        val top = height * 0.32f
        val right = width * 0.76f
        val bottom = height * 0.68f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(8, 8, 8)
        canvas.drawRect(left, top, right, bottom, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = amber
        canvas.drawRect(left, top, right, bottom, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 22f
        canvas.drawText("SAVE RECORDING?", (left + right) / 2f, top + 48f, paint)
        paint.textSize = 16f
        canvas.drawText("LAST ${durationSeconds}s", (left + right) / 2f, top + 84f, paint)

        drawButton(canvas, "SAVE", left + 42f, bottom - 62f, left + 152f, amber)
        drawButton(canvas, "DISCARD", right - 152f, bottom - 62f, right - 42f, amber)
    }

    private fun drawButton(canvas: Canvas, label: String, left: Float, top: Float, right: Float, color: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = color
        canvas.drawRect(left, top, right, top + 40f, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 14f
        canvas.drawText(label, (left + right) / 2f, top + 26f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val left = width * 0.24f
            val top = height * 0.32f
            val right = width * 0.76f
            val buttonTop = height * 0.68f - 62f
            if (event.y in buttonTop..buttonTop + 40f) {
                when {
                    event.x in left + 42f..left + 152f -> onSave?.invoke()
                    event.x in right - 152f..right - 42f -> onDiscard?.invoke()
                }
            }
        }
        return true
    }
}

private class CameraController(
    private val context: Context,
    private val holder: SurfaceHolder,
    private val settings: RecordingSettings,
    private val state: (Boolean) -> Unit
) : SurfaceHolder.Callback {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val thread = HandlerThread("camera").apply { start() }
    private val handler = Handler(thread.looper)
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var recorder: LoopVideoRecorder? = null
    private var recording = false
    private var cameraId = ""
    private var frameRateRanges = emptyList<Range<Int>>()
    var onCapabilitiesChanged: (() -> Unit)? = null
    var onRecordingReady: ((LoopRecording) -> Unit)? = null

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) open()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (recording) stopRecording(false, false)
        closeCamera()
    }

    fun toggleRecording() {
        if (recording) stopRecording() else startRecording()
    }

    fun applySettings() {
        handler.post {
            if (camera == null || recording || !holder.surface.isValid) return@post
            session?.close()
            createSession(null)
        }
    }

    private fun open() {
        try {
            cameraId = manager.cameraIdList.first { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            }
            updateCapabilities()
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    camera = device
                    createSession(null)
                }

                override fun onDisconnected(device: CameraDevice) = device.close()
                override fun onError(device: CameraDevice, error: Int) = device.close()
            }, handler)
        } catch (_: CameraAccessException) {
        }
    }

    private fun updateCapabilities() {
        val characteristics = manager.getCameraCharacteristics(cameraId)
        frameRateRanges = characteristics.get(
            CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
        ).orEmpty().toList()
        settings.supportedFrameRates = listOf(15, 30, 60).filter { fps ->
            frameRateRanges.any { range -> fps in range.lower..range.upper }
        }.ifEmpty { listOf(30) }
        if (settings.frameRate !in settings.supportedFrameRates) {
            settings.frameRate = settings.supportedFrameRates.first()
        }

        val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(SurfaceHolder::class.java)
            ?.map { VideoSize(it.width, it.height) }
            ?.distinct()
            ?.sortedByDescending { it.width * it.height }
            .orEmpty()
        settings.supportedSizes = sizes
        if (settings.size !in sizes && sizes.isNotEmpty()) {
            settings.size = sizes.firstOrNull { it == VideoSize(1280, 720) } ?: sizes.first()
        }
        onCapabilitiesChanged?.invoke()
    }

    private fun startRecording() {
        if (camera == null || recording) return
        val size = settings.size
        recorder = LoopVideoRecorder(
            size.width,
            size.height,
            settings.frameRate,
            settings.loopDurationSeconds
        )
        recorder!!.start()
        session?.close()
        createSession(recorder!!.inputSurface)
        recording = true
        state(true)
    }

    private fun stopRecording(resumePreview: Boolean = true, askToSave: Boolean = true) {
        if (!recording) return
        recording = false
        state(false)
        session?.close()
        session = null
        val finishedRecording = recorder?.stop()
        recorder = null
        if (resumePreview && holder.surface.isValid) createSession(null)
        if (askToSave && finishedRecording != null) onRecordingReady?.invoke(finishedRecording)
    }

    private fun createSession(encoder: Surface?) {
        val device = camera ?: return
        try {
            val surfaces = listOfNotNull(holder.surface, encoder)
            if (!holder.surface.isValid) return
            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(value: CameraCaptureSession) {
                    session = value
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(holder.surface)
                        encoder?.let { addTarget(it) }
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                        selectedFrameRateRange()?.let {
                            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
                        }
                    }.build()
                    value.setRepeatingRequest(request, null, handler)
                }

                override fun onConfigureFailed(value: CameraCaptureSession) = Unit
            }, handler)
        } catch (_: CameraAccessException) {
        }
    }

    private fun selectedFrameRateRange(): Range<Int>? {
        return frameRateRanges.firstOrNull {
            settings.frameRate in it.lower..it.upper
        }
    }

    private fun closeCamera() {
        session?.close()
        camera?.close()
        session = null
        camera = null
    }

    fun close() {
        if (recording) stopRecording(false, false)
        closeCamera()
        thread.quitSafely()
    }
}

private data class EncodedSample(
    val data: ByteArray,
    val presentationTimeUs: Long,
    val flags: Int
)

private class LoopRecording(
    private val format: MediaFormat,
    samples: Collection<EncodedSample>,
    val durationSeconds: Int
) {
    val fileName = SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date()) + ".mp4"
    private val samples = samples.toList().let { allSamples ->
        val firstKeyFrame = allSamples.indexOfFirst {
            it.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        }
        if (firstKeyFrame >= 0) allSamples.drop(firstKeyFrame) else allSamples
    }.toMutableList()

    fun saveTo(file: File) {
        file.parentFile?.mkdirs()
        saveToMuxer(MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4))
    }

    fun saveTo(fileDescriptor: FileDescriptor) {
        saveToMuxer(MediaMuxer(fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4))
    }

    private fun saveToMuxer(muxer: MediaMuxer) {
        try {
            val track = muxer.addTrack(format)
            muxer.start()
            val startTimeUs = samples.firstOrNull()?.presentationTimeUs ?: 0L
            samples.forEach { sample ->
                val info = MediaCodec.BufferInfo().apply {
                    set(0, sample.data.size, sample.presentationTimeUs - startTimeUs, sample.flags)
                }
                muxer.writeSampleData(track, java.nio.ByteBuffer.wrap(sample.data), info)
            }
            muxer.stop()
        } finally {
            muxer.release()
            release()
        }
    }

    fun release() {
        samples.clear()
    }
}

private class LoopVideoRecorder(
    private val width: Int,
    private val height: Int,
    private val frameRate: Int,
    private val durationSeconds: Int
) {
    private val codec = MediaCodec.createEncoderByType("video/avc")
    private val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }
    private val maxDurationUs = durationSeconds * 1_000_000L
    private val samples = ArrayDeque<EncodedSample>()
    val inputSurface: Surface
    private var outputFormat: MediaFormat? = null
    private var thread: Thread? = null

    init {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
    }

    fun start() {
        codec.start()
        thread = Thread {
            val info = MediaCodec.BufferInfo()
            while (true) {
                when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = codec.outputFormat
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (index >= 0) {
                        val buffer = codec.getOutputBuffer(index)
                        if (info.size > 0 && buffer != null &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            buffer.position(info.offset)
                            val data = ByteArray(info.size)
                            buffer.get(data)
                            samples.addLast(EncodedSample(data, info.presentationTimeUs, info.flags))
                            trimSamples(info.presentationTimeUs)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        }.also { it.start() }
    }

    fun stop(): LoopRecording? {
        var recording: LoopRecording? = null
        try {
            codec.signalEndOfInputStream()
            thread?.join()
            outputFormat?.let { recording = LoopRecording(it, samples, durationSeconds) }
        } finally {
            inputSurface.release()
            codec.stop()
            codec.release()
        }
        return recording
    }

    private fun trimSamples(latestPresentationTimeUs: Long) {
        val cutoff = latestPresentationTimeUs - maxDurationUs
        while (samples.size > 1 && samples.first().presentationTimeUs < cutoff) {
            samples.removeFirst()
        }
    }
}
