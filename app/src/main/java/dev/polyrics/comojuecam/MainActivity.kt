package dev.polyrics.comojuecam

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import dev.polyrics.comojuecam.camera.CameraController
import dev.polyrics.comojuecam.recording.LoopRecording
import dev.polyrics.comojuecam.recording.RecordingSettings
import dev.polyrics.comojuecam.storage.RecordingStorage
import dev.polyrics.comojuecam.sound.CollisionSound
import dev.polyrics.comojuecam.ui.RetroOverlay
import dev.polyrics.comojuecam.ui.SavePanel
import dev.polyrics.comojuecam.ui.SettingsPanel
import dev.polyrics.comojuecam.ui.IdleRecordingOverlay

class MainActivity : Activity() {
    private lateinit var cameraController: CameraController
    private lateinit var cameraOverlay: RetroOverlay
    private lateinit var settingsPanel: SettingsPanel
    private lateinit var savePanel: SavePanel
    private lateinit var idleOverlay: IdleRecordingOverlay
    private lateinit var recordingStorage: RecordingStorage
    private lateinit var collisionSound: CollisionSound
    private var pendingRecording: LoopRecording? = null
    private var recordingActive = false
    private var lowPowerMode = false
    private var previousBrightness = -1f
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityTimeout = Runnable { enterLowPowerMode() }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        configureFullscreenWindow()

        val recordingSettings = RecordingSettings()
        val preview = SurfaceView(this)
        cameraOverlay = RetroOverlay(this)
        settingsPanel = SettingsPanel(this, recordingSettings)
        savePanel = SavePanel(this)
        idleOverlay = IdleRecordingOverlay(this)
        recordingStorage = RecordingStorage(this)
        collisionSound = CollisionSound()
        settingsPanel.visibility = View.GONE
        savePanel.visibility = View.GONE
        idleOverlay.visibility = View.GONE

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        root.addView(cameraOverlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(settingsPanel, FrameLayout.LayoutParams(-1, -1))
        root.addView(savePanel, FrameLayout.LayoutParams(-1, -1))
        root.addView(idleOverlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        cameraController = CameraController(this, preview.holder, recordingSettings) { recording ->
            runOnUiThread {
                recordingActive = recording
                cameraOverlay.setRecording(recording)
                idleOverlay.setRecording(recording)
                cameraOverlay.settingsEnabled = !recording
                settingsPanel.isEnabled = !recording
                if (recording) settingsPanel.visibility = View.GONE
                cameraOverlay.invalidate()
                if (recording) resetInactivityTimer() else exitLowPowerMode()
            }
        }
        cameraController.onCapabilitiesChanged = {
            runOnUiThread(settingsPanel::syncFromSettings)
        }
        cameraController.onRecordingReady = { recording ->
            runOnUiThread { showSavePanel(recording) }
        }
        cameraController.onIncidentReady = { recording ->
            Thread {
                try {
                    val path = recordingStorage.save(recording)
                    if (path != null) cameraOverlay.showLog("Stored in: $path")
                } finally {
                    recording.release()
                }
            }.start()
        }
        cameraController.onGForceChanged = { gForce ->
            cameraOverlay.setGForce(gForce)
            idleOverlay.setGForce(gForce)
        }
        cameraController.onIncidentStateChanged = { active ->
            cameraOverlay.setCollisionWarning(active)
            idleOverlay.setCollisionWarning(active)
        }
        cameraController.onCollisionDetected = { collisionSound.play() }
        cameraOverlay.onRecord = { cameraController.toggleRecording() }
        cameraOverlay.onInteraction = { resetInactivityTimer() }
        cameraOverlay.onSettings = { settingsPanel.visibility = View.VISIBLE }
        idleOverlay.onWake = { exitLowPowerMode() }
        idleOverlay.onStop = { cameraController.toggleRecording() }
        settingsPanel.onApply = { cameraController.applySettings() }
        settingsPanel.onClose = { settingsPanel.visibility = View.GONE }
        savePanel.onSave = { savePendingRecording() }
        savePanel.onDiscard = { discardPendingRecording() }
        preview.holder.addCallback(cameraController)

        requestCameraPermissionIfNeeded()
    }

    override fun onRequestPermissionsResult(
        request: Int,
        permissions: Array<out String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(request, permissions, results)
        if (request == CAMERA_PERMISSION_REQUEST &&
            results.firstOrNull() != PackageManager.PERMISSION_GRANTED
        ) {
            finish()
        }
        if (request == STORAGE_PERMISSION_REQUEST &&
            results.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            savePendingRecording()
        }
    }

    override fun onDestroy() {
        inactivityHandler.removeCallbacks(inactivityTimeout)
        exitLowPowerMode()
        if (::cameraController.isInitialized) cameraController.close()
        if (::collisionSound.isInitialized) collisionSound.release()
        pendingRecording?.release()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        if (::cameraController.isInitialized) cameraController.resume()
    }

    override fun onStop() {
        if (::cameraController.isInitialized) cameraController.pause()
        super.onStop()
    }

    private fun configureFullscreenWindow() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun requestCameraPermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        }
    }

    private fun showSavePanel(recording: LoopRecording) {
        pendingRecording?.release()
        pendingRecording = recording
        savePanel.durationSeconds = recording.durationSeconds
        savePanel.visibility = View.VISIBLE
    }

    private fun savePendingRecording() {
        val recording = pendingRecording ?: return
        if (recordingStorage.requiresLegacyPermission() &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_REQUEST)
            return
        }
        val path = recordingStorage.save(recording)
        if (path != null) {
            cameraOverlay.showLog("Stored in: $path")
            clearPendingRecording()
        }
    }

    private fun discardPendingRecording() {
        pendingRecording?.release()
        pendingRecording = null
        savePanel.visibility = View.GONE
    }

    private fun clearPendingRecording() {
        pendingRecording?.release()
        pendingRecording = null
        savePanel.visibility = View.GONE
    }

    private fun resetInactivityTimer() {
        if (!recordingActive) return
        if (lowPowerMode) {
            exitLowPowerMode()
            return
        }
        inactivityHandler.removeCallbacks(inactivityTimeout)
        inactivityHandler.postDelayed(inactivityTimeout, LOW_POWER_DELAY_MS)
    }

    private fun enterLowPowerMode() {
        if (!recordingActive || lowPowerMode) return
        lowPowerMode = true
        cameraOverlay.visibility = View.GONE
        idleOverlay.visibility = View.VISIBLE
        cameraController.setPreviewEnabled(false)
        val attributes = window.attributes
        previousBrightness = attributes.screenBrightness
        attributes.screenBrightness = LOW_POWER_BRIGHTNESS
        window.attributes = attributes
    }

    private fun exitLowPowerMode() {
        if (!lowPowerMode) return
        lowPowerMode = false
        inactivityHandler.removeCallbacks(inactivityTimeout)
        idleOverlay.visibility = View.GONE
        cameraOverlay.visibility = View.VISIBLE
        cameraController.setPreviewEnabled(true)
        val attributes = window.attributes
        attributes.screenBrightness = previousBrightness
        window.attributes = attributes
    }

    private companion object {
        const val CAMERA_PERMISSION_REQUEST = 10
        const val STORAGE_PERMISSION_REQUEST = 20
        const val LOW_POWER_DELAY_MS = 90_000L
        const val LOW_POWER_BRIGHTNESS = 0.1f
    }
}
