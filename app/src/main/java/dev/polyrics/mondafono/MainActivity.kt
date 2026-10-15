package dev.polyrics.mondafono

import android.Manifest
import android.app.Activity
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
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.util.Range
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var camera: CameraController
    private lateinit var overlay: RetroOverlay

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
        settings.visibility = View.GONE
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(settings, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        camera = CameraController(this, preview.holder, recordingSettings) { recording ->
            overlay.recording = recording
            overlay.settingsEnabled = !recording
            settings.isEnabled = !recording
            if (recording) settings.visibility = View.GONE
            overlay.invalidate()
        }
        camera.onCapabilitiesChanged = { runOnUiThread(settings::syncFromSettings) }
        overlay.onRecord = { camera.toggleRecording() }
        overlay.onSettings = { settings.visibility = View.VISIBLE }
        settings.onApply = { camera.applySettings() }
        settings.onClose = { settings.visibility = View.GONE }
        preview.holder.addCallback(camera)

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 10)
        }
    }

    override fun onRequestPermissionsResult(request: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(request, permissions, results)
        if (request == 10 && results.firstOrNull() != PackageManager.PERMISSION_GRANTED) finish()
    }

    override fun onDestroy() {
        if (::camera.isInitialized) camera.close()
        super.onDestroy()
    }
}

private class RetroOverlay(context: Context) : View(context) {
    var recording = false
    var settingsEnabled = true
    var onRecord: (() -> Unit)? = null
    var onSettings: (() -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }

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
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 16f
            canvas.drawText("* RECORDING", 32f, 48f, paint)
        }
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

    fun syncFromSettings() {
        pendingFrameRate = settings.frameRate
        pendingSize = settings.size
        invalidate()
    }

    private fun hasChanges() = pendingFrameRate != settings.frameRate || pendingSize != settings.size

    private fun applyChanges() {
        settings.frameRate = pendingFrameRate
        settings.size = pendingSize
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
    private var recorder: VideoRecorder? = null
    private var recording = false
    private var cameraId = ""
    private var frameRateRanges = emptyList<Range<Int>>()
    var onCapabilitiesChanged: (() -> Unit)? = null

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) open()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (recording) stopRecording(false)
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
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val output = File(
            directory,
            "mondafono-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".mp4"
        )
        recorder = VideoRecorder(size.width, size.height, settings.frameRate, output)
        recorder!!.start()
        session?.close()
        createSession(recorder!!.inputSurface)
        recording = true
        state(true)
    }

    private fun stopRecording(resumePreview: Boolean = true) {
        if (!recording) return
        recording = false
        state(false)
        session?.close()
        session = null
        recorder?.stop()
        recorder = null
        if (resumePreview && holder.surface.isValid) createSession(null)
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
        if (recording) stopRecording()
        closeCamera()
        thread.quitSafely()
    }
}

private class VideoRecorder(
    private val width: Int,
    private val height: Int,
    private val frameRate: Int,
    private val file: File
) {
    private val codec = MediaCodec.createEncoderByType("video/avc")
    private val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }
    private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val inputSurface: Surface
    private var track = -1
    private var muxing = false
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
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (index >= 0) {
                        val buffer = codec.getOutputBuffer(index)
                        if (muxing && info.size > 0 && buffer != null) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        }.also { it.start() }
    }

    fun stop() {
        try {
            codec.signalEndOfInputStream()
            thread?.join()
        } finally {
            if (muxing) muxer.stop()
            muxer.release()
            inputSurface.release()
            codec.stop()
            codec.release()
        }
    }
}
