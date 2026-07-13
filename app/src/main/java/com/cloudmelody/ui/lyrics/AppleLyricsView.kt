package com.cloudmelody.ui.lyrics

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.cloudmelody.model.LyricLine

/**
 * Apple Music 风格逐词高亮歌词视图
 *
 * Improvements:
 * - Gradient highlight text (gold-to-amber)
 * - Smooth vertical scroll animation
 * - Translucent overlay at top/bottom for fade effect
 * - Touch handling so user can still interact with player
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
        color = Color.parseColor("#9AB3D8")
        textSize = 48f
        textAlign = Paint.Align.CENTER
    }

    private val paintHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 52f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // Fade gradient paints for top and bottom edges
    private val paintFadeTop = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintFadeBottom = Paint(Paint.ANTI_ALIAS_FLAG)

    // Scroll
    private var scrollY: Float = 0f
    private var scrollAnimator: ValueAnimator? = null

    private val lineHeight = 120f
    private val linePadding = 24f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setLyrics(lines: List<LyricLine>) {
        lyrics = lines
        currentIndex = if (lines.isNotEmpty()) 0 else -1
        scrollY = 0f
        invalidate()
    }

    fun updateTime(positionMs: Long) {
        val newIndex = lyrics.indexOfLast { it.timeMs <= positionMs }
        if (newIndex != currentIndex && newIndex >= 0) {
            currentIndex = newIndex
            smoothScrollToIndex(currentIndex)
        }
    }

    private fun smoothScrollToIndex(index: Int) {
        if (index < 0 || height <= 0) return
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Update highlight gradient based on view height
        paintHighlight.shader = LinearGradient(
            0f, h * 0.3f, 0f, h * 0.6f,
            Color.parseColor("#F5C842"),
            Color.parseColor("#FFD700"),
            Shader.TileMode.CLAMP
        )

        // Fade gradients
        paintFadeTop.shader = LinearGradient(
            0f, 0f, 0f, h * 0.15f,
            Color.parseColor("#1a1a2e"),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        paintFadeBottom.shader = LinearGradient(
            0f, h * 0.85f, 0f, h.toFloat(),
            Color.TRANSPARENT,
            Color.parseColor("#1a1a2e"),
            Shader.TileMode.CLAMP
        )

        // Re-scroll to current index after size change
        if (currentIndex >= 0) {
            smoothScrollToIndex(currentIndex)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (lyrics.isEmpty()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#9AB3D8")
                textSize = 36f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("暂无歌词", width / 2f, height / 2f, paint)
            return
        }

        val cx = width / 2f

        lyrics.forEachIndexed { i, line ->
            val y = i * (lineHeight + linePadding) - scrollY + lineHeight
            // Only draw lines within visible area
            if (y < -lineHeight || y > height + lineHeight) return@forEachIndexed

            val paint = if (i == currentIndex) paintHighlight else paintNormal
            canvas.drawText(line.text, cx, y, paint)
        }

        // Draw fade overlays
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.15f, paintFadeTop)
        canvas.drawRect(0f, height * 0.85f, width.toFloat(), height.toFloat(), paintFadeBottom)
    }
}
