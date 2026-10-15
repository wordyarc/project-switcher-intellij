package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ashenarx.project.switcher.intellij.ProjectSwitcherBundle
import dev.ashenarx.project.switcher.intellij.model.Highlights
import dev.ashenarx.project.switcher.intellij.model.OpenTarget
import dev.ashenarx.project.switcher.intellij.model.ProjectItem
import org.jetbrains.jewel.bridge.retrieveColorOrNull
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.SearchMatchStyle
import org.jetbrains.jewel.ui.theme.searchMatchStyle
import org.jetbrains.jewel.ui.theme.simpleListItemStyle

private val INDICATOR_WIDTH = 4.dp
private val INDICATOR_STROKE = 2.dp
private val INDICATOR_VPAD = 7.dp
private val INDICATOR_INSET_START = 2.dp

private const val MISSING_ICON_ALPHA = 0.5f

private val GRAYSCALE = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
internal fun ProjectRow(
    item: ProjectItem,
    icon: ImageBitmap?,
    highlights: Highlights,
    isSelected: Boolean,
    isMissing: Boolean,
    showLocation: Boolean,
    onClick: (OpenTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = JewelTheme.simpleListItemStyle

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val pressModifiers = remember { mutableStateOf<PointerKeyboardModifiers?>(null) }

    val selectedBg = retrieveColorOrNull("List.selectionBackground")
        ?: JewelTheme.globalColors.outlines.focused
    val hoverBg = retrieveColorOrNull("List.hoverBackground") ?: selectedBg.copy(alpha = 0.4f)

    val background = when {
        isSelected -> selectedBg
        isHovered -> hoverBg
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(JewelTheme.globalMetrics.rowHeight)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) pressModifiers.value = event.keyboardModifiers
                    }
                }
            }
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) {
                val modifiers = pressModifiers.value
                onClick(openTargetFor(ctrl = modifiers?.isCtrlPressed == true, shift = modifiers?.isShiftPressed == true))
            }
    ) {
        CurrentProjectIndicator(
            visible = item.isCurrent,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = INDICATOR_INSET_START, top = INDICATOR_VPAD, bottom = INDICATOR_VPAD)
                .width(INDICATOR_WIDTH)
                .fillMaxHeight()
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(style.metrics.outerPadding)
                .background(background, RoundedCornerShape(style.metrics.selectionBackgroundCornerSize))
                .padding(style.metrics.innerPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val secondary = with(JewelTheme.globalColors.text) {
                if (isSelected) disabledSelected else disabled
            }

            ProjectIcon(icon, isMissing)
            Spacer(Modifier.width(Dimens.IconGap))

            NameAndLocation(
                item = item,
                highlights = highlights,
                showLocation = showLocation,
                primary = if (isMissing) secondary else Color.Unspecified,
                secondary = secondary,
                modifier = Modifier.weight(1f),
            )

            if (item.isWorktree || item.branch != null) {
                Spacer(Modifier.width(Dimens.MetadataGap))
                GitMetadata(item, color = secondary)
            }
        }
    }
}

@Composable
private fun GitMetadata(item: ProjectItem, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.BadgeGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.isWorktree) {
            Icon(key = SwitcherIcons.Worktree, contentDescription = ProjectSwitcherBundle.message("popup.worktree"))
        }

        item.branch?.let { branch ->
            Text(
                text = branch,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.widthIn(max = Dimens.MaxBranchWidth),
            )
        }
    }
}

@Composable
private fun NameAndLocation(
    item: ProjectItem,
    highlights: Highlights,
    showLocation: Boolean,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    val matchStyle = JewelTheme.searchMatchStyle

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = remember(item, highlights, matchStyle) { item.displayName.highlighted(highlights.name, matchStyle) },
            color = primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        if (showLocation && item.location.isNotEmpty()) {
            Spacer(Modifier.width(Dimens.MetadataGap))
            Text(
                text = remember(item, highlights, matchStyle) { item.location.highlighted(highlights.location, matchStyle) },
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

private fun String.highlighted(ranges: List<IntRange>, style: SearchMatchStyle): AnnotatedString {
    if (ranges.isEmpty()) return AnnotatedString(this)

    val span = SpanStyle(
        color = style.colors.foreground,
        background = style.colors.startBackground,
        fontWeight = FontWeight.Bold,
    )
    return buildAnnotatedString {
        append(this@highlighted)
        ranges.forEach { addStyle(span, it.first, it.last + 1) }
    }
}

// Not SwingPanel: it flickers in recycled rows. Not a Jewel icon key: it cannot hold a generated icon.
@Composable
private fun ProjectIcon(icon: ImageBitmap?, isMissing: Boolean) {
    if (icon == null) {
        Spacer(Modifier.size(Dimens.IconSize))
    } else {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(Dimens.IconSize),
            alpha = if (isMissing) MISSING_ICON_ALPHA else 1f,
            colorFilter = if (isMissing) GRAYSCALE else null,
        )
    }
}

@Composable
private fun CurrentProjectIndicator(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return

    val color = JewelTheme.globalColors.text.normal.copy(alpha = 0.85f)
    Canvas(modifier = modifier) {
        val x = size.width / 2f
        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = INDICATOR_STROKE.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
