package kr.local.galaxybattery

import android.content.Context
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver

/** The source must be a sibling subtree, never an ancestor containing this view. */
class FrostedBackdrop(context: Context, private val source: View,
                      private val palette: AppPalette, enabled: Boolean,
                      private val radiusDp: Float) : View(context) {
    private val gpu = if (enabled && Build.VERSION.SDK_INT >= 31) GpuBackdrop() else null
    private val sourcePosition = IntArray(2)
    private val position = IntArray(2)
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE
    private var observer: ViewTreeObserver? = null
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        // Do not run a timer or request frames when the page is idle. A dirty page,
        // scrolling or a layout change refreshes the backdrop in the same traversal.
        source.getLocationInWindow(sourcePosition)
        getLocationInWindow(position)
        val x = sourcePosition[0] - position[0]
        val y = sourcePosition[1] - position[1]
        if (source.isDirty || x != lastX || y != lastY) {
            lastX = x; lastY = y
            invalidate()
        }
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (gpu != null) {
            observer = viewTreeObserver.also { it.addOnPreDrawListener(preDraw) }
        }
    }

    override fun onDetachedFromWindow() {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw)
        observer = null
        gpu?.release()
        lastX = Int.MIN_VALUE; lastY = Int.MIN_VALUE
        super.onDetachedFromWindow()
    }

    fun refresh() { if (gpu != null) invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (gpu == null || !canvas.isHardwareAccelerated || source.width == 0 || source.height == 0) {
            canvas.drawColor(palette.navigation)
            return
        }
        source.getLocationInWindow(sourcePosition)
        getLocationInWindow(position)
        gpu.draw(canvas, sourcePosition[0] - position[0], sourcePosition[1] - position[1])
        // Only the glass tint is translucent; icons and labels are separate sharp views.
        canvas.drawColor((palette.navigation and 0x00ffffff) or (72 shl 24))
    }

    @android.annotation.TargetApi(31)
    private inner class GpuBackdrop {
        private val radius = radiusDp * resources.displayMetrics.density
        private val padding = kotlin.math.ceil(radius * 3).toInt()
        private val node = RenderNode("Battery glass backdrop").apply {
            setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
        }

        fun draw(canvas: Canvas, x: Int, y: Int) {
            // Record at native resolution with a surrounding halo so content beyond
            // the glass edges contributes to the blur. No bitmap readback/upload.
            val w = width + padding * 2
            val h = height + padding * 2
            node.setPosition(-padding, -padding, width + padding, height + padding)
            val recording = node.beginRecording(w, h)
            try {
                recording.drawColor(palette.background)
                recording.translate((x + padding).toFloat(), (y + padding).toFloat())
                source.draw(recording)
            } finally {
                node.endRecording()
            }
            canvas.drawRenderNode(node)
        }

        fun release() { node.discardDisplayList() }
    }
}
