package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.vcs.RecentProjectsBranchesProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.SystemProperties
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString

@TestApplication
class ProjectCatalogTest {

    private companion object {
        // The sorting test needs this casing.
        val zebra = indexedProjectFixture("Zebra")
        val apple = indexedProjectFixture("apple")
        val worktree = indexedProjectFixture("worktree", linkedWorktreeFixture("worktree"))

        val branchProviderEp =
            ExtensionPointName<RecentProjectsBranchesProvider>("com.intellij.recentProjectsBranchesProvider")
    }

    private val tempDir = tempPathFixture()

    private val catalog get() = ProjectCatalog.getInstance()

    @BeforeEach
    @AfterEach
    fun clearRecents() {
        recentState().additionalInfo.clear()
    }

    @Test
    fun `open projects are listed from the most recently activated`() = timeoutRunBlocking {
        val manager = RecentProjectsManagerBase.getInstanceEx()
        manager.setActivationTimestamp(zebra.get(), 1_000)
        manager.setActivationTimestamp(apple.get(), 2_000)

        val names = catalog.collect(currentProject = null).open.map { it.displayName }

        assertEquals(
            listOf("apple", "Zebra"),
            names.filter { it == "apple" || it == "Zebra" },
            "the project used last has to come first so that switching back is one keystroke",
        )
    }

    @Test
    fun `open projects nobody has activated yet are listed by name, ignoring case`() = timeoutRunBlocking {
        val names = catalog.collect(currentProject = null).open.map { it.displayName }

        assertEquals(
            listOf("apple", "Zebra"),
            names.filter { it == "apple" || it == "Zebra" },
            "sorting on the raw string would put Zebra first",
        )
    }

    @Test
    fun `the project the popup was invoked from is listed first and is the only one marked current`() =
        timeoutRunBlocking {
            val manager = RecentProjectsManagerBase.getInstanceEx()
            manager.setActivationTimestamp(zebra.get(), 2_000)
            manager.setActivationTimestamp(apple.get(), 1_000)

            val open = catalog.collect(currentProject = apple.get()).open

            assertEquals("apple", open.first().displayName)
            assertEquals(listOf("apple"), open.filter { it.isCurrent }.map { it.displayName })
        }

    @Test
    fun `an open project carries the location hash the opener matches on`() = timeoutRunBlocking {
        val item = catalog.collect(currentProject = null).open.single { it.displayName == "apple" }

        assertEquals(apple.get().locationHash, item.locationHash)
        assertEquals("open:${apple.get().locationHash}", item.id)
    }

    @Test
    fun `a project that is both open and recent is listed once, as open`() = timeoutRunBlocking {
        seedRecent(pathOf(apple.get()), name = "apple")

        val list = catalog.collect(currentProject = null)

        assertEquals(listOf("apple"), list.all.filter { it.displayName == "apple" }.map { it.displayName })
        assertTrue(list.open.any { it.displayName == "apple" }, "the open entry is the one that must survive")
        assertEquals(emptyList<String>(), list.recent.map { it.displayName }.filter { it == "apple" })
    }

    @Test
    fun `recent projects keep the platform's most-recently-used order`() = timeoutRunBlocking {
        val oldest = seedRecent(name = "oldest")
        val middle = seedRecent(name = "middle")
        val newest = seedRecent(name = "newest")

        val recent = catalog.collect(currentProject = null).recent

        assertEquals(listOf("newest", "middle", "oldest"), recent.map { it.displayName })
        assertEquals(listOf(newest, middle, oldest), recent.map { it.path })
    }

    @Test
    fun `a recent project is identified by its normalised path`() = timeoutRunBlocking {
        val path = seedRecent(name = "solo")

        val item = catalog.collect(currentProject = null).recent.single()

        assertEquals(path, item.path)
        assertEquals("recent:$path", item.id)
        assertEquals(false, item.path.contains('\\'), "paths must reach the UI system-independent")
    }

    @Test
    fun `a location under the user home is shown from the tilde`() = timeoutRunBlocking {
        val home = SystemProperties.getUserHome().toProjectPath()
        seedRecent("$home/work/solo", name = "solo")

        val item = catalog.collect(currentProject = null).recent.single()

        assertEquals(presentableProjectPath("$home/work/solo"), item.location)
        assertTrue(item.location.startsWith("~"), "the home directory must not be part of what the search sees")
    }

    @Test
    fun `the branch reported for a path reaches both sections`(@TestDisposable disposable: Disposable) =
        timeoutRunBlocking {
            val recentPath = seedRecent(name = "detached")
            val openPath = pathOf(apple.get())
            seedRecent(openPath, name = "apple")

            ExtensionTestUtil.maskExtensions(
                branchProviderEp,
                listOf(FakeBranches(mapOf(recentPath to "release/24.1", openPath to "main"))),
                disposable,
                fireEvents = false,
            )

            val list = catalog.collect(currentProject = null)

            assertEquals("main", list.open.single { it.displayName == "apple" }.branch)
            assertEquals("release/24.1", list.recent.single { it.displayName == "detached" }.branch)
        }

    @Test
    fun `a project with no branch information simply has none`() = timeoutRunBlocking {
        seedRecent(name = "plain")

        assertNull(catalog.collect(currentProject = null).recent.single().branch)
    }

