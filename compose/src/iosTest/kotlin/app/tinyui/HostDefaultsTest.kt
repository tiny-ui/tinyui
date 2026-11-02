package app.tinyui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The host's [TinyUIDefaults] win over M3's component defaults, and M3's stand when the host sets none (docs/components.md §6). */
@OptIn(ExperimentalTestApi::class)
class HostDefaultsTest {
    @Test
    fun textInputColoursComeFromTheHost() = runComposeUiTest {
        var custom: TextFieldColors? = null
        var got: TextFieldColors? = null
        setContent {
            MaterialTheme {
                custom = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Red)
                CompositionLocalProvider(LocalTinyUIDefaults provides TinyUIDefaults(textFieldColors = custom)) { got = textFieldColors() }
            }
        }
        waitForIdle()
        assertEquals(custom, got)
        assertEquals(Color.Red, got!!.focusedIndicatorColor)
    }

    @Test
    fun m3DefaultsWhenTheHostSetsNone() = runComposeUiTest {
        var m3: TextFieldColors? = null
        var got: TextFieldColors? = null
        setContent {
            MaterialTheme {
                m3 = OutlinedTextFieldDefaults.colors()
                got = textFieldColors()
            }
        }
        waitForIdle()
        assertEquals(m3, got)
    }
}
