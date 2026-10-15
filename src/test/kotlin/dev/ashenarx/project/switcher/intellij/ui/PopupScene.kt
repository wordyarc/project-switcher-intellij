package dev.ashenarx.project.switcher.intellij.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.theme.JewelTheme
import java.awt.event.InputEvent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import java.awt.event.KeyEvent as AwtKeyEvent

internal class PopupScene(
    width: Int = DEFAULT_WIDTH,
    height: Int = DEFAULT_HEIGHT,
    content: @Composable () -> Unit,
) : AutoCloseable {

    private var themeLoaded = false

    private val scene = ImageComposeScene(width, height, Density(1f), Dispatchers.Unconfined) {
        SwingBridgeTheme {
            val loaded = JewelTheme.defaultTextStyle.fontFamily != null
            SideEffect { themeLoaded = loaded }
            if (loaded) content()
        }
    }

    private val source = JPanel()

    private var nanos = 0L

    init {
        timeoutRunBlocking(THEME_TIMEOUT) {
            while (!themeLoaded) {
                frames(count = 1)
                delay(THEME_POLL)
            }
        }
    }

    fun frames(count: Int = 2) {
        repeat(count) {
            nanos += FRAME_NANOS
            scene.render(nanos)
        }
    }

    fun press(
        key: Key,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
    ): Boolean {
        val modifiers = modifiersOf(ctrl = ctrl, shift = shift, alt = alt, meta = meta)
        val handled = send(AwtKeyEvent.KEY_PRESSED, key.nativeKeyCode, AwtKeyEvent.CHAR_UNDEFINED, modifiers)
        send(AwtKeyEvent.KEY_RELEASED, key.nativeKeyCode, AwtKeyEvent.CHAR_UNDEFINED, modifiers)
        return handled
    }

    fun type(text: String) {
        for (char in text) {
            val keyCode = AwtKeyEvent.getExtendedKeyCodeForChar(char.code)
            send(AwtKeyEvent.KEY_PRESSED, keyCode, char, modifiers = 0)
            send(AwtKeyEvent.KEY_TYPED, AwtKeyEvent.VK_UNDEFINED, char, modifiers = 0)
            send(AwtKeyEvent.KEY_RELEASED, keyCode, char, modifiers = 0)
        }
    }

    fun click(text: String, ctrl: Boolean = false, shift: Boolean = false) {
        val center = checkNotNull(boundsOf(text)) { "nothing on screen shows \"$text\"" }.center
        val modifiers = PointerKeyboardModifiers(isCtrlPressed = ctrl, isShiftPressed = shift)

        scene.sendPointerEvent(PointerEventType.Move, center, keyboardModifiers = modifiers)
        scene.sendPointerEvent(
            PointerEventType.Press,
            center,
            buttons = PointerButtons(isPrimaryPressed = true),
            keyboardModifiers = modifiers,
            button = PointerButton.Primary,
        )
        scene.sendPointerEvent(
            PointerEventType.Release,
            center,
            buttons = PointerButtons(),
            keyboardModifiers = modifiers,
            button = PointerButton.Primary,
        )
    }

    fun texts(): List<String> = scene.semanticsOwners.flatMap { it.unmergedRootSemanticsNode.texts() }

    fun boundsOf(text: String): Rect? =
        scene.semanticsOwners.firstNotNullOfOrNull { it.unmergedRootSemanticsNode.find(text) }?.boundsInRoot

    fun imagesDescribed(description: String): List<Rect> =
        scene.semanticsOwners.flatMap { it.unmergedRootSemanticsNode.described(description) }.map { it.boundsInRoot }

    override fun close() {
        scene.close()
    }

    private fun send(id: Int, keyCode: Int, keyChar: Char, modifiers: Int): Boolean {
        val awt = AwtKeyEvent(source, id, System.currentTimeMillis(), modifiers, keyCode, keyChar)
        val type = when (id) {
            AwtKeyEvent.KEY_PRESSED -> KeyEventType.KeyDown
            AwtKeyEvent.KEY_RELEASED -> KeyEventType.KeyUp
            else -> KeyEventType.Unknown
        }

        return scene.sendKeyEvent(
            KeyEvent(
                key = Key(keyCode),
                type = type,
                codePoint = if (keyChar == AwtKeyEvent.CHAR_UNDEFINED) 0 else keyChar.code,
                isCtrlPressed = awt.isControlDown,
                isMetaPressed = awt.isMetaDown,
                isAltPressed = awt.isAltDown,
                isShiftPressed = awt.isShiftDown,
                nativeEvent = awt,
            ),
        )
    }

    private fun modifiersOf(ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean): Int =
        (if (ctrl) InputEvent.CTRL_DOWN_MASK else 0) or
            (if (shift) InputEvent.SHIFT_DOWN_MASK else 0) or
            (if (alt) InputEvent.ALT_DOWN_MASK else 0) or
            (if (meta) InputEvent.META_DOWN_MASK else 0)

    private fun SemanticsNode.texts(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + children.flatMap { it.texts() }

    private fun SemanticsNode.find(text: String): SemanticsNode? {
        val texts = config.getOrNull(SemanticsProperties.Text).orEmpty()
        if (texts.any { it.text == text }) return this

        return children.firstNotNullOfOrNull { it.find(text) }
    }

    private fun SemanticsNode.described(description: String): List<SemanticsNode> {
        val descriptions = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        return listOfNotNull(takeIf { description in descriptions }) + children.flatMap { it.described(description) }
    }

    private companion object {
        const val DEFAULT_WIDTH = 420
        const val DEFAULT_HEIGHT = 420
        const val FRAME_NANOS = 16_000_000L
        val THEME_TIMEOUT = 10.seconds
        val THEME_POLL = 10.milliseconds
    }
}
