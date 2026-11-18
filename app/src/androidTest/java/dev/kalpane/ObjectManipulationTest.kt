package dev.kalpane

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.kalpane.data.*
import dev.kalpane.ink.NotebookCanvas
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class ObjectManipulationTest {
    @Test fun stylusDragCommitsImagePositionAndHonorsLock() {
        val original=Layer(kind=LayerKind.Image,x=400f,y=600f,width=200f,height=220f)
        val page=Page(layers=listOf(original))
        val edited=AtomicReference<Page>()
        ActivityScenario.launch(InkTestActivity::class.java).use {scenario->
            lateinit var canvas: NotebookCanvas
            scenario.onActivity {activity->
                canvas=NotebookCanvas(activity,DiskStore(File(activity.cacheDir,"object-test"))).apply {
                    onEdit={edited.set(it)}
                    bind(page,Tool.Select,0xff222222.toInt(),3f,false,false,28f)
                }
                activity.setContentView(canvas)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            fun drag() {
                val down=SystemClock.uptimeMillis()
                listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP).forEachIndexed {i,action->
                    scenario.onActivity {
                        val pointer=MotionEvent.PointerProperties().apply {id=0;toolType=MotionEvent.TOOL_TYPE_STYLUS}
                        val coords=MotionEvent.PointerCoords().apply {x=canvas.width/2f+i*20f;y=canvas.height/2f+i*20f;pressure=.5f}
                        MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,1,arrayOf(pointer),arrayOf(coords),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0).also {canvas.dispatchTouchEvent(it);it.recycle()}
                    }
                    Thread.sleep(40)
                }
            }
            drag()
            val moved=edited.get()?.layers?.single() ?: error("Pen-up must commit the image move")
            assertTrue(moved.x>original.x);assertTrue(moved.y>original.y)
            assertEquals(original.width,moved.width);assertEquals(original.height,moved.height)
            edited.set(null)
            scenario.onActivity {canvas.bind(page.copy(layers=listOf(original.copy(locked=true))),Tool.Select,0xff222222.toInt(),3f,false,false,28f)}
            drag()
            assertNull("A locked image must stay in position",edited.get())
        }
    }
}
