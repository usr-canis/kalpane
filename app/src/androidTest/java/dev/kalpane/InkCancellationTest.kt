package dev.kalpane

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kalpane.data.*
import dev.kalpane.ink.NotebookCanvas
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic MotionEvent coverage; this does not certify physical palm rejection. */
@RunWith(AndroidJUnit4::class)
class InkCancellationTest {
    private fun event(action: Int,time: Long,type: Int,px: Float=200f,py: Float=300f): MotionEvent {
        val properties=MotionEvent.PointerProperties().apply {id=0;toolType=type}
        val coords=MotionEvent.PointerCoords().apply {x=px;y=py;pressure=.5f;size=.1f}
        return MotionEvent.obtain(time-10,time,action,1,arrayOf(properties),arrayOf(coords),0,0,1f,1f,0,0,if(type==MotionEvent.TOOL_TYPE_STYLUS) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN,0)
    }
    @Test fun cancelledPenAndPalmProduceNoCommittedInkOrPageTurns() {
        val commits=AtomicInteger();val turns=AtomicInteger()
        ActivityScenario.launch(InkTestActivity::class.java).use {scenario->
            lateinit var canvas: NotebookCanvas
            scenario.onActivity {activity->
                canvas=NotebookCanvas(activity,DiskStore(File(activity.cacheDir,"ink-test"))).apply {
                    onStroke={_,_->commits.incrementAndGet()};onTurn={turns.incrementAndGet()}
                    bind(Page(),Tool.Pen,0xff222222.toInt(),3f,false,false,28f)
                }
                activity.setContentView(canvas)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(500)
            scenario.onActivity {
                val t=SystemClock.uptimeMillis()
                listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP).forEachIndexed {i,a->event(a,t+i*10,MotionEvent.TOOL_TYPE_FINGER,canvas.width/2f,canvas.height/2f).also {canvas.dispatchTouchEvent(it);it.recycle()}}
                listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_CANCEL).forEachIndexed {i,a->event(a,t+100+i*10,MotionEvent.TOOL_TYPE_STYLUS,canvas.width/2f,canvas.height/2f).also {canvas.dispatchTouchEvent(it);it.recycle()}}
            }
            Thread.sleep(1000)
            assertEquals(0,commits.get());assertEquals(0,turns.get())
        }
    }
    @Test fun cancelledPalmPointerDoesNotCancelActivePen() {
        val commits=AtomicInteger()
        ActivityScenario.launch(InkTestActivity::class.java).use {scenario->
            lateinit var canvas: NotebookCanvas
            scenario.onActivity {activity->
                canvas=NotebookCanvas(activity,DiskStore(File(activity.cacheDir,"ink-palm-test"))).apply {
                    onStroke={_,_->commits.incrementAndGet()}
                    bind(Page(),Tool.Pen,0xff222222.toInt(),3f,false,false,28f)
                }
                activity.setContentView(canvas)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(500)
            scenario.onActivity {
                val t=SystemClock.uptimeMillis()
                val centerX=canvas.width/2f;val centerY=canvas.height/2f
                assertTrue(canvas.width>0 && canvas.height>0)
                event(MotionEvent.ACTION_DOWN,t,MotionEvent.TOOL_TYPE_STYLUS,centerX,centerY).also {canvas.dispatchTouchEvent(it);it.recycle()}
                assertTrue("A pen down at the paper center must start a stroke",canvas.hasActiveStroke)
                fun palm(action: Int,time: Long,flags: Int) {
                    val pen=MotionEvent.PointerProperties().apply {id=0;toolType=MotionEvent.TOOL_TYPE_STYLUS}
                    val palm=MotionEvent.PointerProperties().apply {id=1;toolType=MotionEvent.TOOL_TYPE_FINGER}
                    val a=MotionEvent.PointerCoords().apply {x=centerX+10;y=centerY+10;pressure=.5f;size=.1f}
                    val b=MotionEvent.PointerCoords().apply {x=centerX+60;y=centerY+80;pressure=.4f;size=.8f}
                    MotionEvent.obtain(t,time,action or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,arrayOf(pen,palm),arrayOf(a,b),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,flags).also {canvas.dispatchTouchEvent(it);it.recycle()}
                }
                palm(MotionEvent.ACTION_POINTER_DOWN,t+20,0)
                palm(MotionEvent.ACTION_POINTER_UP,t+40,MotionEvent.FLAG_CANCELED)
                assertTrue("Cancelling a palm must leave the pen stroke active",canvas.hasActiveStroke)
                event(MotionEvent.ACTION_MOVE,t+60,MotionEvent.TOOL_TYPE_STYLUS,centerX+25,centerY+25).also {canvas.dispatchTouchEvent(it);it.recycle()}
                event(MotionEvent.ACTION_UP,t+80,MotionEvent.TOOL_TYPE_STYLUS,centerX+40,centerY+40).also {canvas.dispatchTouchEvent(it);it.recycle()}
            }
            val deadline=SystemClock.uptimeMillis()+5000
            while(commits.get()==0 && SystemClock.uptimeMillis()<deadline) Thread.sleep(50)
            assertEquals(1,commits.get())
        }
    }

}
