// renders a github release body (markdown and html img tags), anything it doesn't recognise shows as plain text, never dropped

package com.mochyy.mochymusic.ui.screens.update

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Image(val url: String, val alt: String) : MdBlock
    data object Rule : MdBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock
}

private val MdImage = Regex("""!\[([^\]]*)]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)""")
private val HtmlImage = Regex("""<img\b[^>]*?\bsrc\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
private val HtmlAlt = Regex("""\balt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
private val HtmlTag = Regex("""</?[a-zA-Z][^>]*>""")
private val HeadingLine = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
private val BulletLine = Regex("""^(\s*)[-*+]\s+(?:\[[ xX]]\s+)?(.*)$""")
private val NumberLine = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
private val RuleLine = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
private val Fence = Regex("""^\s*(```|~~~)""")
private val TableRowLine = Regex("""^\s*\|.+\|\s*$""")
private val TableSepCell = Regex("""^\s*:?-{3,}:?\s*$""")

private fun isTableRow(line: String): Boolean = TableRowLine.matches(line)

private fun isTableSeparator(line: String): Boolean {
    if (!isTableRow(line)) return false
    return splitTableCells(line).all { TableSepCell.matches(it) }
}

private fun splitTableCells(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith("|")) s = s.drop(1)
    if (s.endsWith("|")) s = s.dropLast(1)
    return s.split("|").map { it.trim() }
}

/**
 * Release notes may ship two languages in one GitHub body:
 * `<!--lang:id-->` … `<!--lang:en-->` …
 * Indonesian locales (`in` / `id`) pick the id block; everyone else picks en, then the first block.
 */
internal fun pickReleaseNotes(body: String, language: String): String {
    val matches = Regex("""<!--\s*lang:([a-zA-Z-]+)\s*-->""").findAll(body).toList()
    if (matches.isEmpty()) return body
    fun norm(tag: String) = tag.lowercase().substringBefore('-').let { if (it == "in") "id" else it }
    val wanted = norm(language)
    val parts = matches.mapIndexed { i, match ->
        val lang = norm(match.groupValues[1])
        val start = match.range.last + 1
        val end = matches.getOrNull(i + 1)?.range?.first ?: body.length
        lang to body.substring(start, end).trim()
    }.filter { it.second.isNotEmpty() }
    return parts.firstOrNull { it.first == wanted }?.second
        ?: parts.firstOrNull { it.first == "en" }?.second
        ?: body
}

/** pulls image references out of line, returns the text left over plus the images in order */
private fun extractImages(line: String): Pair<String, List<MdBlock.Image>> {
    val images = mutableListOf<Pair<Int, MdBlock.Image>>()
    MdImage.findAll(line).forEach { images += it.range.first to MdBlock.Image(it.groupValues[2], it.groupValues[1]) }
    HtmlImage.findAll(line).forEach { match ->
        val alt = HtmlAlt.find(match.value)?.groupValues?.get(1).orEmpty()
        images += match.range.first to MdBlock.Image(match.groupValues[1], alt)
    }
    if (images.isEmpty()) return line to emptyList()
    val rest = line.replace(MdImage, " ").replace(HtmlImage, " ")
    return rest to images.sortedBy { it.first }.map { it.second }
}

