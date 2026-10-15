package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.PowerSaveMode
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

@TestApplication
class ProjectIconLoaderTest {

    private val tempDir = tempPathFixture()

    private val loader get() = ProjectIconLoader.getInstance()

    @Test
    fun `warm up does its work once per IDE run`() = timeoutRunBlocking {
        assertTrue(loader.warmUp(), "the first call has to do the warming")
        assertFalse(loader.warmUp(), "a second call must not repeat the icon passes")
    }

    @Test
    fun `every requested project gets an icon`() = timeoutRunBlocking {
        val projects = projects("first", "second", "third")
        val emitted = ConcurrentHashMap<String, BufferedImage>()

        loader.loadIcons(projects) { key, image -> emitted[key] = image }

        assertEquals(projects.map { it.path }.toSet(), emitted.keys)
    }

    @Test
    fun `an icon rendered once is ready for the next popup at once`() = timeoutRunBlocking {
        val projects = projects("cached")

        loader.loadIcons(projects) { _, _ -> }

        assertEquals(
            projects.map { it.path }.toSet(),
            loader.cachedIcons(projects.map { it.path }).keys,
            "the next popup must be able to draw the icon in its first frame",
        )
    }

    @Test
    fun `in power save mode the loader does not wait for icons the IDE will not compute`() = timeoutRunBlocking {
        val projects = projects("saving-a", "saving-b", "saving-c", "saving-d", "saving-e")
        val wasEnabled = PowerSaveMode.isEnabled()
        PowerSaveMode.setEnabled(true)

        try {
            val elapsed = measureTime { loader.loadIcons(projects) { _, _ -> } }

            assertTrue(elapsed < 1.seconds, "the popup kept polling for $elapsed although nothing would ever settle")
        } finally {
            PowerSaveMode.setEnabled(wasEnabled)
        }
    }

    private fun projects(vararg names: String): List<ProjectItem> = names.map { name ->
        val path = tempDir.get().resolve(name).createDirectories().invariantSeparatorsPathString
        ProjectItem.Recent(displayName = name, path = path, location = path, branch = null, isWorktree = false)
    }
}
