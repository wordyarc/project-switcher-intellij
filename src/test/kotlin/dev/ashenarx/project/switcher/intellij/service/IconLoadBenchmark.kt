package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.DeferredIcon
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime

@TestApplication
class IconLoadBenchmark {

    @Test
    fun `measure the icon passes over the real recent projects`() = timeoutRunBlocking(5.minutes) {
        val paths = realRecentProjectPaths()
        assumeTrue(paths.isNotEmpty(), "no local recentProjects.xml to measure against")

        val catalog = ProjectCatalog.getInstance()
        val loader = ProjectIconLoader.getInstance()
        println("=== ${paths.size} real recent projects ===")

        val collectCold = measureTime { catalog.collect(currentProject = null) }
        val collectWarm = measureTime { catalog.collect(currentProject = null) }
        println("collect    cold : ${collectCold.inWholeMilliseconds} ms")
        println("collect    warm : ${collectWarm.inWholeMilliseconds} ms")

        val projects = paths.map {
            ProjectItem.Recent(displayName = it, path = it, location = it, branch = null, isWorktree = false)
        }

        val cold = measureTime { loader.loadIcons(projects) { _, _ -> } }
        println("loadIcons  before warm up : ${cold.inWholeMilliseconds} ms")

        val warm = measureTime { loader.loadIcons(projects) { _, _ -> } }
        println("loadIcons  after  warm up : ${warm.inWholeMilliseconds} ms")

        val manager = RecentProjectsManager.getInstance() as RecentProjectsManagerBase
        for (size in listOf(20, 60)) {
            val unsettled = AtomicInteger()
            val fetch = measureTime {
                for (path in paths) {
                    val icon = manager.getProjectIcon(path, true, size)
                    if (icon is DeferredIcon && !icon.isDone) unsettled.incrementAndGet()
                }
            }
            println(
                "getProjectIcon ${size}px : ${fetch.inWholeMilliseconds} ms for ${paths.size} icons," +
                    " ${unsettled.get()} handed back unsettled"
            )
        }
    }

    private fun realRecentProjectPaths(): List<String> {
        val home = Path.of(System.getProperty("user.home"))
        val config = home.resolve("Library/Application Support/JetBrains/IntelliJIdea2026.2/options/recentProjects.xml")
        if (!config.exists()) return emptyList()

        return Regex("key=\"\\\$USER_HOME\\\$(/[^\"]+)\"")
            .findAll(Files.readString(config))
            .map { home.resolve(it.groupValues[1].removePrefix("/")).toString() }
            .filter { Path.of(it).exists() }
            .distinct()
            .toList()
    }
}
