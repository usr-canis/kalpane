package dev.kalpane.ui

import android.view.MotionEvent
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes

/**
 * Low-latency "wet" ink: while the pen is down, AndroidX Ink draws the stroke in a
 * front-buffered overlay above the whole window. When the stroke ends, the page's own
 * renderer takes over and the overlay stroke is removed a couple of frames later.
 */
object WetInk {
    var view: InProgressStrokesView? = null
    private var pointerId = -1
    var active = false
        private set

    /** The overlay fills the window, so map an event to window coordinates whatever space it arrived in. */
    private inline fun <T> inWindow(event: MotionEvent, block: (MotionEvent) -> T): T? {
        val v = view ?: return null
        val location = IntArray(2).also { v.getLocationOnScreen(it) }
        val index = event.findPointerIndex(pointerId).takeIf { it >= 0 } ?: event.actionIndex
        val copy = MotionEvent.obtain(event)
        copy.offsetLocation(event.getRawX(index) - location[0] - event.getX(index), event.getRawY(index) - location[1] - event.getY(index))
        return try { block(copy) } finally { copy.recycle() }
    }

    fun start(event: MotionEvent, argb: Int, sizePx: Float): Boolean {
        val v = view ?: return false
        pointerId = event.getPointerId(event.actionIndex)
        val brush = Brush.createWithColorIntArgb(StockBrushes.pressurePen(), argb, sizePx, 0.1f)
        return runCatching { inWindow(event) { v.startStroke(it, pointerId, brush) } }.isSuccess.also { active = it }
    }

    fun move(event: MotionEvent) {
        val v = view ?: return
        if (!active || event.findPointerIndex(pointerId) < 0) return
        runCatching { inWindow(event) { v.addToStroke(it, pointerId, null) } }
    }

    fun finish(event: MotionEvent) {
        val v = view ?: return
        if (!active) return
        active = false
        runCatching { inWindow(event) { v.finishStroke(it, pointerId) } }
    }

    fun cancel(event: MotionEvent?) {
        val v = view ?: return
        if (!active) return
        active = false
        if (event != null) runCatching { inWindow(event) { v.cancelStroke(it, pointerId) } }
    }
}
