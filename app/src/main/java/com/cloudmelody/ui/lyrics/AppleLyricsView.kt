package com.cloudmelody.ui.lyrics

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.cloudmelody.R
import com.cloudmelody.model.LyricLine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Apple Music-style lyrics view.
 *
 * Features:
 *  - Active line: large, bold, white/opaque
 *  - Adjacent lines: medium size, translucent
 *  - Distant lines: small, very translucent (fade out)
 *  - Smooth auto-scroll animation to keep active line centered
 *  - Translation displayed below each line in smaller italic text
 *  - Touch to seek (calls [onSeekToLine] callback)
 *  - Blur/fade edges (top & bottom gradient mask)
 */
class AppleLyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ──────────── Public API ────────────

    var lines: List<LyricLine> = emptyList()
        set(value) {
            field = value
            lineHeights.clear()
            invalidate()
        }

    var currentLineIndex: Int = -1
        set(value) {
            if (field == value) return
            field = value
            smoothScrollToLine(value)
            invalidate()
        }

    /** Called when user taps a lyric line. Provides the time in ms to seek to. */
    var onSeekToLine: ((Long) -> Unit)? = null

    // ──────────── Paint setup ────────────

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val nearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val farPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val transPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
    }

    private val gradientPaint = Paint()

    // Colors: set from resources / setColors()
    private var colorActive   = 0xFFFFFFFF.toInt()
    private var colorNear     = 0xCCFFFFFF.toInt()
    private var colorFar      = 0x66FFFFFF.toInt()
    private var colorTrans    = 0x99FFFFFF.toInt()

    // Font sizes (dp -> px in init)
    private var sizeActive = 0f
    private var sizeNear   = 0f
    private var sizeFar    = 0f
    private var sizeTrans  = 0f

    // Line spacing factor
    private val lineSpacing = 1.45f
    private val transSpacing = 1.1f
    private val blockGap get() = sizeActive * 0.6f

    // Scroll state
    private var scrollY = 0f
    private var targetScrollY = 0f
    private var scrollAnimator: ValueAnimator? = null

    // Cached per-line layout heights
    private val lineHeights = mutableListOf<Float>()

    // Gesture
    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleTap(e.y)
                return true
            }
            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent,
                distanceX: Float, distanceY: Float
            ): Boolean {
                scrollY = (scrollY + distanceY).coerceIn(minScrollY(), maxScrollY())
                invalidate()
                return true
            }
        })

    init {
        val dp = context.resources.displayMetrics.density
        sizeActive = 26f * dp
        sizeNear   = 20f * dp
        sizeFar    = 16f * dp
        sizeTrans  = 14f * dp
        activePaint.textSize = sizeActive
        nearPaint.textSize   = sizeNear
        farPaint.textSize    = sizeFar
        transPaint.textSize  = sizeTrans
        activePaint.color = colorActive
        nearPaint.color   = colorNear
        farPaint.color    = colorFar
        transPaint.color  = colorTrans
    }

    // ──────────── Public helpers ────────────

    fun setColors(active: Int, near: Int, far: Int, trans: Int) {
        colorActive = active; colorNear = near; colorFar = far; colorTrans = trans
        activePaint.color = active
        nearPaint.color = near
        farPaint.color = far
        transPaint.color = trans
        invalidate()
    }

    // ──────────── Drawing ────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lines.isEmpty()) {
            drawPlaceholder(canvas)
            return
        }
        buildHeightsIfNeeded()

        val cx = width / 2f
        val centerY = height / 2f
        var y = centerY - scrollY

        for (i in lines.indices) {
            val line = lines[i]
            val dist = abs(i - currentLineIndex)
            val (paint, tPaint) = selectPaints(dist)

            // Clip invisible lines for performance
            val blockH = lineHeights.getOrElse(i) { sizeActive }
            if (y + blockH < 0 || y - blockH > height) {
                y += blockH + blockGap
                continue
            }

            // Main text (potentially word-wrapped)
            val textY = y + paint.textSize
            drawMultiline(canvas, line.text, cx, textY, paint)
            var blockBottom = textY + (paint.textSize * (lineSpacing - 1f))

            // Translation
            if (line.translation.isNotEmpty()) {
                tPaint.alpha = paint.alpha
                blockBottom += tPaint.textSize * transSpacing
                drawMultiline(canvas, line.translation, cx, blockBottom, tPaint)
                blockBottom += tPaint.textSize * 0.3f
            }
            y += blockH + blockGap
        }

        drawEdgeFade(canvas)
    }

    private fun selectPaints(dist: Int): Pair<Paint, Paint> = when (dist) {
        0    -> activePaint to transPaint
        1, 2 -> nearPaint to transPaint
        else -> farPaint to transPaint
    }

    private fun drawMultiline(canvas: Canvas, text: String, cx: Float, startY: Float, paint: Paint) {
        val maxWidth = (width * 0.85f)
        if (paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, cx, startY, paint)
            return
        }
        // Simple word-wrap
        val words = text.split(" ")
        var line = StringBuilder()
        var y = startY
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) > maxWidth) {
                canvas.drawText(line.toString(), cx, y, paint)
                y += paint.textSize * lineSpacing
                line = StringBuilder(word)
            } else {
                line = StringBuilder(test)
            }
        }
        if (line.isNotEmpty()) canvas.drawText(line.toString(), cx, y, paint)
    }

    private fun drawPlaceholder(canvas: Canvas) {
        activePaint.alpha = 80
        canvas.drawText("暂无歌词", width / 2f, height / 2f, activePaint)
        activePaint.alpha = 255
    }

    private fun drawEdgeFade(canvas: Canvas) {
        val fadeH = height * 0.18f
        // Top fade
        val topShader = LinearGradient(
            0f, 0f, 0f, fadeH,
            intArrayOf(0xFF000000.toInt(), 0x00000000.toInt()),
            null, Shader.TileMode.CLAMP
        )
        gradientPaint.shader = topShader
        canvas.drawRect(0f, 0f, width.toFloat(), fadeH, gradientPaint)
        // Bottom fade
        val botShader = LinearGradient(
            0f, height - fadeH, 0f, height.toFloat(),
            intArrayOf(0x00000000.toInt(), 0xFF000000.toInt()),
            null, Shader.TileMode.CLAMP
        )
        gradientPaint.shader = botShader
        canvas.drawRect(0f, height - fadeH, width.toFloat(), height.toFloat(), gradientPaint)
    }

    // ──────────── Layout helpers ────────────

    private fun buildHeightsIfNeeded() {
        if (lineHeights.size == lines.size) return
        lineHeights.clear()
        for (i in lines.indices) {
            val dist = abs(i - currentLineIndex)
            val paint = selectPaints(dist).first
            val transH = if (lines[i].translation.isNotEmpty()) sizeTrans * transSpacing else 0f
            val textH = paint.textSize * lineSpacing
            lineHeights.add(textH + transH)
        }
    }

    private fun totalContentHeight(): Float {
        buildHeightsIfNeeded()
        return lineHeights.sum() + blockGap * lines.size
    }

    private fun scrollYForLine(index: Int): Float {
        buildHeightsIfNeeded()
        var y = 0f
        for (i in 0 until min(index, lineHeights.size)) {
            y += lineHeights[i] + blockGap
        }
        return y
    }

    private fun minScrollY() = 0f
    private fun maxScrollY() = max(0f, totalContentHeight() - height)

    // ──────────── Scroll animation ────────────

    private fun smoothScrollToLine(index: Int) {
        if (index < 0 || lines.isEmpty()) return
        buildHeightsIfNeeded()
        val target = scrollYForLine(index).coerceIn(minScrollY(), maxScrollY())
        if (abs(target - scrollY) < 2f) return

        scrollAnimator?.cancel()
        scrollAnimator = ValueAnimator.ofFloat(scrollY, target).apply {
            duration = 450L
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                scrollY = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // ──────────── Touch ────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun handleTap(tapY: Float) {
        val centerY = height / 2f
        var y = centerY - scrollY
        for (i in lines.indices) {
            val h = lineHeights.getOrElse(i) { sizeActive } + blockGap
            if (tapY in y..(y + h)) {
                onSeekToLine?.invoke(lines[i].timeMs)
                return
            }
            y += h
        }
    }
}
