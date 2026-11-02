package app.tinyui.components

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The prefix block follows the field's corners: rounded stays rounded with the radius less the inset, cut stays cut. */
class ConcentricInsetShapeTest {
    private val density = Density(1f)
    private val size = Size(80f, 48f)

    @Test
    fun roundedCornersShrinkByTheInset() {
        val outline = ConcentricInsetShape(RoundedCornerShape(8.dp), 4.dp).createOutline(size, LayoutDirection.Ltr, density)
        val rounded = assertIs<Outline.Rounded>(outline)
        assertEquals(4f, rounded.roundRect.topLeftCornerRadius.x)
    }

    @Test
    fun cutCornersStayCut() {
        val outline = ConcentricInsetShape(CutCornerShape(8.dp), 4.dp).createOutline(size, LayoutDirection.Ltr, density)
        // a cut corner is a path; a rounded one would come back as Outline.Rounded
        assertIs<Outline.Generic>(outline)
    }
}
