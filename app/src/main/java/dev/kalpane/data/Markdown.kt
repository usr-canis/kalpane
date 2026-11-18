package dev.kalpane.data

/** Markdown is stored verbatim. Presentation hides markers only outside the active editor. */
internal data class MarkdownSpan(val start: Int,val end: Int,val kind: String)
internal data class MarkdownBlock(val text: String,val spans: List<MarkdownSpan>,val heading: Int=0,val checked: Boolean?=null,val code: Boolean=false)
internal object Markdown {
    fun parse(source: String): List<MarkdownBlock> {
        var inCode=false
        return source.lines().map { line ->
            if(line.startsWith("```")) { inCode=!inCode; MarkdownBlock("",emptyList(),code=true) }
            else if(inCode) MarkdownBlock(line,emptyList(),code=true)
            else parseLine(line)
        }
    }
    private fun parseLine(raw: String): MarkdownBlock {
        var text=raw;var heading=0;var checked:Boolean?=null
        val h=Regex("^(#{1,6})\\s+").find(text)
        if(h!=null) {heading=h.groupValues[1].length;text=text.substring(h.range.last+1)}
        val check=Regex("^- \\[([ xX])]\\s+").find(text)
        if(check!=null) {checked=check.groupValues[1].lowercase()=="x";text=text.substring(check.range.last+1)}
        val out=StringBuilder();val spans=mutableListOf<MarkdownSpan>();var i=0
        val marks=listOf("**" to "bold","~~" to "strike","*" to "italic","`" to "code")
        while(i<text.length) {
            if(text[i]=='\\' && i+1<text.length) {out.append(text[i+1]);i+=2;continue}
            if(text[i]=='[') {
                val close=text.indexOf("](",i+1);val end=if(close>=0) text.indexOf(')',close+2) else -1
                if(close>=0 && end>=0) {val start=out.length;out.append(text.substring(i+1,close));spans+=MarkdownSpan(start,out.length,"link:"+text.substring(close+2,end));i=end+1;continue}
            }
            val mark=marks.firstOrNull {text.startsWith(it.first,i)}
            if(mark!=null) {
                var end=text.indexOf(mark.first,i+mark.first.length)
                while(end>0 && text[end-1]=='\\') end=text.indexOf(mark.first,end+mark.first.length)
                if(end>i+mark.first.length) {val start=out.length;out.append(text.substring(i+mark.first.length,end));spans+=MarkdownSpan(start,out.length,mark.second);i=end+mark.first.length;continue}
            }
            out.append(text[i++])
        }
        return MarkdownBlock(out.toString(),spans,heading,checked)
    }
}
