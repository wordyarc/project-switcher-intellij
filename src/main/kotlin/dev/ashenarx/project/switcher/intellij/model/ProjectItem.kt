package dev.ashenarx.project.switcher.intellij.model

sealed interface ProjectItem {
    val id: String
    val displayName: String
    val path: String
    val location: String
    val branch: String?
    val isWorktree: Boolean

    val isCurrent: Boolean get() = false

    data class Open(
        val locationHash: String,
        override val displayName: String,
        override val path: String,
        override val location: String,
        override val branch: String?,
        override val isWorktree: Boolean,
        override val isCurrent: Boolean,
    ) : ProjectItem {
        override val id: String get() = "open:$locationHash"
    }

    data class Recent(
        override val displayName: String,
        override val path: String,
        override val location: String,
        override val branch: String?,
        override val isWorktree: Boolean,
    ) : ProjectItem {
        override val id: String get() = "recent:$path"
    }
}
