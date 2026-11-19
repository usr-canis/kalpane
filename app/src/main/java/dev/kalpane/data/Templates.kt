package dev.kalpane.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Template(val name: String, val data: PageData, val builtIn: Boolean)

/**
 * Page templates. Built-ins ship with the app; your own live as pages in a notebook called
 * [NOTEBOOK], so they are versioned and pushed like any other notebook.
 */
object Templates {
    const val NOTEBOOK = "Templates"

    private fun page(text: String, style: PageStyle = PageStyle()) = PageData(text, CanvasDoc(style = style))

    val builtIn = listOf(
        Template("Blank", page(""), true),
        Template("Meeting notes", page(
            "# Meeting · {{date}}\n\n**Attendees:** \n\n## Agenda\n- \n\n## Notes\n\n\n## Decisions\n- \n\n## Action items\n- [ ] "
        ), true),
        Template("Daily journal", page(
            "# {{weekday}}, {{date}}\n\n## Grateful for\n- \n\n## Today\n- [ ] \n\n## Notes\n\n",
            PageStyle(paper = Paper.Ruled),
        ), true),
        Template("Checklist", page("# Checklist\n\n- [ ] \n- [ ] \n- [ ] "), true),
        Template("Lecture notes", page(
            "# Lecture · {{date}}\n\n**Topic:** \n\n## Key ideas\n- \n\n## Notes\n\n\n## Questions\n- \n\n## Summary\n",
            PageStyle(paper = Paper.Ruled),
        ), true),
        Template("Code notes", page(
            "# Code notes · {{date}}\n\n## Problem\n\n\n## Solution\n\n```kotlin\n\n```\n\n## Links\n- ",
            PageStyle(font = "JetBrainsMono", paper = Paper.Grid),
        ), true),
        Template("Sketch", page("", PageStyle(paper = Paper.Dots)), true),
    )

    /** Replaces {{date}}, {{time}} and {{weekday}}. */
    fun fill(text: String, now: LocalDateTime = LocalDateTime.now()): String = text
        .replace("{{date}}", now.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)))
        .replace("{{time}}", now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)))
        .replace("{{weekday}}", now.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)))

    fun instantiate(t: Template, now: LocalDateTime = LocalDateTime.now()): PageData =
        t.data.copy(text = fill(t.data.text, now))
}
