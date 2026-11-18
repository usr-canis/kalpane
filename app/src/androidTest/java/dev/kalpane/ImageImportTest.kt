package dev.kalpane

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import dev.kalpane.data.ImageAssets
import dev.kalpane.data.hash
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ImageImportTest {
    @Test fun cameraOrientationAndOriginalImageSurviveDecoding() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"rotated-camera-fixture.jpg")
        val bitmap=Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.RED)
            drawRect(20f,0f,40f,20f,Paint().apply {color=Color.BLUE})
        }
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,100,it)}
        bitmap.recycle()
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val original=hash(file.readBytes())
        val size=ImageAssets.dimensions(file.readBytes())
        assertEquals(20,size.width);assertEquals(40,size.height)
        val decoded=ImageAssets.decode(file,20)
        assertEquals(10,decoded.width);assertEquals(20,decoded.height)
        val top=decoded.getPixel(5,3);val bottom=decoded.getPixel(5,17)
        assertTrue(Color.red(top)>Color.blue(top));assertTrue(Color.blue(bottom)>Color.red(bottom))
        assertEquals(original,hash(file.readBytes()))
        decoded.recycle();file.delete()
    }
}
