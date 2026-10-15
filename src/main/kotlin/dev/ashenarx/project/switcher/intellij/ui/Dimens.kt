package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.ui.unit.dp
import java.awt.Dimension
import kotlin.math.roundToInt

internal object Dimens {
    val IconSize = 20.dp
    val IconGap = 6.dp
    val MetadataGap = 8.dp
    val BadgeGap = 4.dp

    val MaxBranchWidth = 160.dp

    val PopupPaddingHorizontal = 8.dp
    val PopupPaddingVertical = 10.dp

    val SectionSpacing = 8.dp
}

private const val POPUP_WIDTH_RATIO = 1.0 / 4.0
private const val POPUP_HEIGHT_RATIO = 3.0 / 5.0
private const val POPUP_MIN_WIDTH = 380
private const val POPUP_MIN_HEIGHT = 420
private const val POPUP_MAX_WIDTH = 760
private const val POPUP_MAX_HEIGHT = 760
private const val POPUP_EDGE_MARGIN = 32

internal val DEFAULT_POPUP_SIZE: Dimension
    get() = Dimension(POPUP_MIN_WIDTH, POPUP_MIN_HEIGHT)

internal fun popupSizeFor(windowSize: Dimension): Dimension = Dimension(
    boundedSize(windowSize.width, POPUP_WIDTH_RATIO, POPUP_MIN_WIDTH, POPUP_MAX_WIDTH),
    boundedSize(windowSize.height, POPUP_HEIGHT_RATIO, POPUP_MIN_HEIGHT, POPUP_MAX_HEIGHT),
)

private fun boundedSize(available: Int, ratio: Double, minimum: Int, maximum: Int): Int {
    val insetAvailable = (available - POPUP_EDGE_MARGIN * 2).coerceAtLeast(1)
    return (available * ratio).roundToInt()
        .coerceIn(minimum, maximum)
        .coerceAtMost(insetAvailable)
}
