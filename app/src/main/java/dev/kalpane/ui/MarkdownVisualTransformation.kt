package dev.kalpane.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.em
import dev.kalpane.data.Markdown
import dev.kalpane.data.projectMarkdown

/** The model retains raw Markdown; cursor offsets refer to the original source. */
internal object MarkdownVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val projection=projectMarkdown(text.text)
        val blocks=Markdown.parse(text.text)
        val annotated=buildAnnotatedString {
            append(projection.text)
            var start=0
            blocks.forEach {block->
                val prefix=if(block.checked!=null) 2 else 0
                val contentStart=start+prefix
                val end=(contentStart+block.text.length).coerceAtMost(length)
                if(end>start && block.heading>0) addStyle(SpanStyle(fontWeight=FontWeight.Bold,fontSize=(1.55f-(block.heading-1)*.1f).coerceAtLeast(1f).em),start,end)
                if(end>start && block.code) addStyle(SpanStyle(fontFamily=FontFamily.Monospace),start,end)
                block.spans.forEach {span->
                    val a=(contentStart+span.start).coerceAtMost(length);val b=(contentStart+span.end).coerceAtMost(length)
                    if(a<b) {
                        val style=when {
                            span.kind=="bold" -> SpanStyle(fontWeight=FontWeight.Bold)
                            span.kind=="italic" -> SpanStyle(fontStyle=FontStyle.Italic)
                            span.kind=="strike" -> SpanStyle(textDecoration=TextDecoration.LineThrough)
                            span.kind=="code" -> SpanStyle(fontFamily=FontFamily.Monospace)
                            span.kind.startsWith("link:") -> SpanStyle(textDecoration=TextDecoration.Underline)
                            else -> SpanStyle()
                        }
                        addStyle(style,a,b)
                    }
                }
                start=end+1
            }
        }
        return TransformedText(annotated,object: OffsetMapping {
            override fun originalToTransformed(offset: Int)=projection.toVisual[offset.coerceIn(projection.toVisual.indices)]
            override fun transformedToOriginal(offset: Int)=projection.toSource[offset.coerceIn(projection.toSource.indices)]
        })
    }
}
