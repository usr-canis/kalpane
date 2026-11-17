package dev.kalpane.ink

/** Conservative touch arbitration, independent of device brand. */
class InputPolicy {
    var penDown: Boolean = false; private set
    var lastPenTime: Long = Long.MIN_VALUE / 2; private set
    var touchSequenceContaminated: Boolean = false; private set
    fun pen(time: Long, down: Boolean) { lastPenTime = time; penDown = down; touchSequenceContaminated = true }
    fun beginTouch(time: Long) { touchSequenceContaminated = penDown || time - lastPenTime < 700 }
    fun cancelled() { touchSequenceContaminated = true }
    fun touchAllowed(time: Long) = !penDown && !touchSequenceContaminated && time - lastPenTime >= 700
    fun drawFinger(time: Long, enabled: Boolean, pointerCount: Int, size: Float) = enabled && pointerCount == 1 && size < 0.45f && touchAllowed(time)
    fun turnPage(time: Long, navigationTool: Boolean, zoom: Float, dx: Float, dy: Float, duration: Long) =
        navigationTool && touchAllowed(time) && zoom <= 1.02f && kotlin.math.abs(dx) > 120 && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 2 && duration in 50..700
}
