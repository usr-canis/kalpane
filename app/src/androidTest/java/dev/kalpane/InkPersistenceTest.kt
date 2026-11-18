package dev.kalpane

import androidx.ink.strokes.StrokeInput
import androidx.test.platform.app.InstrumentationRegistry
import dev.kalpane.data.*
import dev.kalpane.ink.rebuildInk
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class InkPersistenceTest {
    @Test fun inkWithoutDeviceCalibrationRebuildsAfterOfflineReload() {
        val store=DiskStore(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"ink-persistence"))
        val layer=Layer(kind=LayerKind.Ink,points=listOf(InkPoint(100f,200f,0),InkPoint(200f,230f,30),InkPoint(300f,210f,60)))
        val doc=Document(notebooks=listOf(Notebook(pages=listOf(Page(layers=listOf(layer))))))
        store.save("","main",Workspace(document=doc))
        val restored=store.read("","main").document.validate().pages().single().layers.single()
        val native=rebuildInk(restored)
        assertEquals(3,native.inputs.size)
        val sample=StrokeInput();native.inputs.populate(1,sample)
        assertEquals(200f,sample.x);assertEquals(230f,sample.y)
        assertEquals(StrokeInput.NO_STROKE_UNIT_LENGTH,sample.strokeUnitLengthCm)
        assertEquals(StrokeInput.NO_PRESSURE,sample.pressure)
        val calibrated=layer.copy(points=layer.points.map {it.copy(pressure=.6f,tilt=.4f,orientation=1.2f,unitCm=.01f)})
        rebuildInk(calibrated).inputs.populate(1,sample)
        assertEquals(.6f,sample.pressure);assertEquals(.4f,sample.tiltRadians)
        assertEquals(1.2f,sample.orientationRadians);assertEquals(.01f,sample.strokeUnitLengthCm)
    }
}
