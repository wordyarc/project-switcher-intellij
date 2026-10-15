package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import dev.ashenarx.project.switcher.intellij.model.ProjectItem

internal fun open(name: String, isCurrent: Boolean = false) = ProjectItem.Open(
    locationHash = "hash-$name",
    displayName = name,
    path = "/projects/$name",
    location = "~/projects/$name",
    branch = null,
    isWorktree = false,
    isCurrent = isCurrent,
)

internal fun recent(name: String, location: String = "~/projects/$name") = ProjectItem.Recent(
    displayName = name,
    path = "/projects/$name",
    location = location,
    branch = null,
    isWorktree = false,
)

internal object StubBitmap : ImageBitmap {
    override val width: Int = 1
    override val height: Int = 1
    override val colorSpace: ColorSpace = ColorSpaces.Srgb
    override val hasAlpha: Boolean = true
    override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) = Unit

    override fun prepareToDraw() = Unit
}
