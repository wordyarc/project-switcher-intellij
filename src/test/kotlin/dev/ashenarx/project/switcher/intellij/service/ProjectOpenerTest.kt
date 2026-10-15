package dev.ashenarx.project.switcher.intellij.service

import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseHandler
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import dev.ashenarx.project.switcher.intellij.model.OpenTarget
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration.Companion.seconds

@TestApplication
class ProjectOpenerTest {

    private companion object {
        val closeHandlerEp = ExtensionPointName<ProjectCloseHandler>("com.intellij.projectCloseHandler")
    }

    private val tempDir = tempPathFixture()

    private val opener get() = ProjectOpener.getInstance()

    @Test
    fun `a recent project whose directory is gone is reported to the user`(@TestDisposable disposable: Disposable) =
        timeoutRunBlocking {
            val missing = tempDir.get().resolve("deleted-behind-our-back")
            val item = recentItem(missing, name = "Ghost Project")

            val notification = awaitNotification(disposable) {
                opener.reopen(item, OpenTarget.Ask, contextProject = null)
            }

            assertEquals(NotificationType.ERROR, notification.type)
            assertEquals("Cannot open project", notification.title)
            assertTrue(
                notification.content.contains("Ghost Project"),
                "the user must be told which project failed, got: ${notification.content}",
            )
        }

    @Test
    fun `a path that exists but cannot be opened is reported too`(@TestDisposable disposable: Disposable) =
        timeoutRunBlocking {
            // Skips the missing-directory branch; the platform then returns a null project silently.
            val file = tempDir.get().resolve("notes.txt").createFile()
            val item = recentItem(file, name = "Not A Project")

            val notification = awaitNotification(disposable) {
                opener.reopen(item, OpenTarget.Ask, contextProject = null)
            }

            assertEquals(NotificationType.ERROR, notification.type)
            assertTrue(notification.content.contains("Not A Project"), "got: ${notification.content}")
        }

    @Test
    fun `an existing project is opened and left open`() = timeoutRunBlocking {
        val path = tempDir.get().resolve("reopened").createDirectories()

        withContext(Dispatchers.EDT) {
            opener.reopen(recentItem(path, name = "reopened"), OpenTarget.NewWindow, contextProject = null)
        }

        val opened = awaitOpenProject(path)
        try {
            assertEquals(path.invariantSeparatorsPathString, opened.basePath)
            assertFalse(opened.isDisposed)
        } finally {
            ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(opened, save = false)
        }
    }

    @Test
    fun `opening an existing project raises no failure notification`(@TestDisposable disposable: Disposable) =
        timeoutRunBlocking {
            val path = tempDir.get().resolve("quiet").createDirectories()
            val notifications = recordNotifications(disposable)

            withContext(Dispatchers.EDT) {
                opener.reopen(recentItem(path, name = "quiet"), OpenTarget.CurrentWindow, contextProject = null)
            }

            val opened = awaitOpenProject(path)
            try {
                assertEquals(emptyList<String>(), notifications.map { it.content })
            } finally {
                ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(opened, save = false)
            }
        }

    @Test
    fun `closing an open project disposes it`() = timeoutRunBlocking {
        val project = openProject("closable")
        val item = openItem(project)

        val closed = withContext(Dispatchers.EDT) { opener.close(item) }

        assertTrue(closed, "the caller focuses the next project only after a close that happened")
        assertTrue(project.isDisposed, "closeAndDispose must have run")
        assertFalse(ProjectManagerEx.getOpenProjects().contains(project))
    }

    @Test
    fun `a close the platform refuses skips the cleanup that follows it`(@TestDisposable disposable: Disposable) =
        timeoutRunBlocking {
            val project = openProject("undeletable")
            val recents = RecentProjectsManagerBase.getInstanceEx().state.additionalInfo
            val info = RecentProjectMetaInfo().also { it.opened = false }
            recents[project.basePath!!] = info

            ExtensionTestUtil.maskExtensions(
                closeHandlerEp,
                listOf(ProjectCloseHandler { false }),
                disposable,
                fireEvents = false,
            )

            try {
                val closed = withContext(Dispatchers.EDT) { opener.close(openItem(project)) }

                assertFalse(closed, "a refused close must not hand the focus to another project")
                assertFalse(project.isDisposed, "the veto must be honoured")
                // updateLastProjectPath would have flipped this on for a project that is still open.
                assertFalse(info.opened, "cleanup must not run when the close was refused")
            } finally {
                recents.clear()
                ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(project, save = false)
            }
        }

    @Test
    fun `closing a project that is already gone is a no-op`() = timeoutRunBlocking {
        val project = openProject("vanished")
        val item = openItem(project)
        ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(project, save = false)

        withContext(Dispatchers.EDT) {
            opener.close(item)
            opener.focus(item)
        }
    }

    @Test
    fun `an open project is matched by location hash, not by path`() = timeoutRunBlocking {
        val project = openProject("hashed")

        try {
            val wrongHash = openItem(project).copy(locationHash = "not-the-right-hash")

            withContext(Dispatchers.EDT) { opener.close(wrongHash) }

            assertFalse(project.isDisposed, "a mismatched location hash must not close anything")
        } finally {
            ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(project, save = false)
        }
    }

    private fun recentItem(path: Path, name: String) = ProjectItem.Recent(
        displayName = name,
        path = path.invariantSeparatorsPathString,
        location = path.invariantSeparatorsPathString,
        branch = null,
        isWorktree = false,
    )

    private fun openItem(project: Project) = ProjectItem.Open(
        locationHash = project.locationHash,
        displayName = project.name,
        path = project.basePath.orEmpty(),
        location = project.basePath.orEmpty(),
        branch = null,
        isWorktree = false,
        isCurrent = false,
    )

    private suspend fun openProject(name: String): Project {
        val path = tempDir.get().resolve(name).createDirectories()
        return ProjectManagerEx.getInstanceEx().openProjectAsync(
            path,
            createTestOpenProjectOptions(runPostStartUpActivities = false),
        )!!
    }

    private suspend fun awaitOpenProject(path: Path): Project {
        val wanted = path.invariantSeparatorsPathString

        val project = withTimeoutOrNull(60.seconds) {
            while (true) {
                ProjectManagerEx.getOpenProjects().firstOrNull { it.basePath == wanted }?.let { return@withTimeoutOrNull it }
                delay(50)
            }
            @Suppress("UNREACHABLE_CODE") null
        }

        return checkNotNull(project) { "no project opened at $wanted" }
    }

    private fun recordNotifications(disposable: Disposable): List<Notification> {
        val received = mutableListOf<Notification>()
        subscribe(disposable) { received += it }
        return received
    }

    private suspend fun awaitNotification(disposable: Disposable, trigger: () -> Unit): Notification {
        val received = CompletableDeferred<Notification>()
        subscribe(disposable) { received.complete(it) }

        withContext(Dispatchers.EDT) { trigger() }

        return received.await()
    }

    private fun subscribe(disposable: Disposable, onNotify: (Notification) -> Unit) {
        ApplicationManager.getApplication().messageBus.connect(disposable)
            .subscribe(Notifications.TOPIC, object : Notifications {
                override fun notify(notification: Notification) {
                    if (notification.groupId == "Project Switcher") onNotify(notification)
                }
            })
    }
}
