package app.tinyui.components

import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A `BottomSheet`'s colours are the host's where set, and M3's where not (docs/components.md §6). */
@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class BottomSheetDefaultsTest {
    private val scheme = lightColorScheme(surface = Color.White, onSurface = Color.Black, surfaceContainerLow = Color.Gray)

    @Test
    fun aContainerFromTheSchemeBringsItsOwnContentColour() = runComposeUiTest {
        var got: ResolvedBottomSheetStyle? = null
        setContent {
            MaterialTheme(colorScheme = scheme) {
                val defaults = TinyUIDefaults(bottomSheet = BottomSheetStyle(containerColor = MaterialTheme.colorScheme.surface))
                CompositionLocalProvider(LocalTinyUIDefaults provides defaults) { got = bottomSheetStyle() }
            }
        }
        waitForIdle()
        assertEquals(Color.White, got!!.containerColor)
        assertEquals(Color.Black, got!!.contentColor)
    }

    @Test
    fun theContentColourIsTheHostsWhenSet() = runComposeUiTest {
        var got: ResolvedBottomSheetStyle? = null
        setContent {
            MaterialTheme(colorScheme = scheme) {
                val defaults = TinyUIDefaults(bottomSheet = BottomSheetStyle(containerColor = Color.Red, contentColor = Color.Blue))
                CompositionLocalProvider(LocalTinyUIDefaults provides defaults) { got = bottomSheetStyle() }
            }
        }
        waitForIdle()
        assertEquals(Color.Red, got!!.containerColor)
        assertEquals(Color.Blue, got!!.contentColor)
    }

    @Test
    fun m3DefaultsWhenTheHostSetsNone() = runComposeUiTest {
        var m3: Color? = null
        var got: ResolvedBottomSheetStyle? = null
        setContent {
            MaterialTheme(colorScheme = scheme) {
                m3 = BottomSheetDefaults.ContainerColor
                got = bottomSheetStyle()
            }
        }
        waitForIdle()
        assertEquals(m3, got!!.containerColor)
        assertEquals(Color.Gray, got!!.containerColor)
    }
}
