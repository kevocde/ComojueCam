package dev.polyrics.mondafono

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import dev.polyrics.mondafono.camera.CameraController
import dev.polyrics.mondafono.recording.LoopRecording
import dev.polyrics.mondafono.recording.RecordingSettings
import dev.polyrics.mondafono.storage.RecordingStorage
import dev.polyrics.mondafono.ui.RetroOverlay
import dev.polyrics.mondafono.ui.SavePanel
import dev.polyrics.mondafono.ui.SettingsPanel

class MainActivity : Activity() {
    private lateinit var cameraController: CameraController
    private lateinit var cameraOverlay: RetroOverlay
    private lateinit var settingsPanel: SettingsPanel
    private lateinit var savePanel: SavePanel
    private lateinit var recordingStorage: RecordingStorage
    private var pendingRecording: LoopRecording? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        configureFullscreenWindow()

        val recordingSettings = RecordingSettings()
        val preview = SurfaceView(this)
        cameraOverlay = RetroOverlay(this)
        settingsPanel = SettingsPanel(this, recordingSettings)
        savePanel = SavePanel(this)
        recordingStorage = RecordingStorage(this)
        settingsPanel.visibility = View.GONE
        savePanel.visibility = View.GONE

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        root.addView(cameraOverlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(settingsPanel, FrameLayout.LayoutParams(-1, -1))
        root.addView(savePanel, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        cameraController = CameraController(this, preview.holder, recordingSettings) { recording ->
            cameraOverlay.setRecording(recording)
            cameraOverlay.settingsEnabled = !recording
            settingsPanel.isEnabled = !recording
            if (recording) settingsPanel.visibility = View.GONE
            cameraOverlay.invalidate()
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
                    recordingStorage.save(recording)
                } finally {
                    recording.release()
                }
            }.start()
        }
        cameraController.onGForceChanged = cameraOverlay::setGForce
        cameraController.onIncidentStateChanged = cameraOverlay::setCollisionWarning
        cameraOverlay.onRecord = { cameraController.toggleRecording() }
        cameraOverlay.onSettings = { settingsPanel.visibility = View.VISIBLE }
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
        if (::cameraController.isInitialized) cameraController.close()
        pendingRecording?.release()
        super.onDestroy()
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
        if (recordingStorage.save(recording)) clearPendingRecording()
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

    private companion object {
        const val CAMERA_PERMISSION_REQUEST = 10
        const val STORAGE_PERMISSION_REQUEST = 20
    }
}
