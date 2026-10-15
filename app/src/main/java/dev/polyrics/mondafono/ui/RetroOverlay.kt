package dev.polyrics.mondafono.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import java.util.Locale

class RetroOverlay(context: Context) : View(context) {
    var settingsEnabled = true
    var onRecord: (() -> Unit)? = null
    var onSettings: (() -> Unit)? = null
    var onInteraction: (() -> Unit)? = null
    private var recording = false
    private var recordingStartedAt = 0L
    private var gForce = 0f
    private var collisionWarning = false
    private var logMessage: String? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val timerRefresh = object : Runnable {
        override fun run() {
            if (recording) {
                invalidate()
                postDelayed(this, 500L)
            }
        }
    }
    private val logClear = Runnable {
        logMessage = null
        invalidate()
    }

    fun setRecording(value: Boolean) {
        if (recording == value) return
        recording = value
        if (value) {
            recordingStartedAt = SystemClock.elapsedRealtime()
            post(timerRefresh)
        } else {
            removeCallbacks(timerRefresh)
            gForce = 0f
            collisionWarning = false
        }
        invalidate()
    }

    fun setGForce(value: Float) {
        post {
            gForce = value
            invalidate()
        }
    }

    fun setCollisionWarning(value: Boolean) {
        post {
            collisionWarning = value
            invalidate()
        }
    }

    fun showLog(message: String) {
        post {
            logMessage = message
            removeCallbacks(logClear)
            postDelayed(logClear, 4_000L)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val amber = Color.rgb(255, 176, 0)
        drawSettingsButton(canvas, amber)
        drawRecordButton(canvas, amber)
        if (recording) drawRecordingStatus(canvas)
        if (collisionWarning) drawCollisionWarning(canvas, amber)
        drawLog(canvas)
    }

    private fun drawSettingsButton(canvas: Canvas, amber: Int) {
        val left = width - 150f
        val top = height - 83f
        val right = width - 24f
        val bottom = height - 37f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(220, 0, 0, 0)
        canvas.drawRect(left, top, right, bottom, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = if (settingsEnabled) amber else Color.DKGRAY
        canvas.drawRect(left, top, right, bottom, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 15f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("CONFIG", (left + right) / 2f, top + 29f, paint)
    }

    private fun drawRecordButton(canvas: Canvas, amber: Int) {
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
    }

    private fun drawRecordingStatus(canvas: Canvas) {
        val elapsedMillis = SystemClock.elapsedRealtime() - recordingStartedAt
        paint.textSize = 16f
        paint.textAlign = Paint.Align.LEFT
        if ((elapsedMillis / 500L) % 2L == 0L) {
            paint.color = Color.RED
            canvas.drawText("* RECORDING", 32f, 48f, paint)
        }
        paint.color = Color.WHITE
        canvas.drawText("Time: ${formatElapsedTime(elapsedMillis / 1000L)}", 32f, 76f, paint)
        paint.textSize = 16f
        canvas.drawText(String.format(Locale.US, "G FORCE: %.1f", gForce), 32f, 104f, paint)
    }

    private fun drawCollisionWarning(canvas: Canvas, amber: Int) {
        val elapsedMillis = SystemClock.elapsedRealtime() - recordingStartedAt
        if ((elapsedMillis / 500L) % 2L != 0L) return
        val left = (width - 300f) / 2f
        val top = 82f
        paint.style = Paint.Style.FILL
        paint.color = amber
        canvas.drawRect(left, top, left + 300f, top + 44f, paint)
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 17f
        canvas.drawText("COLLISION DETECTED", width / 2f, top + 28f, paint)
    }

    private fun drawLog(canvas: Canvas) {
        val message = logMessage ?: return
        paint.textSize = 16f
        paint.textAlign = Paint.Align.RIGHT
        val right = width - 24f
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawText(message, right, 41f, paint)
    }

    private fun formatElapsedTime(seconds: Long): String = String.format(
        Locale.US,
        "%02d:%02d:%02d",
        seconds / 3_600,
        seconds / 60 % 60,
        seconds % 60
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) onInteraction?.invoke()
        if (event.action == MotionEvent.ACTION_UP) {
            if (settingsEnabled && event.x > width - 170f && event.y > height - 100f &&
                event.y < height - 27f
            ) {
                onSettings?.invoke()
            } else if (event.y > height - 110f &&
                event.x > width / 2f - 110f && event.x < width / 2f + 110f
            ) {
                onRecord?.invoke()
            }
        }
        return true
    }
}
