package dev.ashenarx.project.switcher.intellij.ui

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SwitcherIconsTest {

    @Test
    fun `the worktree badge exists for the light and the dark theme`() {
        val key = SwitcherIcons.Worktree
        val path = key.path(isNewUi = true)

        assertNotNull(key.iconClass.classLoader.getResource(path), "a wrong path leaves an empty gap instead of the badge")
        assertNotNull(
            key.iconClass.classLoader.getResource(path.replace(".svg", "_dark.svg")),
            "without a _dark variant the badge keeps its light colours in a dark theme",
        )
    }
}
