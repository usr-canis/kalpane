package dev.kalpane

import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.kalpane.ui.KalpaneApp

class MainActivity : ComponentActivity() {
    private val vm: NotebookViewModel by viewModels()
    private var lastPen = Long.MIN_VALUE / 2
    private var penDown = false
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KalpaneApp(vm) }
    }
    override fun onPause() {penDown=false;super.onPause()}
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val hasPen=(0 until event.pointerCount).any { event.getToolType(it)==MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(it)==MotionEvent.TOOL_TYPE_ERASER }
        if(hasPen) {
            lastPen=event.eventTime
            val penAtAction=event.getToolType(event.actionIndex) in listOf(MotionEvent.TOOL_TYPE_STYLUS,MotionEvent.TOOL_TYPE_ERASER)
            penDown=!(penAtAction && event.actionMasked in listOf(MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP,MotionEvent.ACTION_CANCEL))
        }
        if(event.actionMasked==MotionEvent.ACTION_CANCEL) penDown=false
        if(!hasPen && event.actionMasked!=MotionEvent.ACTION_CANCEL && (penDown || SystemClock.uptimeMillis()-lastPen<700)) {
            val cancel=MotionEvent.obtain(event); cancel.action=MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancel); cancel.recycle(); return true
        }
        return super.dispatchTouchEvent(event)
    }
}
