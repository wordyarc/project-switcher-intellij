package dev.ashenarx.project.switcher.intellij.service

import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.service.ProjectIconLoader.Companion.distinctIconItems
import dev.ashenarx.project.switcher.intellij.service.ProjectIconLoader.Companion.iconSizePasses
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IconSizePassesTest {

    @Test
    fun `icons are asked for once per non-blank key in encounter order`() {
        val items = listOf("/first", "", "/second", "/first", "   ", "/second").map(::recent)

        assertEquals(listOf("/first", "/second"), distinctIconItems(items).map { it.path })
    }

    @Test
    fun `an open project and its recent entry share one icon`() {
        val open =
            ProjectItem.Open("hash", "first", "/first", "~/first", branch = null, isWorktree = false, isCurrent = false)

        assertEquals(listOf(open), distinctIconItems(listOf(open, recent("/first"))))
    }

    @Test
    fun `the first pass asks for the size the platform itself caches`() {
        for (scale in listOf(1f, 1.25f, 2f, 3f, 4f)) {
            assertEquals(20, iconSizePasses(scale).first(), "scale $scale")
        }
    }

    @Test
    fun `passes are ordered coarse to crisp`() {
        val passes = iconSizePasses(2f)

        assertEquals(listOf(20, 40), passes)
        assertTrue(passes == passes.sorted(), "expected ascending, got $passes")
    }

    @Test
    fun `a non-HiDPI screen gets a single pass`() {
        assertEquals(listOf(20), iconSizePasses(1f))
    }

    @Test
    fun `a fractional scale rounds up to the next whole pass`() {
        assertEquals(listOf(20, 40), iconSizePasses(1.25f))
        assertEquals(listOf(20, 60), iconSizePasses(2.5f))
    }

    @Test
    fun `an extreme scale is clamped`() {
        assertEquals(listOf(20, 60), iconSizePasses(4f))
        assertEquals(listOf(20, 60), iconSizePasses(100f))
    }

    @Test
    fun `a degenerate scale falls back to a single pass`() {
        assertEquals(listOf(20), iconSizePasses(0f))
        assertEquals(listOf(20), iconSizePasses(-1f))
    }

    private fun recent(path: String) =
        ProjectItem.Recent(displayName = path, path = path, location = path, branch = null, isWorktree = false)
}
