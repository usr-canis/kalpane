package dev.kalpane.ink

import dev.kalpane.data.*
import kotlin.math.*

/** Split a stroke at the eraser path, preserving layer order and input metadata. */
internal fun eraseParts(layer: Layer, path: List<Pair<Float,Float>>, radius: Float): List<Layer> {
    if (layer.points.isEmpty() || path.isEmpty()) return listOf(layer)
    fun distance(x: Float,y: Float): Float = if(path.size==1) hypot(x-path[0].first,y-path[0].second) else path.zipWithNext().minOf { (a,b)->
        val dx=b.first-a.first;val dy=b.second-a.second
        val t=if(dx*dx+dy*dy==0f) 0f else (((x-a.first)*dx+(y-a.second)*dy)/(dx*dx+dy*dy)).coerceIn(0f,1f)
        hypot(x-a.first-t*dx,y-a.second-t*dy)
    }
    val sampled=mutableListOf(layer.points.first())
    layer.points.zipWithNext().forEach { (a,b)->
        val count=ceil(hypot(b.x-a.x,b.y-a.y)/(radius/2).coerceAtLeast(1f)).toInt().coerceIn(1,1000)
        for(i in 1..count) {val t=i.toFloat()/count;sampled+=a.copy(x=a.x+(b.x-a.x)*t,y=a.y+(b.y-a.y)*t,time=(a.time+(b.time-a.time)*t).toLong(),pressure=if(a.pressure>=0 && b.pressure>=0) a.pressure+(b.pressure-a.pressure)*t else a.pressure,tilt=if(a.tilt>=0 && b.tilt>=0) a.tilt+(b.tilt-a.tilt)*t else a.tilt,orientation=if(a.orientation>=0 && b.orientation>=0) a.orientation+(b.orientation-a.orientation)*t else a.orientation) }
    }
    if(sampled.none {distance(it.x,it.y)<=radius+layer.penWidth/2}) return listOf(layer)
    val groups=mutableListOf<MutableList<InkPoint>>();var group=mutableListOf<InkPoint>()
    sampled.forEach {p->if(distance(p.x,p.y)<=radius+layer.penWidth/2) {if(group.isNotEmpty()) {groups+=group;group=mutableListOf()}} else group+=p}
    if(group.isNotEmpty()) groups+=group
    return groups.map {layer.copy(id=id(),points=it)}
}
