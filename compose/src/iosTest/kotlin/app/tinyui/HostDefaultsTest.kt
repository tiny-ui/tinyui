package app.tinyui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The host's [TinyUIDefaults] win over M3's component defaults, and M3's stand when the host sets none (docs/components.md §6). */
@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class HostDefaultsTest {
    @Test
    fun textInputColoursComeFromTheHost() = runComposeUiTest {
        var custom: TextFieldColors? = null
        var got: ResolvedTextFieldStyle? = null
        setContent {
            MaterialTheme {
                custom = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Red)
                CompositionLocalProvider(LocalTinyUIDefaults provides TinyUIDefaults(textField = TextFieldStyle(colors = custom))) { got = textFieldStyle() }
            }
        }
        waitForIdle()
        assertEquals(custom, got!!.colors)
        assertEquals(Color.Red, got!!.colors.focusedIndicatorColor)
        // the pieces the host left out are M3's
        assertEquals(OutlinedTextFieldDefaults.contentPaddingWithoutLabel(), got!!.contentPadding)
        assertEquals(OutlinedTextFieldDefaults.MinHeight, got!!.minHeight)
    }

    @Test
    fun textInputDensityComesFromTheHost() = runComposeUiTest {
        var m3: TextFieldColors? = null
        var got: ResolvedTextFieldStyle? = null
        val padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        setContent {
            MaterialTheme {
                m3 = OutlinedTextFieldDefaults.colors()
                CompositionLocalProvider(LocalTinyUIDefaults provides TinyUIDefaults(textField = TextFieldStyle(contentPadding = padding, minHeight = 48.dp))) { got = textFieldStyle() }
            }
        }
        waitForIdle()
        assertEquals(padding, got!!.contentPadding)
        assertEquals(48.dp, got!!.minHeight)
        assertEquals(m3, got!!.colors)
    }

    @Test
    fun m3DefaultsWhenTheHostSetsNone() = runComposeUiTest {
        var m3: TextFieldColors? = null
        var got: ResolvedTextFieldStyle? = null
        setContent {
            MaterialTheme {
                m3 = OutlinedTextFieldDefaults.colors()
                got = textFieldStyle()
            }
        }
        waitForIdle()
        assertEquals(m3, got!!.colors)
        assertEquals(OutlinedTextFieldDefaults.contentPaddingWithoutLabel(), got!!.contentPadding)
        assertEquals(OutlinedTextFieldDefaults.MinHeight, got!!.minHeight)
    }
}