    @Test
    fun `a project in a linked git worktree is marked in both sections`() = timeoutRunBlocking {
        val main = gitCheckout(tempDir.get().resolve("main"))
        val recentPath = linkedWorktree(main, tempDir.get().resolve("feature")).invariantSeparatorsPathString
        seedRecent(recentPath, name = "feature")

        val list = catalog.collect(currentProject = null)

        assertTrue(
            list.open.single { it.locationHash == worktree.get().locationHash }.isWorktree,
            "an open worktree must carry the badge too",
        )
        assertTrue(list.recent.single { it.displayName == "feature" }.isWorktree)
    }

    @Test
    fun `the main checkout of a repository with worktrees is not marked`() = timeoutRunBlocking {
        val main = gitCheckout(tempDir.get().resolve("main"))
        linkedWorktree(main, tempDir.get().resolve("feature"))
        seedRecent(main.invariantSeparatorsPathString, name = "main")

        assertFalse(
            catalog.collect(currentProject = null).recent.single().isWorktree,
            "the badge tells a worktree apart from the repository it was created from",
        )
    }

    @Test
    fun `when the recent list cannot be built the open projects are still listed`(
        @TestDisposable disposable: Disposable,
    ) = timeoutRunBlocking {
        seedRecent(name = "unreadable")
        ExtensionTestUtil.maskExtensions(branchProviderEp, listOf(ThrowingBranches()), disposable, fireEvents = false)

        val list = catalog.collect(currentProject = null)

        assertTrue(list.open.any { it.displayName == "apple" }, "a broken recent list must not hide the open projects")
        assertEquals(emptyList<ProjectItem.Recent>(), list.recent)
    }

    @Test
    fun `forgetting a project drops it from the next collect`() = timeoutRunBlocking {
        val kept = seedRecent(name = "kept")
        val dropped = seedRecent(name = "dropped")

        catalog.forget(dropped)

        val recent = catalog.collect(currentProject = null).recent

        assertEquals(listOf(kept), recent.map { it.path })
    }

    @Test
    fun `forgetting an open project does not remove it from the open section`() = timeoutRunBlocking {
        val openPath = pathOf(apple.get())
        seedRecent(openPath, name = "apple")

        catalog.forget(openPath)

        val list = catalog.collect(currentProject = null)

        assertTrue(list.open.any { it.displayName == "apple" }, "the project is still open, so it stays listed")
    }

    @Test
    fun `the snapshot is the last collected list, marked for the project that asks`() = timeoutRunBlocking {
        val manager = RecentProjectsManagerBase.getInstanceEx()
        manager.setActivationTimestamp(zebra.get(), 2_000)
        manager.setActivationTimestamp(apple.get(), 1_000)
        val collected = catalog.collect(currentProject = zebra.get())

        val snapshot = checkNotNull(catalog.snapshot(apple.get())) { "a collected list must be kept for the next popup" }

        assertEquals(collected.all.map { it.id }.toSet(), snapshot.all.map { it.id }.toSet())
        assertEquals("apple", snapshot.open.first().displayName, "the asking project moves to the top")
        assertEquals(listOf("apple"), snapshot.open.filter { it.isCurrent }.map { it.displayName })
    }

    @Test
    fun `recent projects whose directory is gone are reported, the others are not`() = timeoutRunBlocking {
        val present = tempDir.get().resolve("present").createDirectories().invariantSeparatorsPathString
        val gone = tempDir.get().resolve("gone").invariantSeparatorsPathString

        assertEquals(setOf(gone), catalog.missing(listOf(present, gone)))
    }

    // Seed oldest first: the platform hands recents back in reverse.
    private fun seedRecent(path: String? = null, name: String): String {
        val resolved = path ?: "/tmp/project-switcher-test/$name"

        recentState().additionalInfo[resolved] = RecentProjectMetaInfo().also {
            // customProjectName resolves synchronously and, unlike displayName, leaves branches enabled.
            it.customProjectName = name
        }

        return resolved
    }

    private fun recentState() = RecentProjectsManagerBase.getInstanceEx().state

    private fun pathOf(project: Project): String =
        checkNotNull(project.basePath) { "an opened project must have a base path" }

    private class FakeBranches(private val branches: Map<String, String>) : RecentProjectsBranchesProvider {
        override fun getCurrentBranch(projectPath: String, nameIsDistinct: Boolean): String? = branches[projectPath]
    }

    private class ThrowingBranches : RecentProjectsBranchesProvider {
        override fun getCurrentBranch(projectPath: String, nameIsDistinct: Boolean): String? =
            throw IllegalStateException("branch cache is broken")
    }
}

private fun indexedProjectFixture(
    name: String,
    path: TestFixture<Path> = tempPathFixture(subdirName = name),
): TestFixture<Project> = testFixture {
    val project = projectFixture(path, openAfterCreation = true).init()
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    initialized(project) {}
}

private fun linkedWorktreeFixture(name: String): TestFixture<Path> = testFixture {
    val root = tempPathFixture().init()
    initialized(linkedWorktree(gitCheckout(root.resolve("main")), root.resolve(name))) {}
}
