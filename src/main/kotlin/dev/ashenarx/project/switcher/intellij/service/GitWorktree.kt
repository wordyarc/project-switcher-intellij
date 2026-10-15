package dev.ashenarx.project.switcher.intellij.service

import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

private const val DOT_GIT = ".git"
private const val GIT_DIR_PREFIX = "gitdir: "
private const val COMMON_DIR = "commondir"

internal fun Path.isInLinkedGitWorktree(): Boolean =
    try {
        generateSequence(toAbsolutePath()) { it.parent }
            .map { it.resolve(DOT_GIT) }
            .firstOrNull { it.exists() }
            ?.linkedGitDir()
            ?.resolve(COMMON_DIR)
            ?.isRegularFile() == true
    } catch (_: IOException) {
        false
    } catch (_: InvalidPathException) {
        false
    }

private fun Path.linkedGitDir(): Path? {
    if (!isRegularFile()) return null

    val content = readText().trimEnd()
    if (!content.startsWith(GIT_DIR_PREFIX)) return null

    return parent.resolve(content.removePrefix(GIT_DIR_PREFIX))
}