private fun parseBlocks(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotEmpty()) blocks += MdBlock.Paragraph(text)
        paragraph.clear()
    }

    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
    var i = 0
    while (i < lines.size) {
        val raw = lines[i]

        if (Fence.containsMatchIn(raw)) {
            flushParagraph()
            val code = StringBuilder()
            i++
            while (i < lines.size && !Fence.containsMatchIn(lines[i])) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[i])
                i++
            }
            blocks += MdBlock.Code(code.toString())
            i++
            continue
        }

        // html comments (release templates leave them in) and <br> tags carry no text
        val (withoutImages, images) = extractImages(raw.replace(Regex("<!--.*?-->"), ""))
        val line = withoutImages.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), " ")
            .replace(HtmlTag, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

        when {
            line.isBlank() -> flushParagraph()
            RuleLine.matches(line) -> {
                flushParagraph()
                blocks += MdBlock.Rule
            }
            HeadingLine.matches(line) -> {
                flushParagraph()
                val match = HeadingLine.find(line)!!
                blocks += MdBlock.Heading(match.groupValues[1].length, match.groupValues[2])
            }
            BulletLine.matches(line) -> {
                flushParagraph()
                val match = BulletLine.find(line)!!
                blocks += MdBlock.ListItem("•", match.groupValues[2].trim(), match.groupValues[1].length / 2)
            }
            NumberLine.matches(line) -> {
                flushParagraph()
                val match = NumberLine.find(line)!!
                blocks += MdBlock.ListItem("${match.groupValues[2]}.", match.groupValues[3].trim(), match.groupValues[1].length / 2)
            }
            line.trimStart().startsWith(">") -> {
                flushParagraph()
                blocks += MdBlock.Quote(line.trimStart().removePrefix(">").trim())
            }
            isTableRow(line) -> {
                flushParagraph()
                val collected = mutableListOf<List<String>>()
                var j = i
                while (j < lines.size && isTableRow(lines[j].replace(Regex("<!--.*?-->"), "").replace(HtmlTag, ""))) {
                    val rowLine = lines[j].replace(Regex("<!--.*?-->"), "").replace(HtmlTag, "")
                    if (!isTableSeparator(rowLine)) collected += splitTableCells(rowLine)
                    j++
                }
                if (collected.isNotEmpty()) {
                    blocks += MdBlock.Table(collected.first(), collected.drop(1))
                }
                i = j
                continue
            }
            else -> {
                // a line under a list item that's just indented continues that item
                val last = blocks.lastOrNull()
                if (paragraph.isEmpty() && last is MdBlock.ListItem && raw.startsWith("  ")) {
                    blocks[blocks.lastIndex] = last.copy(text = last.text + " " + line.trim())
                } else {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                }
            }
        }

        if (images.isNotEmpty()) {
            flushParagraph()
            blocks += images
        }
        i++
    }
    flushParagraph()
    return blocks
}

private val InlinePattern = Regex(
    """(\*\*|__)(.+?)\1""" + // 1-2: bold
        """|~~(.+?)~~""" + // 3: strike
        """|`([^`]+)`""" + // 4: code
        """|\[([^\]]+)]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)""" + // 5-6: link
        """|(?<![\w*])\*(?![\s*])(.+?)(?<![\s*])\*(?![\w*])""" + // 7: *italic*
        """|(?<![\w_])_(?![\s_])(.+?)(?<![\s_])_(?![\w_])""" + // 8: _italic_
        """|(https?://[^\s<>()]+[^\s<>().,;:!?'"])""", // 9: bare link
)

private fun AnnotatedString.Builder.appendInline(text: String, strong: SpanStyle, link: SpanStyle) {
    var cursor = 0
    while (cursor < text.length) {
        val match = InlinePattern.find(text, cursor)
        if (match == null) {
            append(text.substring(cursor))
            return
        }
        append(text.substring(cursor, match.range.first))
        val g = match.groupValues
        when {
            g[2].isNotEmpty() -> withStyle(strong) { appendInline(g[2], strong, link) }
            g[3].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendInline(g[3], strong, link)
            }
            g[4].isNotEmpty() -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = Color.White.copy(alpha = 0.08f))
            ) { append(g[4]) }
            g[5].isNotEmpty() -> withLink(LinkAnnotation.Url(g[6], TextLinkStyles(style = link))) {
                appendInline(g[5], strong, link)
            }
            g[7].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(g[7], strong, link) }
            g[8].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(g[8], strong, link) }
            g[9].isNotEmpty() -> withLink(LinkAnnotation.Url(g[9], TextLinkStyles(style = link))) { append(g[9]) }
        }
        cursor = match.range.last + 1
    }
}

private fun inline(text: String, strongColor: Color): AnnotatedString = buildAnnotatedString {
    appendInline(
        text = text,
        strong = SpanStyle(fontWeight = FontWeight.Bold, color = strongColor),
        link = SpanStyle(color = strongColor, textDecoration = TextDecoration.Underline),
    )
}

