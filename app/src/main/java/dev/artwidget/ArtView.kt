package dev.artwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import dev.artwidget.art.ArtRenderer
import dev.artwidget.art.ArtSpec
import java.util.concurrent.Executors

/** Renders an ArtSpec off the main thread and draws it filling the view. */
class ArtView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var spec: ArtSpec? = null
        set(value) {
            field = value
            rerender()
        }

    private var bitmap: Bitmap? = null
    private var renderSeq = 0
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = Rect()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = rerender()

    private fun rerender() {
        val s = spec ?: return
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        val seq = ++renderSeq
        executor.execute {
            val bmp = ArtRenderer.render(s, w, h)
            post {
                if (seq == renderSeq) {
                    bitmap = bmp
                    invalidate()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        dst.set(0, 0, width, height)
        canvas.drawBitmap(bmp, null, dst, paint)
    }

    private companion object {
        val executor = Executors.newSingleThreadExecutor()
    }
}
