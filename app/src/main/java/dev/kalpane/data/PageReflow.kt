package dev.kalpane.data

/** Move downstream content when a text block changes height, keeping stacking order. */
internal fun Page.resizeTextAndReflow(layerId: String, newHeight: Float): Page {
    val target=layers.firstOrNull {it.id==layerId && it.kind==LayerKind.Text} ?: return this
    val height=newHeight.coerceAtLeast(1f)
    val delta=height-target.height
    if(delta==0f) return this
    val boundary=target.y+target.height
    val moved=layers.map {l->
        if(l.id==layerId) l.copy(height=height)
        else {
            val top=if(l.kind==LayerKind.Ink) l.points.firstOrNull()?.y ?: l.y else l.y
            if(top<boundary) l else if(l.kind==LayerKind.Ink) l.copy(points=l.points.map {it.copy(y=it.y+delta)}) else l.copy(y=l.y+delta)
        }
    }
    return copy(height=(this.height+delta.coerceAtLeast(0f)).coerceAtMost(10000f),layers=moved)
}
