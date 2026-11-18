package dev.kalpane.data

/** Maps every cursor boundary while hiding Markdown syntax in the formatted view. */
internal data class MarkdownProjection(val text: String,val toVisual: IntArray,val toSource: IntArray)
internal fun projectMarkdown(source: String): MarkdownProjection {
    val output=StringBuilder()
    val forward=IntArray(source.length+1)
    val backward=mutableListOf(0)
    var i=0;var inCode=false
    fun skip(count: Int) {repeat(count) {i++;forward[i]=output.length};backward[backward.lastIndex]=i}
    fun emit() {output.append(source[i]);i++;forward[i]=output.length;backward.add(i)}
    fun literal(c: Char) {output.append(c);backward.add(i);forward[i]=output.length}
    while(i<source.length) {
        val end=source.indexOf('\n',i).let {if(it<0) source.length else it}
        val line=source.substring(i,end)
        if(line.startsWith("```")) {skip(end-i);inCode=!inCode}
        else if(inCode) {while(i<end) emit()}
        else {
            val heading=Regex("^(#{1,6})\\s+").find(line)
            if(heading!=null) skip(heading.value.length)
            val checkbox=Regex("^- \\[([ xX])]\\s+").find(source.substring(i,end))
            if(checkbox!=null) {skip(checkbox.value.length);literal(if(checkbox.groupValues[1].lowercase()=="x") '☑' else '☐');literal(' ')}
            while(i<end) {
                if(source[i]=='\\' && i+1<end) {skip(1);emit();continue}
                if(source[i]=='[') {
                    val close=source.indexOf("](",i+1);val finish=if(close in (i+1) until end) source.indexOf(')',close+2) else -1
                    if(finish in (close+2) until end) {skip(1);while(i<close) emit();skip(finish+1-i);continue}
                }
                val marker=listOf("**","~~","*","`").firstOrNull {source.startsWith(it,i)}
                if(marker!=null) {
                    var close=source.indexOf(marker,i+marker.length)
                    while(close in 1 until end && source[close-1]=='\\') close=source.indexOf(marker,close+marker.length)
                    if(close>i+marker.length && close<end) {skip(marker.length);while(i<close) emit();skip(marker.length);continue}
                }
                emit()
            }
        }
        if(i<source.length && source[i]=='\n') emit()
    }
    return MarkdownProjection(output.toString(),forward,backward.toIntArray())
}
