package dev.ashenarx.project.switcher.intellij.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SelectionTest {

    private val current = open("current", isCurrent = true)
    private val previous = open("previous")
    private val beta = recent("beta")
    private val gamma = recent("gamma")
    private val rows = listOf(current, previous, beta, gamma)

    @Test
    fun `defaultSelection skips the current project`() {
        assertEquals(previous.id, defaultSelection(rows))
        assertEquals(beta.id, defaultSelection(listOf(current, beta)), "the first recent project comes next")
    }

    @Test
    fun `defaultSelection settles for the current project when nothing else is listed`() {
        assertEquals(current.id, defaultSelection(listOf(current)))
        assertNull(defaultSelection(emptyList()))
    }

    @Test
    fun `a choice holds while the query it was made under does`() {
        assertEquals(gamma.id, resolveSelection(rows, "", Choice(gamma.id, ""), top = null))
        assertEquals(beta.id, resolveSelection(rows, "b", Choice(gamma.id, ""), top = beta), "a new query brings back the top match")
    }

    @Test
    fun `a choice whose row is gone falls back to the top match and then the default`() {
        assertEquals(beta.id, resolveSelection(rows, "b", Choice("recent:/vanished", "b"), top = beta))
        assertEquals(previous.id, resolveSelection(rows, "", Choice("recent:/vanished", ""), top = null))
    }

    @Test
    fun `moveSelection walks the combined list across the section boundary`() {
        assertEquals(beta.id, moveSelection(rows, previous.id, delta = +1))
        assertEquals(previous.id, moveSelection(rows, beta.id, delta = -1))
    }

    @Test
    fun `moveSelection wraps around at both ends`() {
        assertEquals(gamma.id, moveSelection(rows, current.id, delta = -1))
        assertEquals(current.id, moveSelection(rows, gamma.id, delta = +1))
    }

    @Test
    fun `moveSelection wraps a single row onto itself`() {
        assertEquals(current.id, moveSelection(listOf(current), current.id, delta = -1))
        assertEquals(current.id, moveSelection(listOf(current), current.id, delta = +1))
    }

    @Test
    fun `moveSelection falls back to the first item when the selection is gone`() {
        assertEquals(current.id, moveSelection(rows, "recent:/vanished", delta = 0))
        assertNull(moveSelection(emptyList(), current.id, delta = +1))
    }

    @Test
    fun `pageSelection stops at the ends instead of wrapping`() {
        assertEquals(gamma.id, pageSelection(rows, beta.id, delta = +5))
        assertEquals(current.id, pageSelection(rows, beta.id, delta = -5))
        assertEquals(gamma.id, pageSelection(rows, previous.id, delta = +2))
        assertNull(pageSelection(emptyList(), null, delta = +1))
    }

    @Test
    fun `selection after removing moves down the list`() {
        assertEquals(gamma.id, selectionAfterRemoving(rows, beta.id))
        assertEquals(beta.id, selectionAfterRemoving(rows, previous.id))
    }

    @Test
    fun `selection after removing the last item steps back`() {
        assertEquals(beta.id, selectionAfterRemoving(rows, gamma.id))
    }

    @Test
    fun `selection after removing the only item is nothing`() {
        assertNull(selectionAfterRemoving(listOf(beta), beta.id))
        assertNull(selectionAfterRemoving(emptyList(), beta.id))
    }

    @Test
    fun `selection after removing an unknown id is nothing`() {
        assertNull(selectionAfterRemoving(listOf(beta), "recent:/nope"))
        assertNull(selectionAfterRemoving(listOf(beta), null))
    }

    private fun open(name: String, isCurrent: Boolean = false) = ProjectItem.Open(
        locationHash = "hash-$name",
        displayName = name,
        path = "/$name",
        location = "/$name",
        branch = null,
        isWorktree = false,
        isCurrent = isCurrent,
    )

    private fun recent(name: String) = ProjectItem.Recent(
        displayName = name,
        path = "/$name",
        location = "/$name",
        branch = null,
        isWorktree = false,
    )
}
