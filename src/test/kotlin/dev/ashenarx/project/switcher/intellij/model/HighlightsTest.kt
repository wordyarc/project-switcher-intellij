package dev.ashenarx.project.switcher.intellij.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HighlightsTest {

    private val item = ProjectItem.Recent(
        displayName = "switcher",
        path = "/home/me/switcher",
        location = "~/switcher",
        branch = null,
        isWorktree = false,
    )

    @Test
    fun `ranges inside the name stay in name coordinates`() {
        val highlights = item.splitHighlights(listOf(0..2))

        assertEquals(Highlights(name = listOf(0..2), location = emptyList()), highlights)
    }

    @Test
    fun `ranges inside the location are rebased onto the location`() {
        val highlights = item.splitHighlights(listOf(9..11))

        assertEquals(Highlights(name = emptyList(), location = listOf(0..2)), highlights)
        assertEquals("~/s", item.location.substring(0..2))
    }

    @Test
    fun `a range spanning the separator is split between the two`() {
        val highlights = item.splitHighlights(listOf(6..10))

        assertEquals(listOf(6..7), highlights.name)
        assertEquals(listOf(0..1), highlights.location)
    }

    @Test
    fun `an item without a location has no location highlights`() {
        val bare = item.copy(location = "")

        assertEquals(Highlights(name = listOf(0..7), location = emptyList()), bare.splitHighlights(listOf(0..7)))
    }

    @Test
    fun `highlights confined to the name make a name-only match`() {
        assertEquals(true, item.splitHighlights(listOf(0..2)).isNameOnly)
        assertEquals(false, item.splitHighlights(listOf(0..2, 9..9)).isNameOnly)
    }
}
