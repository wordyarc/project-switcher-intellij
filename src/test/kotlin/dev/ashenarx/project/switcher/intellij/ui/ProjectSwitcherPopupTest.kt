package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.ui.input.key.Key
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.testFramework.common.ThreadLeakTracker
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import dev.ashenarx.project.switcher.intellij.model.OpenTarget
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import dev.ashenarx.project.switcher.intellij.model.SwitchOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class ProjectSwitcherPopupTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val outcomes = mutableListOf<SwitchOutcome>()

    @BeforeEach
    fun allowSkiaCleaner() {
        ThreadLeakTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "Reference Cleaner")
    }

    @AfterEach
    fun cancelScope() {
        scope.cancel("test finished")
    }

    @Test
    fun `by default the popup shows its title and a speed search appears only once typing starts`() {
        val model = loadedModel(threeProjects())

        runInEdtAndWait {
            popup(model, PopupAppearance()).use { scene ->
                scene.frames()
                assertNotNull(scene.boundsOf("Project Switcher"), "the classic popup is titled like Recent Files")
                assertNull(scene.boundsOf("gam"), "nothing has been typed yet, so there is no speed search")

                scene.type("gam")
                scene.frames()

                assertNotNull(scene.boundsOf("gam"), "the speed search shows what was typed")
            }
        }
    }

    @Test
    fun `the search field takes the place of the title when the setting is on`() {
        val model = loadedModel(threeProjects())

        runInEdtAndWait {
            popup(model, PopupAppearance(searchField = true)).use { scene ->
                scene.frames()

                assertNull(scene.boundsOf("Project Switcher"))
                assertNotNull(scene.boundsOf("Search projects"), "an empty field says what it is for")
            }
        }
    }

    @ParameterizedTest(name = "search field: {0}")
    @ValueSource(booleans = [false, true])
    fun `typed text narrows the list`(searchField: Boolean) {
        val model = loadedModel(threeProjects())

        runInEdtAndWait {
            popup(model, PopupAppearance(searchField = searchField)).use { scene ->
                scene.frames()

                scene.type("gam")
                scene.frames()

                assertNull(scene.boundsOf("beta"), "a row that does not match must leave the list")
                assertNotNull(scene.boundsOf("gamma"))
            }
        }
    }

    @ParameterizedTest(name = "search field: {0}")
    @ValueSource(booleans = [false, true])
    fun `enter opens what was typed even when the keys arrive before the next frame`(searchField: Boolean) {
        val model = loadedModel(threeProjects())

        runInEdtAndWait {
            popup(model, PopupAppearance(searchField = searchField)).use { scene ->
                scene.frames()

                scene.type("gamma")
                scene.press(Key.Enter)
            }
        }

        assertEquals(
            listOf(SwitchOutcome.Reopen(recent("gamma"), OpenTarget.Ask)),
            outcomes,
            "Enter must act on the text already typed, not on the query of an earlier frame",
        )
    }

    @Test
    fun `the footer is off by default`() {
        val model = loadedModel(threeProjects())

        runInEdtAndWait {
            popup(model, PopupAppearance()).use { scene ->
                scene.frames()

                assertNull(scene.boundsOf("~/projects/alpha"), "the location belongs to an opt-in footer")
                assertFalse(scene.texts().any { "switch" in it }, "the shortcut hints belong to an opt-in footer")
            }
        }
    }

    @Test
    fun `the footer shows the location and the shortcuts each under its own setting`() {
        val model = loadedModel(threeProjects())

        runInEdtAndWait {
            popup(model, PopupAppearance(showLocation = true)).use { scene ->
                scene.frames()

                assertNotNull(scene.boundsOf("~/projects/alpha"))
                assertFalse(scene.texts().any { "switch" in it }, "the hints have a setting of their own")
            }

            popup(model, PopupAppearance(showShortcuts = true)).use { scene ->
                scene.frames()

                assertNull(scene.boundsOf("~/projects/alpha"), "the location has a setting of its own")
                assertTrue(scene.texts().any { "switch" in it }, "expected the hints for the selected open project")
            }
        }
    }

    @Test
    fun `the shortcut hints name delete once it closes projects instead of the shortcut`() {
        val model = loadedModel(threeProjects())

        runInEdtAndWait {
            popup(model, PopupAppearance(showShortcuts = true), closeOnDelete = true).use { scene ->
                scene.frames()
                val hints = scene.texts().single { "switch" in it }

                assertTrue("${KeymapUtil.getKeystrokeText(deleteShortcut())} close" in hints, "expected the delete key in \"$hints\"")
                assertFalse(KeymapUtil.getKeystrokeText(closeShortcut()) in hints, "the shortcut no longer closes, so it must not be offered")
            }
        }
    }

    @Test
    fun `moving the selection to a visible row does not scroll the list`() {
        val model = loadedModel(ProjectList(open = emptyList(), recent = manyRows()))

        runInEdtAndWait {
            popup(model, PopupAppearance()).use { scene ->
                scene.frames()
                val before = checkNotNull(scene.boundsOf("project00")) { "the first row must be on screen" }

                scene.press(Key.DirectionDown)
                scene.frames(30)

                assertEquals(
                    before,
                    scene.boundsOf("project00"),
                    "the second row was already visible, so the list must stay where it is",
                )
            }
        }
    }

    @Test
    fun `a row selected below the view is scrolled up to the footer, not to the top`() {
        val model = loadedModel(ProjectList(open = emptyList(), recent = manyRows()))

        runInEdtAndWait {
            popup(model, PopupAppearance(showLocation = true)).use { scene ->
                scene.frames()

                scene.press(Key.MoveEnd)
                scene.frames(30)

                val row = checkNotNull(scene.boundsOf("project39")) { "End must bring the last row into view" }
                val footer = checkNotNull(scene.boundsOf("~/projects/project39")) { "the footer names the selection" }
                assertTrue(row.bottom <= footer.top, "the last row must be fully visible above the footer")
                assertNotNull(scene.boundsOf("project30"), "the rows above the selection must stay in view")
            }
        }
    }

    @Test
    fun `a click with shift opens a recent project in the current window`() {
        val model = loadedModel(threeProjects())

        runInEdtAndWait {
            popup(model, PopupAppearance()).use { scene ->
                scene.frames()

                scene.click("beta", shift = true)
                scene.frames()
            }
        }

        assertEquals(listOf(SwitchOutcome.Reopen(recent("beta"), OpenTarget.CurrentWindow)), outcomes)
    }

    @Test
    fun `a worktree carries a badge in front of its branch and other rows do not`() {
        val model = loadedModel(
            ProjectList(
                open = listOf(open("alpha").copy(branch = "main")),
                recent = listOf(recent("beta").copy(branch = "feature/badge", isWorktree = true)),
            ),
        )

        runInEdtAndWait {
            popup(model, PopupAppearance()).use { scene ->
                scene.frames()

                val badges = scene.imagesDescribed("Git worktree")
                assertEquals(1, badges.size, "only the worktree row may carry the badge")

                val badge = badges.single()
                val branch = checkNotNull(scene.boundsOf("feature/badge")) { "the worktree's branch must be shown" }
                assertTrue(badge.right <= branch.left, "the badge stands in front of the branch it belongs to")
                assertTrue(badge.center.y in branch.top..branch.bottom, "the badge must be on the worktree's row")
            }
        }
    }

    @Test
    fun `a worktree without a known branch still carries the badge`() {
        val model = loadedModel(ProjectList(open = emptyList(), recent = listOf(recent("beta").copy(isWorktree = true))))

        runInEdtAndWait {
            popup(model, PopupAppearance()).use { scene ->
                scene.frames()

                assertEquals(
                    1,
                    scene.imagesDescribed("Git worktree").size,
                    "a detached HEAD or a hidden branch name must not hide that the project is a worktree",
                )
            }
        }
    }

    @ParameterizedTest(name = "search field: {0}")
    @ValueSource(booleans = [false, true])
    fun `with close on delete, backspace erases the search before it removes the row picked after it`(searchField: Boolean) {
        val actions = FakeProjectActions(threeProjects())
        val model = loadedModel(actions)

        runInEdtAndWait {
            popup(model, PopupAppearance(searchField = searchField), closeOnDelete = true).use { scene ->
                scene.frames()

                scene.type("gam")
                repeat(4) { scene.press(Key.Backspace) }
                scene.frames()

                assertEquals("", model.query)
                assertEquals(emptyList<ProjectItem.Open>(), actions.closed, "the press after the search was erased must not close a project")

                scene.press(Key.DirectionDown)
                scene.press(Key.Backspace)
                scene.frames()

                assertEquals(listOf(recent("beta").path), actions.forgotten)
                assertNull(scene.boundsOf("beta"), "the removed row must leave the list")
            }
        }
    }

    private fun threeProjects() = ProjectList(open = listOf(open("alpha")), recent = listOf(recent("beta"), recent("gamma")))

    private fun manyRows() = (0 until 40).map { recent("project%02d".format(it)) }

    private fun loadedModel(projects: ProjectList): ProjectSwitcherModel = loadedModel(FakeProjectActions(projects))

    private fun loadedModel(actions: FakeProjectActions): ProjectSwitcherModel = timeoutRunBlocking {
        val model = ProjectSwitcherModel(
            coroutineScope = scope,
            actions = actions,
            uiContext = Dispatchers.Unconfined,
        )
        model.load()
        while (model.loadState == ProjectLoadState.LOADING) delay(20)
        model
    }

    private fun popup(model: ProjectSwitcherModel, appearance: PopupAppearance, closeOnDelete: Boolean = false) = PopupScene {
        ProjectSwitcherPopup(
            model = model,
            appearance = appearance,
            toggleShortcuts = emptyList(),
            closeOnDelete = closeOnDelete,
            onClose = {},
            onResult = { outcomes += it },
        )
    }
}
