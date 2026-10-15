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

class IdleRecordingOverlay(context: Context) : View(context) {
    var onWake: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    private var recording = false
    private var collisionWarning = false
    private var gForce = 0f
    private var startedAt = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val refresh = object : Runnable {
        override fun run() {
            if (recording) {
                invalidate()
                postDelayed(this, 500L)
            }
        }
    }

    fun setRecording(value: Boolean) {
        post {
            recording = value
            if (value) {
                startedAt = SystemClock.elapsedRealtime()
                post(refresh)
            } else {
                removeCallbacks(refresh)
            }
            invalidate()
        }
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

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val amber = Color.rgb(255, 176, 0)
        val centerX = width / 2f
        val panelTop = height / 2f - 150f
        val panelLeft = centerX - 220f
        val panelRight = centerX + 220f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = amber
        canvas.drawRect(panelLeft, panelTop, panelRight, panelTop + 300f, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 22f
        paint.color = Color.RED
        if ((SystemClock.elapsedRealtime() - startedAt) / 500L % 2L == 0L) {
            canvas.drawText("* RECORDING", centerX, panelTop + 48f, paint)
        }
        paint.color = Color.WHITE
        paint.textSize = 18f
        val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000L
        canvas.drawText("Time: ${formatTime(seconds)}", centerX, panelTop + 88f, paint)
        canvas.drawText(String.format(Locale.US, "G FORCE: %.1f", gForce), centerX, panelTop + 120f, paint)

        if (collisionWarning && (SystemClock.elapsedRealtime() - startedAt) / 500L % 2L == 0L) {
            paint.color = amber
            canvas.drawRect(centerX - 145f, panelTop + 138f, centerX + 145f, panelTop + 178f, paint)
            paint.color = Color.BLACK
            paint.textSize = 16f
            canvas.drawText("COLLISION DETECTED", centerX, panelTop + 165f, paint)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = amber
        canvas.drawRect(centerX - 95f, panelTop + 220f, centerX + 95f, panelTop + 270f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.RED
        paint.textSize = 18f
        canvas.drawText("STOP", centerX, panelTop + 252f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val centerX = width / 2f
        val panelTop = height / 2f - 150f
        if (event.x in centerX - 95f..centerX + 95f && event.y in panelTop + 220f..panelTop + 270f) {
            onStop?.invoke()
        } else {
            onWake?.invoke()
        }
        return true
    }

    private fun formatTime(seconds: Long): String = String.format(
        Locale.US,
        "%02d:%02d:%02d",
        seconds / 3_600,
        seconds / 60 % 60,
        seconds % 60
    )
}
