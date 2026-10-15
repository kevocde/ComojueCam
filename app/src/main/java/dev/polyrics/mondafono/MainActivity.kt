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
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        camera = CameraController(this, preview.holder) { recording ->
            overlay.recording = recording
            overlay.invalidate()
        }
        overlay.onRecord = { camera.toggleRecording() }
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
    var onRecord: (() -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }

    override fun onDraw(canvas: Canvas) {
        val amber = Color.rgb(255, 176, 0)

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
        if (event.action == MotionEvent.ACTION_UP && event.y > height - 110f &&
            event.x > width / 2f - 110f && event.x < width / 2f + 110f) {
            onRecord?.invoke()
        }
        return true
    }
}

private class CameraController(
    private val context: Context,
    private val holder: SurfaceHolder,
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

    private fun open() {
        try {
            cameraId = manager.cameraIdList.first { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            }
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

    private fun startRecording() {
        if (camera == null || recording) return
        val size = chooseSize()
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val output = File(
            directory,
            "mondafono-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".mp4"
        )
        recorder = VideoRecorder(size.first, size.second, output)
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

    private fun chooseSize(): Pair<Int, Int> {
        val map = manager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(SurfaceHolder::class.java).orEmpty()
        val size = sizes.firstOrNull { it.width == 1280 && it.height == 720 }
            ?: sizes.firstOrNull { it.width.toFloat() / it.height < 1.9f }
            ?: sizes.first()
        return size.width to size.height
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
                    }.build()
                    value.setRepeatingRequest(request, null, handler)
                }

                override fun onConfigureFailed(value: CameraCaptureSession) = Unit
            }, handler)
        } catch (_: CameraAccessException) {
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

private class VideoRecorder(private val width: Int, private val height: Int, private val file: File) {
    private val codec = MediaCodec.createEncoderByType("video/avc")
    private val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, 30)
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
