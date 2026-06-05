package com.cloudmelody.ui.lyrics

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.cloudmelody.model.LyricLine

/**
 * Apple Music-style lyrics view.
 * Call [setLyrics] then [updateTime] periodically with the player position.
 */
class AppleLyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var lyrics: List<LyricLine> = emptyList()
    private var currentIndex: Int = -1

    // Paints
    private val paintNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#9AB3D8")
        textSize  = 48f
        textAlign = Paint.Align.CENTER
    }
    private val paintHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#F5C842")
        textSize  = 52f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // Scroll
    private var scrollY: Float = 0f
    private var targetScrollY: Float = 0f
    private var scrollAnimator: ValueAnimator? = null

    private val lineHeight = 120f
    private val linePadding = 24f

    fun setLyrics(lines: List<LyricLine>) {
        lyrics = lines
        currentIndex = -1
        scrollY = 0f
        invalidate()
    }

    fun updateTime(positionMs: Long) {
        val newIndex = lyrics.indexOfLast { it.timeMs <= positionMs }
        if (newIndex != currentIndex) {
            currentIndex = newIndex
            smoothScrollToIndex(currentIndex)
            invalidate()
        }
    }

    private fun smoothScrollToIndex(index: Int) {
        if (index < 0) return
        val centerY = height / 2f
        val dest = index * (lineHeight + linePadding) - centerY + lineHeight / 2
        scrollAnimator?.cancel()
        scrollAnimator = ValueAnimator.ofFloat(scrollY, dest).apply {
            duration = 400
            interpolator = LinearInterpolator()
            addUpdateListener {
                scrollY = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lyrics.isEmpty()) return

        val cx = width / 2f

        lyrics.forEachIndexed { i, line ->
            val y = i * (lineHeight + linePadding) - scrollY + lineHeight
            val paint = if (i == currentIndex) paintHighlight else paintNormal
            canvas.drawText(line.text, cx, y, paint)
        }
    }
}
