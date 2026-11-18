package dev.kalpane

import androidx.compose.ui.text.AnnotatedString
import dev.kalpane.ui.MarkdownVisualTransformation
import org.junit.Assert.*
import org.junit.Test

class MarkdownVisualTransformationTest {
    @Test fun formattingKeepsRawSourceAndValidCursorMapping() {
        val source="# Heading **bold** [link](https://example.com)"
        val result=MarkdownVisualTransformation.filter(AnnotatedString(source))
        assertEquals("Heading bold link",result.text.text)
        assertTrue(result.text.spanStyles.isNotEmpty())
        for(i in 0..source.length) assertTrue(result.offsetMapping.originalToTransformed(i) in 0..result.text.length)
        for(i in 0..result.text.length) assertTrue(result.offsetMapping.transformedToOriginal(i) in 0..source.length)
    }
}
