package dev.ashenarx.project.switcher.intellij.service

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.writeText

internal fun gitCheckout(root: Path): Path {
    root.resolve(".git").createDirectories().resolve("HEAD").writeText("ref: refs/heads/main\n")
    return root
}

internal fun linkedWorktree(repository: Path, checkout: Path, relativePaths: Boolean = false): Path {
    val adminDir = repository.resolve(".git/worktrees/${checkout.name}").createDirectories()
    adminDir.resolve("HEAD").writeText("ref: refs/heads/${checkout.name}\n")
    adminDir.resolve("commondir").writeText("../..\n")
    adminDir.resolve("gitdir").writeText("${checkout.resolve(".git").invariantSeparatorsPathString}\n")

    val gitDir = if (relativePaths) checkout.relativize(adminDir) else adminDir
    checkout.createDirectories().resolve(".git").writeText("gitdir: ${gitDir.invariantSeparatorsPathString}\n")
    return checkout
}

internal fun submodule(repository: Path, name: String): Path {
    repository.resolve(".git/modules/$name").createDirectories().resolve("HEAD").writeText("ref: refs/heads/main\n")

    val checkout = repository.resolve(name).createDirectories()
    checkout.resolve(".git").writeText("gitdir: ../.git/modules/$name\n")
    return checkout
}
