package dev.kalpane.data

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Size
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/** ImageDecoder honors camera EXIF orientation; originals remain untouched in asset storage. */
object ImageAssets {
    fun dimensions(bytes: ByteArray): Size {
        var dimensions=Size(0,0)
        val probe=ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) {decoder,info,_->
            dimensions=info.size
            decoder.setTargetSize(1,1)
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        }
        probe.recycle()
        return dimensions
    }

    fun decode(file: File, maxDimension: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) {decoder,info,_->
            val scale=(maxDimension.toFloat()/max(info.size.width,info.size.height)).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width*scale).roundToInt().coerceAtLeast(1),(info.size.height*scale).roundToInt().coerceAtLeast(1))
            decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        }
}
