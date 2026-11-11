package com.mochyy.mochymusic.ui.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/*
 * Inline Markdown for changelog copy: bold, italic, strike, code, links. Headings and lists are
 * the screen's job (each changelog item is already one bullet). Kept quiet on purpose: no extra
 * colours beyond the theme's primary for links and a slightly muted code span.
 */

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val annotated = remember(text, linkColor, codeColor) {
        parseInlineMarkdown(text.trim(), linkColor, codeColor)
    }
    Text(text = annotated, modifier = modifier, style = style, color = color)
}

internal fun parseInlineMarkdown(
    source: String,
    linkColor: Color,
    codeColor: Color,
): AnnotatedString {
    val stripped = source.removePrefix("#".repeat(headingLevel(source))).trimStart()
    val linkStyle = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    return buildAnnotatedString {
        var i = 0
        fun appendLink(label: String, url: String) {
            val start = length
            append(label)
            addStyle(linkStyle, start, length)
            addLink(LinkAnnotation.Url(url), start, length)
        }
        while (i < stripped.length) {
            when {
                stripped.startsWith("**", i) || stripped.startsWith("__", i) -> {
                    val mark = stripped.substring(i, i + 2)
                    val end = stripped.indexOf(mark, i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(stripped.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(stripped[i]); i++
                    }
                }
                stripped.startsWith("~~", i) -> {
                    val end = stripped.indexOf("~~", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(stripped.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(stripped[i]); i++
                    }
                }
                stripped.startsWith("`", i) -> {
                    val end = stripped.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = codeColor,
                            ),
                        ) {
                            append(stripped.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(stripped[i]); i++
                    }
                }
                stripped.startsWith("[", i) -> {
                    val close = stripped.indexOf(']', i + 1)
                    val destOpen = if (close >= 0 && close + 1 < stripped.length && stripped[close + 1] == '(') {
                        stripped.indexOf(')', close + 2)
                    } else -1
                    if (close > i && destOpen > close) {
                        appendLink(stripped.substring(i + 1, close), stripped.substring(close + 2, destOpen))
                        i = destOpen + 1
                    } else {
                        append(stripped[i]); i++
                    }
                }
                stripped.startsWith("http://", i) || stripped.startsWith("https://", i) -> {
                    var end = i
                    while (end < stripped.length && !stripped[end].isWhitespace() && stripped[end] !in "<>)\"'") end++
                    while (end > i && stripped[end - 1] in ".,;:!?") end--
                    appendLink(stripped.substring(i, end), stripped.substring(i, end))
                    i = end
                }
                stripped[i] == '*' || stripped[i] == '_' -> {
                    val mark = stripped[i]
                    val end = stripped.indexOf(mark, i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(stripped.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(stripped[i]); i++
                    }
                }
                else -> {
                    append(stripped[i]); i++
                }
            }
        }
    }
}

private fun headingLevel(text: String): Int {
    var n = 0
    while (n < text.length && n < 3 && text[n] == '#') n++
    return if (n > 0 && n < text.length && text[n] == ' ') n else 0
}
