package dev.kalpane

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.ScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.kalpane.data.*
import dev.kalpane.ink.NotebookCanvas
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Synthetic viewport coverage; physical stylus behavior must be checked separately. */
class InkViewportTest {
    @Test fun scrolledLongPageUsesBoundedInkSurfaceAndPreservesPageCoordinates() {
        val result=AtomicReference<Layer>()
        ActivityScenario.launch(InkTestActivity::class.java).use {scenario->
            lateinit var canvas: NotebookCanvas
            lateinit var scroller: ScrollView
            scenario.onActivity {activity->
                canvas=NotebookCanvas(activity,DiskStore(File(activity.cacheDir,"viewport-test"))).apply {
                    onStroke={_,layer->result.set(layer)}
                    bind(Page(height=10000f),Tool.Pen,0xff222222.toInt(),3f,false,false,28f)
                }
                scroller=ScrollView(activity).apply {addView(canvas,android.widget.FrameLayout.LayoutParams(-1,8000))}
                activity.setContentView(scroller)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {scroller.scrollTo(0,2000)}
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(300)
            var expectedY=0f
            val down=SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP).forEachIndexed {i,action->
                scenario.onActivity {
                    val x=canvas.width/2f;val y=scroller.scrollY+scroller.height/2f+i*12f
                    if(i==0) {
                        assertTrue("Ink surface must be smaller than the long page",canvas.liveViewportHeight<canvas.height)
                        assertTrue("Ink surface must follow scrolling",canvas.liveViewportOffset>0f)
                        expectedY=canvas.pagePoint(x,y).y
                    }
                    val prop=MotionEvent.PointerProperties().apply {id=0;toolType=MotionEvent.TOOL_TYPE_STYLUS}
                    val coords=MotionEvent.PointerCoords().apply {this.x=x;this.y=y;pressure=.5f}
                    MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,1,arrayOf(prop),arrayOf(coords),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0).also {canvas.dispatchTouchEvent(it);it.recycle()}
                }
                Thread.sleep(40)
            }
            val deadline=SystemClock.uptimeMillis()+5000
            while(result.get()==null && SystemClock.uptimeMillis()<deadline) Thread.sleep(50)
            val stroke=result.get() ?: error("The stroke was not committed")
            assertEquals(expectedY,stroke.points.first().y,2f)
        }
    }
}