/** GitHub release body, already picked for the app language. */
@Composable
fun ReleaseNotesMarkdown(
    body: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF9A9A9A),
    strongColor: Color = Color.White,
    fontSize: TextUnit = 12.sp,
    lineHeight: TextUnit = 17.sp,
) {
    val language = LocalConfiguration.current.locales[0]?.language ?: "en"
    ChangelogMarkdown(
        markdown = pickReleaseNotes(body, language),
        modifier = modifier,
        color = color,
        strongColor = strongColor,
        fontSize = fontSize,
        lineHeight = lineHeight,
        emptyText = stringResource(R.string.no_changelog_available),
    )
}

/** a release body as styled text and images, bold text, headings and links use strongColor so they stand out from color */
@Composable
fun ChangelogMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF9A9A9A),
    strongColor: Color = Color.White,
    fontSize: TextUnit = 12.sp,
    lineHeight: TextUnit = 17.sp,
    emptyText: String? = null,
) {
    val empty = emptyText ?: stringResource(R.string.no_changelog_available)
    val blocks = remember(markdown) { parseBlocks(markdown.trim()) }

    if (blocks.isEmpty()) {
        Text(text = empty, color = color, fontSize = fontSize, lineHeight = lineHeight, modifier = modifier)
        return
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = remember(block) { inline(block.text, strongColor) },
                    color = strongColor,
                    fontSize = when (block.level) {
                        1 -> fontSize * 1.5f
                        2 -> fontSize * 1.32f
                        else -> fontSize * 1.16f
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )

                is MdBlock.Paragraph -> Text(
                    text = remember(block) { inline(block.text, strongColor) },
                    color = color,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                )

                is MdBlock.ListItem -> Row(modifier = Modifier.padding(start = (block.indent * 12).dp)) {
                    Text(
                        text = block.marker,
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        modifier = Modifier.width(if (block.marker == "•") 12.dp else 20.dp),
                    )
                    Text(
                        text = remember(block) { inline(block.text, strongColor) },
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                    )
                }

                is MdBlock.Quote -> Row {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .heightIn(min = 16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color.copy(alpha = 0.5f))
                    )
                    Text(
                        text = remember(block) { inline(block.text, strongColor) },
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }

                is MdBlock.Code -> Text(
                    text = block.text,
                    color = strongColor.copy(alpha = 0.85f),
                    fontSize = fontSize * 0.95f,
                    lineHeight = lineHeight,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(10.dp),
                )

                is MdBlock.Image -> AsyncImage(
                    model = block.url,
                    contentDescription = block.alt.ifBlank { null },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )

                MdBlock.Rule -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .heightIn(min = 1.dp, max = 1.dp)
                        .background(color.copy(alpha = 0.25f))
                )

                is MdBlock.Table -> ChangelogTable(
                    table = block,
                    color = color,
                    strongColor = strongColor,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                )
            }
        }
    }
}

@Composable
private fun ChangelogTable(
    table: MdBlock.Table,
    color: Color,
    strongColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
) {
    val columns = (listOf(table.headers) + table.rows).maxOf { it.size }.coerceAtLeast(1)
    fun cells(row: List<String>) = List(columns) { i -> row.getOrElse(i) { "" } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            cells(table.headers).forEach { cell ->
                Text(
                    text = inline(cell, strongColor),
                    color = strongColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = lineHeight,
                    modifier = Modifier.widthIn(min = 88.dp, max = 220.dp),
                )
            }
        }
        table.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                cells(row).forEach { cell ->
                    Text(
                        text = inline(cell, strongColor),
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        modifier = Modifier.widthIn(min = 88.dp, max = 220.dp),
                    )
                }
            }
        }
    }
}

/** first meaningful line of a release body as plain text, for one-line summaries and the update notification where markdown would show as stray symbols */
internal fun changelogTableHeaders(markdown: String): List<String> =
    parseBlocks(markdown).filterIsInstance<MdBlock.Table>().firstOrNull()?.headers.orEmpty()

fun changelogSummaryLine(markdown: String): String =
    parseBlocks(markdown).firstNotNullOfOrNull { block ->
        when (block) {
            is MdBlock.Heading -> block.text
            is MdBlock.Paragraph -> block.text
            is MdBlock.ListItem -> block.text
            is MdBlock.Quote -> block.text
            is MdBlock.Table -> block.headers.firstOrNull()
            else -> null
        }?.let { inline(it, Color.Unspecified).text.trim() }?.takeIf { it.isNotEmpty() }
    }.orEmpty()
