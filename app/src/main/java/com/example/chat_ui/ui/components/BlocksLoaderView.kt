package com.example.chat_ui.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.floor

data class Pt2(val x: Float, val y: Float)

/**
 * Animated blocks loader view - shows 5 cubes moving in a pattern
 */
class BlocksLoaderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0xFFBDBDBD.toInt()
    }

    private var t: Float = 0f

    private val paths: List<List<Pt2>> = listOf(
        listOf(Pt2(-2f,0f), Pt2(-2f,0f), Pt2(-2f,0f), Pt2(-2f,0f), Pt2(-2f,0f), Pt2(-1f,0f), Pt2(-2f,0f)),
        listOf(Pt2(-1f,0f), Pt2(-1f,0f), Pt2(-1f,0f), Pt2(-1f,0f), Pt2(-1f,0f), Pt2(-2f,0f), Pt2(-1f,0f)),
        listOf(Pt2(0f,0f),  Pt2(0f,0.3f),Pt2(0f,0f),  Pt2(0f,-0.35f),Pt2(0f,0f),Pt2(0f,0.25f),Pt2(0f,0f)),
        listOf(Pt2(1f,0f),  Pt2(1f,-1f), Pt2(1f,0f), Pt2(1f,0f), Pt2(1f,0f), Pt2(1f,-0.2f), Pt2(1f,0f)),
        listOf(Pt2(2f,0f),  Pt2(2f,1f),  Pt2(2f,0f), Pt2(2f,0f), Pt2(2f,0f), Pt2(2f,0.2f), Pt2(2f,0f)),
    )

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val square = dp(48f)
        val cx = width / 2f
        val cy = height / 2f

        for (i in 0 until 5) {
            val p = sample(paths[i], t)
            val x = cx + p.x * square - square / 2f
            val y = cy + p.y * square - square / 2f

            canvas.drawRect(x, y, x + square, y + square, fillPaint)
            canvas.drawRect(x, y, x + square, y + square, strokePaint)
        }
    }

    private fun sample(path: List<Pt2>, tt: Float): Pt2 {
        val n = path.size
        val scaled = tt * (n - 1)
        val i = floor(scaled).toInt().coerceIn(0, n - 2)
        val local = scaled - i
        val a = path[i]
        val b = path[i + 1]
        return Pt2(
            a.x + (b.x - a.x) * local,
            a.y + (b.y - a.y) * local
        )
    }

    private fun dp(v: Float): Float =
        v * resources.displayMetrics.density
}
