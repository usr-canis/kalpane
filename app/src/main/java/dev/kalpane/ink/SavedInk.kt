package dev.kalpane.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import dev.kalpane.data.Layer

internal fun inkBrush(color: Int,size: Float) = Brush.createWithColorIntArgb(StockBrushes.pressurePen(),color,size,0.1f)

internal fun rebuildInk(layer: Layer): Stroke {
    val inputs=MutableStrokeInputBatch()
    layer.points.forEach {p->
        // Ink uses 0 for unknown physical units, unlike its -1 pressure/tilt sentinels.
        // Accept the earlier -1 representation when importing an older local snapshot.
        inputs.add(if(layer.stylus) InputToolType.STYLUS else InputToolType.TOUCH,p.x,p.y,p.time,p.unitCm.coerceAtLeast(0f),p.pressure,p.tilt,p.orientation)
    }
    return Stroke(inkBrush(layer.color,layer.penWidth),inputs)
}
