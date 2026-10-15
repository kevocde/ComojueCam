package dev.polyrics.mondafono.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.view.Surface
import android.view.SurfaceHolder
import dev.polyrics.mondafono.recording.LoopRecording
import dev.polyrics.mondafono.recording.LoopVideoRecorder
import dev.polyrics.mondafono.recording.RecordingSettings
import dev.polyrics.mondafono.recording.VideoSize
import dev.polyrics.mondafono.sensor.CollisionDetector

class CameraController(
    private val context: Context,
    private val holder: SurfaceHolder,
    private val settings: RecordingSettings,
    private val state: (Boolean) -> Unit
) : SurfaceHolder.Callback {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val thread = HandlerThread("camera").apply { start() }
    private val handler = Handler(thread.looper)
    private val collisionDetector = CollisionDetector(context, ::handleCollision)
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var recorder: LoopVideoRecorder? = null
    private var recording = false
    private var incidentCaptureActive = false
    private var cameraId = ""
    private var frameRateRanges = emptyList<Range<Int>>()
    var onCapabilitiesChanged: (() -> Unit)? = null
    var onRecordingReady: ((LoopRecording) -> Unit)? = null
    var onIncidentReady: ((LoopRecording) -> Unit)? = null

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

    fun close() {
        if (recording) stopRecording(false, false)
        closeCamera()
        thread.quitSafely()
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
        collisionDetector.start()
        state(true)
    }

    private fun stopRecording(resumePreview: Boolean = true, askToSave: Boolean = true) {
        if (!recording) return
        recording = false
        collisionDetector.stop()
        incidentCaptureActive = false
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

    private fun handleCollision() {
        if (!recording || incidentCaptureActive) return
        val activeRecorder = recorder ?: return
        incidentCaptureActive = true
        val preDuration = settings.loopDurationSeconds
        val postDuration = preDuration * settings.incidentMultiplier
        activeRecorder.startIncidentCapture(preDuration, postDuration) { incident ->
            incidentCaptureActive = false
            onIncidentReady?.invoke(incident)
        }
    }

    private fun closeCamera() {
        session?.close()
        camera?.close()
        session = null
        camera = null
    }
}
