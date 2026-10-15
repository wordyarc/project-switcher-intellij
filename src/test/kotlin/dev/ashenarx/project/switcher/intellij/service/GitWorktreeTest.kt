package dev.ashenarx.project.switcher.intellij.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile

class GitWorktreeTest {

    @TempDir
    lateinit var dir: Path

    private val emptyGitConfig by lazy { dir.resolve("gitconfig").createFile() }

    @Test
    fun `the root of a linked worktree is recognised`() {
        val worktree = linkedWorktree(gitCheckout(dir.resolve("main")), dir.resolve("feature"))

        assertTrue(worktree.isInLinkedGitWorktree())
    }

    @Test
    fun `a project in a subdirectory of a linked worktree is recognised`() {
        val worktree = linkedWorktree(gitCheckout(dir.resolve("main")), dir.resolve("feature"))
        val module = worktree.resolve("modules/app").createDirectories()

        assertTrue(module.isInLinkedGitWorktree(), "git finds the repository from any directory below the worktree root")
    }

    @Test
    fun `a worktree linked with relative paths is recognised`() {
        val worktree = linkedWorktree(gitCheckout(dir.resolve("main")), dir.resolve("feature"), relativePaths = true)

        assertTrue(worktree.isInLinkedGitWorktree(), "git worktree add --relative-paths writes gitdir relative to the worktree")
    }

    @Test
    fun `the main checkout is not a worktree, even when it has linked worktrees`() {
        val main = gitCheckout(dir.resolve("main"))
        linkedWorktree(main, dir.resolve("feature"))

        assertFalse(main.isInLinkedGitWorktree(), "only the linked worktrees get the badge, not the repository they belong to")
    }

    @Test
    fun `a submodule is not a worktree`() {
        val module = submodule(gitCheckout(dir.resolve("main")), "library")

        assertFalse(module.isInLinkedGitWorktree(), "a submodule also has a .git file, but its git directory has no commondir")
    }

    @Test
    fun `a worktree whose administrative directory is gone is not recognised`() {
        val main = gitCheckout(dir.resolve("main"))
        val worktree = linkedWorktree(main, dir.resolve("feature"))
        main.resolve(".git/worktrees").toFile().deleteRecursively()

        assertFalse(worktree.isInLinkedGitWorktree(), "git itself no longer treats this directory as a repository")
    }

    @Test
    fun `a worktree added by git itself is recognised and its main checkout is not`() {
        val main = dir.resolve("main").createDirectories()
        val worktree = dir.resolve("feature")
        git(main, "init", "--quiet")
        git(main, "commit", "--quiet", "--allow-empty", "--no-verify", "-m", "initial")
        git(main, "worktree", "add", "--quiet", "-b", "feature", worktree.toString())

        assertTrue(worktree.isInLinkedGitWorktree(), "the hand-made layouts above must match what git really writes")
        assertFalse(main.isInLinkedGitWorktree())
    }

    private fun git(workDir: Path, vararg args: String) {
        val command = listOf("git", "-c", "user.name=Project Switcher", "-c", "user.email=test@example.com") + args
        val process = ProcessBuilder(command)
            .directory(workDir.toFile())
            .redirectErrorStream(true)
            .apply {
                environment().keys.removeIf { it.startsWith("GIT_") }
                environment()["GIT_CONFIG_GLOBAL"] = emptyGitConfig.toString()
                environment()["GIT_CONFIG_NOSYSTEM"] = "1"
            }
            .start()

        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed:\n$output" }
    }
}
