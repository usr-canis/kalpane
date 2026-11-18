package dev.kalpane.ink

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import dev.kalpane.data.Markdown

/** Render markup only when a block is not being edited; the saved source stays intact. */
internal fun markdownDisplay(source: String): CharSequence {
    val result=SpannableStringBuilder()
    Markdown.parse(source).forEachIndexed {i,block->
        if(i>0) result.append('\n')
        val start=result.length
        block.checked?.let {result.append(if(it) "☑ " else "☐ ")}
        val contentStart=result.length
        result.append(block.text)
        val end=result.length
        val flags=Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        if(end>start) {
            if(block.heading>0) {result.setSpan(StyleSpan(Typeface.BOLD),start,end,flags);result.setSpan(RelativeSizeSpan((1.55f-(block.heading-1)*.1f).coerceAtLeast(1f)),start,end,flags)}
            if(block.code) result.setSpan(TypefaceSpan("monospace"),start,end,flags)
        }
        block.spans.forEach {span->
            val a=contentStart+span.start;val b=contentStart+span.end
            if(a<b) when {
                span.kind=="bold" -> result.setSpan(StyleSpan(Typeface.BOLD),a,b,flags)
                span.kind=="italic" -> result.setSpan(StyleSpan(Typeface.ITALIC),a,b,flags)
                span.kind=="strike" -> result.setSpan(StrikethroughSpan(),a,b,flags)
                span.kind=="code" -> result.setSpan(TypefaceSpan("monospace"),a,b,flags)
                span.kind.startsWith("link:") -> result.setSpan(UnderlineSpan(),a,b,flags)
            }
        }
    }
    return result
}
