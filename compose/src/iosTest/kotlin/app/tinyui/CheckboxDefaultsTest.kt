package app.tinyui

import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals

/** A `Checkbox` takes the host's [CheckboxStyle] piece by piece, M3's for every piece the host left null (docs/components.md §6). */
@OptIn(ExperimentalTestApi::class)
class CheckboxDefaultsTest {
    @Test
    fun hostPiecesWinAndTheRestIsM3() = runComposeUiTest {
        var outline: Stroke? = null
        var got: ResolvedCheckboxStyle? = null
        var m3Colors: CheckboxColors? = null
        var m3Width = 0f
        setContent {
            MaterialTheme {
                m3Colors = CheckboxDefaults.colors()
                m3Width = with(LocalDensity.current) { floor(CheckboxDefaults.StrokeWidth.toPx()) }
                outline = Stroke(with(LocalDensity.current) { 1.dp.toPx() })
                CompositionLocalProvider(LocalTinyUIDefaults provides TinyUIDefaults(checkbox = CheckboxStyle(outlineStroke = outline))) { got = checkboxStyle() }
            }
        }
        waitForIdle()
        assertEquals(outline, got!!.outlineStroke)
        // the checkmark the host left out is M3's: its stroke width, square caps
        assertEquals(Stroke(width = m3Width, cap = StrokeCap.Square), got!!.checkmarkStroke)
        assertEquals(m3Colors, got!!.colors)
    }

    @Test
    fun m3WhenTheHostSetsNone() = runComposeUiTest {
        var got: ResolvedCheckboxStyle? = null
        var m3Width = 0f
        setContent {
            MaterialTheme {
                m3Width = with(LocalDensity.current) { floor(CheckboxDefaults.StrokeWidth.toPx()) }
                got = checkboxStyle()
            }
        }
        waitForIdle()
        assertEquals(Stroke(width = m3Width), got!!.outlineStroke)
        assertEquals(Stroke(width = m3Width, cap = StrokeCap.Square), got!!.checkmarkStroke)
    }
}
