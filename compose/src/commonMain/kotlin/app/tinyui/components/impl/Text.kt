package app.tinyui.components.impl

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import app.tinyui.components.LocalTinyUIDefaults
import app.tinyui.components.NodeScope
import app.tinyui.components.Theme

@Composable
internal fun TextComponent(scope: NodeScope) {
    val maxLines = scope.get<Double>("maxLines")?.toInt()?.takeIf { it > 0 } ?: Int.MAX_VALUE
    val raw = scope.get<String>("text") ?: ""
    // the host's link look, else M3's: the primary colour, the text's own weight
    val links = LocalTinyUIDefaults.current.textLinks ?: TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
    // whether the text is read for marks is the page's one declaration, never a side effect of which events it bound
    val markup = scope.get<Boolean>("markup") == true
    val text = remember(raw, links, markup) {
        shownText(raw, markup, links) { id -> if (scope.has("onLinkClick")) scope.dispatch("onLinkClick", payload("id", id)) }
    }
    Text(
        text = text,
        style = scope.get<String>("style")?.let { Theme.textStyle(MaterialTheme.typography, it) } ?: LocalTextStyle.current,
        color = scope.color("color") ?: Color.Unspecified,
        fontSize = scope["fontSize"] ?: TextUnit.Unspecified,
        fontWeight = when (scope.get<String>("fontWeight")) { "normal" -> FontWeight.Normal; "medium" -> FontWeight.Medium; "bold" -> FontWeight.Bold; else -> null },
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        textAlign = when (scope.get<String>("align")) { "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> TextAlign.Start },
        modifier = scope.modifier(),
    )
}

/**
 * What a `Text` shows for [text]: every character as given, or, with [markup], its marks read ([markedText]). Off is the
 * default because most text is data, not copy — a masked address is full of `*`, and none of it is a mark
 * (docs/components.md §3).
 */
internal fun shownText(text: String, markup: Boolean, linkStyles: TextLinkStyles, onLink: (String) -> Unit): AnnotatedString =
    if (markup) markedText(text, linkStyles, onLink) else AnnotatedString(text)

private val MARK = Regex("""\*\*(.+?)\*\*|\[([^\]]+)]\(([^)\s]+)\)""")

/**
 * The `**bold**` and `[label](id)` spans of [text] — the Markdown subset SwiftUI's `Text` reads, flat, no nesting; stray
 * marks stay literal. Bold is a weight on the span. A link takes [linkStyles] and reports its id through [onLink]
 * (docs/components.md §3).
 */
internal fun markedText(text: String, linkStyles: TextLinkStyles, onLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (m in MARK.findAll(text)) {
        val bold = m.groups[1]?.value
        append(text.substring(at, m.range.first))
        if (bold != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
        } else {
            val id = m.groupValues[3]
            pushLink(LinkAnnotation.Clickable(tag = id, styles = linkStyles) { onLink(id) })
            append(m.groupValues[2])
            pop()
        }
        at = m.range.last + 1
    }
    append(text.substring(at))
}
