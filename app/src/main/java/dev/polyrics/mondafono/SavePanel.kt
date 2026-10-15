package dev.polyrics.mondafono

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

class SavePanel(context: Context) : View(context) {
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
            val top = height * 0.68f - 62f
            val right = width * 0.76f
            if (event.y in top..top + 40f) {
                when {
                    event.x in left + 42f..left + 152f -> onSave?.invoke()
                    event.x in right - 152f..right - 42f -> onDiscard?.invoke()
                }
            }
        }
        return true
    }
}
