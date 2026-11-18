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
    private fun event(action: Int, downTime: Long, type: Int, px: Float, py: Float): MotionEvent {
        val properties=MotionEvent.PointerProperties().apply {id=0;toolType=type}
        val coords=MotionEvent.PointerCoords().apply {x=px;y=py;pressure=.5f;size=.1f}
        return MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,1,arrayOf(properties),arrayOf(coords),0,0,1f,1f,0,0,if(type==MotionEvent.TOOL_TYPE_STYLUS) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN,0)
    }
    private fun withCanvas(test: (ActivityScenario<InkTestActivity>,NotebookCanvas,AtomicInteger,AtomicInteger)->Unit) {
        val commits=AtomicInteger();val turns=AtomicInteger()
        ActivityScenario.launch(InkTestActivity::class.java).use {scenario->
            lateinit var canvas: NotebookCanvas
            scenario.onActivity {activity->
                canvas=NotebookCanvas(activity,DiskStore(File(activity.cacheDir,"ink-test"))).apply {
                    onStroke={_,layer->assertTrue(layer.points.isNotEmpty());commits.incrementAndGet()}
                    onTurn={turns.incrementAndGet()}
                    bind(Page(),Tool.Pen,0xff222222.toInt(),3f,false,false,28f)
                }
                activity.setContentView(canvas)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(500)
            scenario.onActivity {assertTrue(canvas.width>0 && canvas.height>0)}
            test(scenario,canvas,commits,turns)
        }
    }
    private fun send(scenario: ActivityScenario<InkTestActivity>,canvas: NotebookCanvas,action: Int,downTime: Long,type: Int=MotionEvent.TOOL_TYPE_STYLUS,offset: Float=0f) {
        scenario.onActivity {
            event(action,downTime,type,canvas.width/2f+offset,canvas.height/2f+offset).also {canvas.dispatchTouchEvent(it);it.recycle()}
        }
        // Let each real-time event cross a render frame, as physical input does.
        Thread.sleep(40)
    }
    private fun awaitCommit(commits: AtomicInteger) {
        val deadline=SystemClock.uptimeMillis()+5000
        while(commits.get()==0 && SystemClock.uptimeMillis()<deadline) Thread.sleep(50)
        assertEquals(1,commits.get())
    }
    @Test fun completedPenCommitsVectorPoints() = withCanvas {scenario,canvas,commits,turns->
        val t=SystemClock.uptimeMillis()
        send(scenario,canvas,MotionEvent.ACTION_DOWN,t)
        scenario.onActivity {assertTrue(canvas.hasActiveStroke)}
        send(scenario,canvas,MotionEvent.ACTION_MOVE,t,offset=25f)
        send(scenario,canvas,MotionEvent.ACTION_UP,t,offset=40f)
        scenario.onActivity {assertFalse(canvas.hasActiveStroke)}
        awaitCommit(commits);assertEquals(0,turns.get())
    }
    @Test fun cancelledPenAndPalmProduceNoCommittedInkOrPageTurns() = withCanvas {scenario,canvas,commits,turns->
        val touchTime=SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP).forEach {send(scenario,canvas,it,touchTime,MotionEvent.TOOL_TYPE_FINGER)}
        val penTime=SystemClock.uptimeMillis()
        send(scenario,canvas,MotionEvent.ACTION_DOWN,penTime)
        scenario.onActivity {assertTrue(canvas.hasActiveStroke)}
        send(scenario,canvas,MotionEvent.ACTION_MOVE,penTime,offset=25f)
        send(scenario,canvas,MotionEvent.ACTION_CANCEL,penTime,offset=40f)
        scenario.onActivity {assertFalse(canvas.hasActiveStroke)}
        Thread.sleep(1000)
        assertEquals(0,commits.get());assertEquals(0,turns.get())
    }
    @Test fun cancelledPalmPointerDoesNotCancelActivePen() = withCanvas {scenario,canvas,commits,turns->
        val t=SystemClock.uptimeMillis()
        send(scenario,canvas,MotionEvent.ACTION_DOWN,t)
        scenario.onActivity {assertTrue("A pen down at the paper center must start a stroke",canvas.hasActiveStroke)}
        fun palm(action: Int,flags: Int) {
            scenario.onActivity {
                val pen=MotionEvent.PointerProperties().apply {id=0;toolType=MotionEvent.TOOL_TYPE_STYLUS}
                val palm=MotionEvent.PointerProperties().apply {id=1;toolType=MotionEvent.TOOL_TYPE_FINGER}
                val centerX=canvas.width/2f;val centerY=canvas.height/2f
                val a=MotionEvent.PointerCoords().apply {x=centerX+10;y=centerY+10;pressure=.5f;size=.1f}
                val b=MotionEvent.PointerCoords().apply {x=centerX+60;y=centerY+80;pressure=.4f;size=.8f}
                MotionEvent.obtain(t,SystemClock.uptimeMillis(),action or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,arrayOf(pen,palm),arrayOf(a,b),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,flags).also {canvas.dispatchTouchEvent(it);it.recycle()}
            }
            Thread.sleep(40)
        }
        palm(MotionEvent.ACTION_POINTER_DOWN,0)
        palm(MotionEvent.ACTION_POINTER_UP,MotionEvent.FLAG_CANCELED)
        scenario.onActivity {assertTrue("Cancelling a palm must leave the pen stroke active",canvas.hasActiveStroke)}
        send(scenario,canvas,MotionEvent.ACTION_MOVE,t,offset=25f)
        send(scenario,canvas,MotionEvent.ACTION_UP,t,offset=40f)
        scenario.onActivity {assertFalse(canvas.hasActiveStroke)}
        awaitCommit(commits);assertEquals(0,turns.get())
    }
}
