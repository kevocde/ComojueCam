package dev.polyrics.mondafono.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import dev.polyrics.mondafono.recording.RecordingSettings

class SettingsPanel(
    context: Context,
    private val settings: RecordingSettings
) : View(context) {
    var onClose: (() -> Unit)? = null
    var onApply: (() -> Unit)? = null
    private val tabs = listOf("VIDEO", "RECORD")
    private var activeTab = 0
    private var pendingFrameRate = settings.frameRate
    private var pendingSize = settings.size
    private var pendingLoopDuration = settings.loopDurationSeconds
    private var pendingIncidentMultiplier = settings.incidentMultiplier
    private var pendingExtraCollisionTime = settings.extraCollisionTimeSeconds
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }

    fun syncFromSettings() {
        pendingFrameRate = settings.frameRate
        pendingSize = settings.size
        pendingLoopDuration = settings.loopDurationSeconds
        pendingIncidentMultiplier = settings.incidentMultiplier
        pendingExtraCollisionTime = settings.extraCollisionTimeSeconds
        invalidate()
    }

    private fun hasChanges() = pendingFrameRate != settings.frameRate ||
        pendingSize != settings.size || pendingLoopDuration != settings.loopDurationSeconds ||
        pendingIncidentMultiplier != settings.incidentMultiplier ||
        pendingExtraCollisionTime != settings.extraCollisionTimeSeconds

    private fun applyChanges() {
        settings.frameRate = pendingFrameRate
        settings.size = pendingSize
        settings.loopDurationSeconds = pendingLoopDuration
        settings.incidentMultiplier = pendingIncidentMultiplier
        settings.extraCollisionTimeSeconds = pendingExtraCollisionTime
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
        drawHeader(canvas, panelLeft, panelTop, panelRight, amber)
        drawTabs(canvas, panelLeft, panelTop + 72f, amber)
    }

    private fun drawHeader(canvas: Canvas, left: Float, top: Float, right: Float, amber: Int) {
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 22f
        paint.color = amber
        canvas.drawText("CONFIG", left + 28f, top + 42f, paint)

        val closeLeft = right - 112f
        val closeTop = top + 18f
        drawButton(canvas, "CLOSE", closeLeft, closeTop, right - 18f, closeTop + 38f, amber)
        if (hasChanges()) {
            drawButton(canvas, "APPLY", closeLeft - 106f, closeTop, closeLeft - 10f, closeTop + 38f, amber)
        }
    }

    private fun drawTabs(canvas: Canvas, left: Float, top: Float, amber: Int) {
        val tabWidth = 92f
        tabs.forEachIndexed { index, title ->
            val tabLeft = left + 20f + index * (tabWidth + 8f)
            paint.style = if (index == activeTab) Paint.Style.FILL else Paint.Style.STROKE
            paint.color = amber
            canvas.drawRect(tabLeft, top, tabLeft + tabWidth, top + 38f, paint)
            paint.style = Paint.Style.FILL
            paint.color = if (index == activeTab) Color.BLACK else amber
            paint.textSize = 15f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(title, tabLeft + tabWidth / 2f, top + 25f, paint)
        }

        if (activeTab == 0) {
            drawFrameRateControl(canvas, top + 70f, left)
            drawQualityControl(canvas, top + 126f, left)
        } else {
            drawLoopControl(canvas, top + 70f, left)
            drawIncidentControl(canvas, top + 126f, left)
            drawExtraCollisionControl(canvas, top + 182f, left)
        }
    }

    private fun drawFrameRateControl(canvas: Canvas, top: Float, left: Float) {
        drawLabel(canvas, "FPS:", top, left)
        val controlLeft = left + 160f
        drawButton(canvas, "-", controlLeft, top, controlLeft + 42f, top + 40f, Color.rgb(255, 176, 0))
        drawValue(canvas, pendingFrameRate.toString(), controlLeft + 50f, top, controlLeft + 146f)
        drawButton(canvas, "+", controlLeft + 154f, top, controlLeft + 196f, top + 40f, Color.rgb(255, 176, 0))
    }

    private fun drawQualityControl(canvas: Canvas, top: Float, left: Float) {
        drawLabel(canvas, "QUALITY:", top, left)
        val controlLeft = left + 160f
        drawValue(canvas, pendingSize.toString(), controlLeft, top, controlLeft + 96f)
        drawButton(canvas, "CHANGE", controlLeft + 104f, top, controlLeft + 200f, top + 40f, Color.rgb(255, 176, 0))
    }

    private fun drawLoopControl(canvas: Canvas, top: Float, left: Float) {
        drawLabel(canvas, "LOOP:", top, left)
        val controlLeft = left + 160f
        drawButton(canvas, "-", controlLeft, top, controlLeft + 42f, top + 40f, Color.rgb(255, 176, 0))
        drawValue(canvas, "${pendingLoopDuration}s", controlLeft + 50f, top, controlLeft + 146f)
        drawButton(canvas, "+", controlLeft + 154f, top, controlLeft + 196f, top + 40f, Color.rgb(255, 176, 0))
    }

    private fun drawIncidentControl(canvas: Canvas, top: Float, left: Float) {
        drawLabel(canvas, "INCIDENT:", top, left)
        val controlLeft = left + 160f
        drawValue(canvas, "x$pendingIncidentMultiplier", controlLeft, top, controlLeft + 96f)
        drawButton(canvas, "CHANGE", controlLeft + 104f, top, controlLeft + 200f, top + 40f, Color.rgb(255, 176, 0))
    }

    private fun drawExtraCollisionControl(canvas: Canvas, top: Float, left: Float) {
        drawLabel(canvas, "EXTRA COLLITION TIME:", top, left)
        val controlLeft = left + 160f
        drawButton(canvas, "-", controlLeft, top, controlLeft + 42f, top + 40f, Color.rgb(255, 176, 0))
        drawValue(canvas, "${pendingExtraCollisionTime}s", controlLeft + 50f, top, controlLeft + 146f)
        drawButton(canvas, "+", controlLeft + 154f, top, controlLeft + 196f, top + 40f, Color.rgb(255, 176, 0))
    }

    private fun drawLabel(canvas: Canvas, label: String, top: Float, left: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 176, 0)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = if (label.length > 12) 11f else 16f
        canvas.drawText(label, left + 28f, top + 28f, paint)
    }

    private fun drawButton(canvas: Canvas, label: String, left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = color
        canvas.drawRect(left, top, right, bottom, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = if (label == "CHANGE") 11f else 14f
        canvas.drawText(label, (left + right) / 2f, top + 27f, paint)
    }

    private fun drawValue(canvas: Canvas, value: String, left: Float, top: Float, right: Float) {
        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(255, 176, 0)
        canvas.drawRect(left, top, right, top + 40f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 15f
        canvas.drawText(value, (left + right) / 2f, top + 26f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val panelTop = height * 0.12f
        val panelRight = width * 0.88f
        val closeLeft = panelRight - 112f
        val closeTop = panelTop + 18f
        when {
            event.x in closeLeft..panelRight - 18f && event.y in closeTop..closeTop + 38f -> {
                syncFromSettings()
                onClose?.invoke()
            }
            hasChanges() && event.x in closeLeft - 106f..closeLeft - 10f &&
                event.y in closeTop..closeTop + 38f -> applyChanges()
            event.y in panelTop + 72f..panelTop + 110f -> selectTab(event.x)
            activeTab == 0 && event.y in panelTop + 198f..panelTop + 238f -> changeQuality()
            activeTab == 0 && event.y in panelTop + 142f..panelTop + 182f -> changeFrameRate(event.x)
            activeTab == 1 && event.y in panelTop + 142f..panelTop + 182f -> changeLoop(event.x)
            activeTab == 1 && event.y in panelTop + 198f..panelTop + 238f -> changeIncidentMultiplier()
            activeTab == 1 && event.y in panelTop + 254f..panelTop + 294f -> changeExtraCollisionTime(event.x)
        }
        return true
    }

    private fun selectTab(x: Float) {
        val left = width * 0.12f + 20f
        activeTab = when {
            x in left..left + 92f -> 0
            x in left + 100f..left + 192f -> 1
            else -> activeTab
        }
        invalidate()
    }

    private fun changeQuality() {
        if (settings.supportedSizes.isNotEmpty()) {
            val index = settings.supportedSizes.indexOf(pendingSize)
            val next = if (index < 0 || index == settings.supportedSizes.lastIndex) 0 else index + 1
            pendingSize = settings.supportedSizes[next]
        }
        invalidate()
    }

    private fun changeFrameRate(x: Float) {
        val left = width * 0.12f + 160f
        val index = settings.supportedFrameRates.indexOf(pendingFrameRate)
        pendingFrameRate = when {
            x in left..left + 42f -> settings.supportedFrameRates[(index - 1).coerceAtLeast(0)]
            x in left + 154f..left + 196f -> settings.supportedFrameRates[
                (index + 1).coerceAtMost(settings.supportedFrameRates.lastIndex)
            ]
            else -> pendingFrameRate
        }
        invalidate()
    }

    private fun changeLoop(x: Float) {
        val left = width * 0.12f + 160f
        pendingLoopDuration = when {
            x in left..left + 42f -> (pendingLoopDuration - 10).coerceAtLeast(20)
            x in left + 154f..left + 196f -> (pendingLoopDuration + 10).coerceAtMost(120)
            else -> pendingLoopDuration
        }
        invalidate()
    }

    private fun changeIncidentMultiplier() {
        pendingIncidentMultiplier = if (pendingIncidentMultiplier == 6) 2 else pendingIncidentMultiplier + 1
        invalidate()
    }

    private fun changeExtraCollisionTime(x: Float) {
        val left = width * 0.12f + 160f
        pendingExtraCollisionTime = when {
            x in left..left + 42f -> (pendingExtraCollisionTime - 20).coerceAtLeast(20)
            x in left + 154f..left + 196f -> (pendingExtraCollisionTime + 20).coerceAtMost(60)
            else -> pendingExtraCollisionTime
        }
        invalidate()
    }
}
