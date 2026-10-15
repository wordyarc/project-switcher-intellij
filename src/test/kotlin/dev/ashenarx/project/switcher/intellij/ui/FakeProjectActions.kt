package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.ui.graphics.ImageBitmap
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import dev.ashenarx.project.switcher.intellij.model.ProjectList
import kotlinx.coroutines.CompletableDeferred

internal class FakeProjectActions(
    private var projects: ProjectList,
    private val snapshot: ProjectList? = null,
    private val cachedIcons: Map<String, ImageBitmap> = emptyMap(),
    private val gone: Set<String> = emptySet(),
) : ProjectActions {

    val closed = mutableListOf<ProjectItem.Open>()
    val forgotten = mutableListOf<String>()

    var collectGate: CompletableDeferred<Unit>? = null
    var failure: Exception? = null

    override fun snapshot(): ProjectList? = snapshot

    override suspend fun collect(): ProjectList {
        collectGate?.await()
        failure?.let { throw it }
        return projects
    }

    fun publish(projects: ProjectList) {
        this.projects = projects
    }

    override fun cachedIcons(keys: Collection<String>): Map<String, ImageBitmap> = cachedIcons.filterKeys { it in keys }

    override suspend fun loadIcons(items: List<ProjectItem>, emit: suspend (String, ImageBitmap) -> Unit) = Unit

    override suspend fun missing(paths: Collection<String>): Set<String> = gone.intersect(paths.toSet())

    override fun close(project: ProjectItem.Open) {
        closed += project
        projects = ProjectList(
            open = projects.open - project,
            recent = projects.recent + with(project) { ProjectItem.Recent(displayName, path, location, branch, isWorktree) },
        )
    }

    override fun forget(path: String) {
        forgotten += path
        projects = ProjectList(
            open = projects.open,
            recent = projects.recent.filterNot { it.path == path },
        )
    }
}
