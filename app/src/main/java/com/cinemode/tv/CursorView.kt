package com.cinemode.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * Small arrow cursor. The view is a fixed size and is moved with
 * translationX/Y (cheap, does not redraw the web page underneath).
 * The arrow tip sits at (PAD, PAD) inside the view.
 */
class CursorView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    val pad = 4f * d
    val sizePx = (48f * d).toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f * d
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val s = 18f * d
        val x = pad
        val y = pad
        path.reset()
        path.moveTo(x, y)
        path.lineTo(x, y + 1.6f * s)
        path.lineTo(x + 0.45f * s, y + 1.25f * s)
        path.lineTo(x + 0.8f * s, y + 1.9f * s)
        path.lineTo(x + 1.05f * s, y + 1.75f * s)
        path.lineTo(x + 0.7f * s, y + 1.15f * s)
        path.lineTo(x + 1.2f * s, y + 1.1f * s)
        path.close()
        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)
    }
}
