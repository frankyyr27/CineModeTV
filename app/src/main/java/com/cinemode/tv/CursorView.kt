package com.cinemode.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Draws a simple mouse-style arrow at (cx, cy). Purely visual. */
class CursorView(context: Context) : View(context) {

    var cx = 0f
    var cy = 0f

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val s = 14f * density
        path.reset()
        path.moveTo(cx, cy)
        path.lineTo(cx, cy + 1.6f * s)
        path.lineTo(cx + 0.45f * s, cy + 1.25f * s)
        path.lineTo(cx + 0.8f * s, cy + 1.9f * s)
        path.lineTo(cx + 1.05f * s, cy + 1.75f * s)
        path.lineTo(cx + 0.7f * s, cy + 1.15f * s)
        path.lineTo(cx + 1.2f * s, cy + 1.1f * s)
        path.close()
        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)
    }
}
