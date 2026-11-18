package dev.kalpane

import dev.kalpane.data.*
import org.junit.Assert.*
import org.junit.Test

class PageReflowTest {
    @Test fun growingTextMovesDownstreamInkAndImagesWithoutChangingStacking() {
        val text=Layer(id="t",kind=LayerKind.Text,y=50f,height=100f)
        val ink=Layer(id="i",kind=LayerKind.Ink,points=listOf(InkPoint(10f,200f,0),InkPoint(20f,220f,1)))
        val image=Layer(id="p",kind=LayerKind.Image,y=250f)
        val page=Page(layers=listOf(image,text,ink))
        val result=page.resizeTextAndReflow("t",150f)
        assertEquals(listOf("p","t","i"),result.layers.map {it.id})
        assertEquals(300f,result.layers[0].y,0f)
        assertEquals(250f,result.layers[2].points[0].y,0f)
        assertEquals(270f,result.layers[2].points[1].y,0f)
    }
    @Test fun shrinkReversesDownstreamMovementAndKeepsOriginalInkMetadata() {
        val text=Layer(id="t",kind=LayerKind.Text,y=50f,height=100f)
        val ink=Layer(id="i",kind=LayerKind.Ink,points=listOf(InkPoint(10f,200f,0,pressure=.4f,tilt=.2f),InkPoint(20f,220f,20,pressure=.8f,tilt=.4f)))
        val page=Page(layers=listOf(text,ink))
        val grown=page.resizeTextAndReflow("t",150f)
        val restored=grown.resizeTextAndReflow("t",100f)
        assertEquals(page.layers,restored.layers)
        assertEquals(page.layers.map {it.id},restored.layers.map {it.id})
    }
}
