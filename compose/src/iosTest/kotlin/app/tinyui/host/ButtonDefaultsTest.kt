package app.tinyui.host

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A `Button` variant takes the host's [ButtonStyle] piece by piece, M3's for every piece the host left null (docs/components.md §6). */
@OptIn(ExperimentalTestApi::class)
class ButtonDefaultsTest {
    @Test
    fun hostPiecesWinAndTheRestIsM3() = runComposeUiTest {
        val shape = RoundedCornerShape(8.dp)
        val border = BorderStroke(1.dp, Color.Red)
        var got: ResolvedButtonStyle? = null
        var m3Colors: ButtonColors? = null
        setContent {
            MaterialTheme {
                m3Colors = ButtonDefaults.outlinedButtonColors()
                CompositionLocalProvider(LocalTinyUIDefaults provides TinyUIDefaults(outlinedButton = ButtonStyle(border = border, shape = shape, contentPadding = PaddingValues(horizontal = 16.dp)))) {
                    got = buttonStyle("outlined", enabled = true)
                }
            }
        }
        waitForIdle()
        assertEquals(shape, got!!.shape)
        assertEquals(border, got!!.border)
        assertEquals(PaddingValues(horizontal = 16.dp), got!!.contentPadding)
        assertEquals(m3Colors, got!!.colors)
    }

    @Test
    fun disabledFallsBackToM3sDisabledBorderNotTheHosts() = runComposeUiTest {
        var got: ResolvedButtonStyle? = null
        var m3Disabled: BorderStroke? = null
        setContent {
            MaterialTheme {
                m3Disabled = ButtonDefaults.outlinedButtonBorder(enabled = false)
                CompositionLocalProvider(LocalTinyUIDefaults provides TinyUIDefaults(outlinedButton = ButtonStyle(border = BorderStroke(1.dp, Color.Red)))) {
                    got = buttonStyle("outlined", enabled = false)
                }
            }
        }
        waitForIdle()
        assertEquals(m3Disabled, got!!.border)
    }

    @Test
    fun m3WhenTheHostSetsNone() = runComposeUiTest {
        var filled: ResolvedButtonStyle? = null
        var text: ResolvedButtonStyle? = null
        var filledShape: Shape? = null
        var textShape: Shape? = null
        setContent {
            MaterialTheme {
                filledShape = ButtonDefaults.shape
                textShape = ButtonDefaults.textShape
                filled = buttonStyle(null, enabled = true)
                text = buttonStyle("text", enabled = true)
            }
        }
        waitForIdle()
        assertEquals(filledShape, filled!!.shape)
        assertNull(filled!!.border)
        assertEquals(ButtonDefaults.ContentPadding, filled!!.contentPadding)
        assertEquals(textShape, text!!.shape)
        assertEquals(ButtonDefaults.TextButtonContentPadding, text!!.contentPadding)
    }
}
