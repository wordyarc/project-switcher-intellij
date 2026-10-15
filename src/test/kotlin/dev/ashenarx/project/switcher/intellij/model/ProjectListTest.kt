package dev.ashenarx.project.switcher.intellij.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProjectListTest {

    @Test
    fun `rankedBy keeps items matching by name or location`() {
        val list = ProjectList(
            open = listOf(open("alpha", "~/me/alpha")),
            recent = listOf(recent("beta", "/work/beta"), recent("gamma", "~/me/gamma")),
        )

        val byName = list.rankedBy(matchingFirst("beta"))
        assertEquals(emptyList<ProjectItem.Open>(), byName.rows.open)
        assertEquals(listOf("recent:/work/beta"), byName.rows.recent.map { it.id })

        val byLocation = list.rankedBy(matchingFirst("~/me"))
        assertEquals(listOf("open:hash-alpha", "recent:~/me/gamma"), byLocation.rows.all.map { it.id })
    }

    @Test
    fun `rankedBy that matches nothing yields an empty list and no top match`() {
        val list = ProjectList(open = listOf(open("alpha", "/a")), recent = listOf(recent("beta", "/b")))

        val ranking = list.rankedBy { null }

        assertEquals(true, ranking.rows.isEmpty)
        assertNull(ranking.top)
    }

    @Test
    fun `rankedBy sorts by degree inside a section without merging the two`() {
        val list = ProjectList(
            open = listOf(open("alpha", "/a"), open("beta", "/b")),
            recent = listOf(recent("gamma", "/c"), recent("delta", "/d")),
        )

        val ranked = list.rankedBy { text -> nameMatch(if (text.startsWith("beta") || text.startsWith("delta")) 10 else 1) }

        assertEquals(listOf("open:hash-beta", "open:hash-alpha"), ranked.rows.open.map { it.id })
        assertEquals(listOf("recent:/d", "recent:/c"), ranked.rows.recent.map { it.id })
    }

    @Test
    fun `a match in the name outranks a better one in the location`() {
        val list = ProjectList(
            open = emptyList(),
            recent = listOf(recent("tools", "~/alpha/tools"), recent("xalpha", "~/x/xalpha")),
        )

        val ranked = list.rankedBy { text ->
            if (text.startsWith("tools")) Match(degree = 900, ranges = listOf(8..12)) else nameMatch(degree = 1)
        }

        assertEquals(listOf("recent:~/x/xalpha", "recent:~/alpha/tools"), ranked.rows.recent.map { it.id })
    }

    @Test
    fun `rankedBy leaves equally scored items in their original order`() {
        val list = ProjectList(
            open = emptyList(),
            recent = listOf(recent("gamma", "/c"), recent("delta", "/d"), recent("epsilon", "/e")),
        )

        assertEquals(
            listOf("recent:/c", "recent:/d", "recent:/e"),
            list.rankedBy { nameMatch(7) }.rows.recent.map { it.id },
        )
    }

    @Test
    fun `rankedBy reports the highlights of every row it keeps`() {
        val list = ProjectList(open = emptyList(), recent = listOf(recent("tools", "~/alpha/tools")))

        val ranking = list.rankedBy { Match(degree = 1, ranges = listOf(0..1, 8..12)) }

        assertEquals(Highlights(name = listOf(0..1), location = listOf(2..6)), ranking.highlights["recent:~/alpha/tools"])
    }

    @Test
    fun `the top match reaches across the section boundary`() {
        val list = ProjectList(
            open = listOf(open("alpha", "/a")),
            recent = listOf(recent("beta", "/b")),
        )

        assertEquals("recent:/b", list.rankedBy { text -> nameMatch(if (text.startsWith("beta")) 900 else 100) }.top?.id)
        assertEquals("open:hash-alpha", list.rankedBy { text -> nameMatch(if (text.startsWith("beta")) 100 else 900) }.top?.id)
    }

    @Test
    fun `the top match breaks a tie in favour of the open project`() {
        val list = ProjectList(
            open = listOf(open("alpha", "/a")),
            recent = listOf(recent("beta", "/b")),
        )

        assertEquals("open:hash-alpha", list.rankedBy { nameMatch(500) }.top?.id)
    }

    @Test
    fun `the top match of an empty list is null`() {
        assertNull(ProjectList.EMPTY.rankedBy { nameMatch(1) }.top)
    }

    @Test
    fun `the top match compares only the head of each section`() {
        val list = ProjectList(
            open = listOf(open("alpha", "/a"), open("zeta", "/z")),
            recent = listOf(recent("beta", "/b")),
        )

        val ranking = list.rankedBy { text ->
            nameMatch(if (text.startsWith("zeta")) 999 else if (text.startsWith("beta")) 500 else 100)
        }

        assertEquals("open:hash-zeta", ranking.top?.id, "zeta heads the ranked open section")
    }

    @Test
    fun `searchText covers both name and location`() {
        assertEquals("alpha ~/alpha", open("alpha", "~/alpha").searchText)
        assertEquals("alpha", open("alpha", "").searchText)
    }

    @Test
    fun `duplicateNames includes names repeated across sections`() {
        val list = ProjectList(
            open = listOf(open("same", "/work/first"), open("unique", "/work/unique")),
            recent = listOf(recent("same", "/work/second")),
        )

        assertEquals(setOf("same"), list.duplicateNames)
    }

    @Test
    fun `without drops the item from whichever section holds it`() {
        val alpha = open("alpha", "/a")
        val beta = recent("beta", "/b")
        val list = ProjectList(open = listOf(alpha), recent = listOf(beta))

        assertEquals(ProjectList(open = emptyList(), recent = listOf(beta)), list.without(alpha))
        assertEquals(ProjectList(open = listOf(alpha), recent = emptyList()), list.without(beta))
    }

    private fun matchingFirst(fragment: String): (String) -> Match? = { text ->
        text.indexOf(fragment).takeIf { it >= 0 }?.let { Match(degree = 0, ranges = listOf(it until it + fragment.length)) }
    }

    private fun nameMatch(degree: Int) = Match(degree = degree, ranges = listOf(0..0))

    private fun open(name: String, location: String, isCurrent: Boolean = false) = ProjectItem.Open(
        locationHash = "hash-$name",
        displayName = name,
        path = location,
        location = location,
        branch = null,
        isWorktree = false,
        isCurrent = isCurrent,
    )

    private fun recent(name: String, location: String) = ProjectItem.Recent(
        displayName = name,
        path = location,
        location = location,
        branch = null,
        isWorktree = false,
    )
}
