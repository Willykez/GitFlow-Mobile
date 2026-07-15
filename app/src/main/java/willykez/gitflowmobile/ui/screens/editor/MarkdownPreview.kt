package willykez.gitflowmobile.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import willykez.gitflowmobile.ui.theme.StatusClean

/**
 * Renders a practical subset of Markdown — headers, bold/italic, inline and
 * fenced code, blockquotes, bullet/numbered lists, links (shown styled but
 * not clickable), and horizontal rules. This is not a full CommonMark
 * implementation; it's the subset that covers the overwhelming majority of
 * real READMEs and notes, which is what this preview is for.
 */
@Composable
fun MarkdownPreview(source: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        val lines = source.split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> Spacer(Modifier.height(8.dp))

                line.startsWith("```") -> {
                    val fenceLang = line.removePrefix("```").trim()
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].startsWith("```")) {
                        codeLines.add(lines[i]); i++
                    }
                    CodeBlock(codeLines.joinToString("\n"), fenceLang)
                }

                line.startsWith("#### ") -> MdHeading(line.removePrefix("#### "), MaterialTheme.typography.titleSmall)
                line.startsWith("### ") -> MdHeading(line.removePrefix("### "), MaterialTheme.typography.titleMedium)
                line.startsWith("## ") -> MdHeading(line.removePrefix("## "), MaterialTheme.typography.titleLarge)
                line.startsWith("# ") -> MdHeading(line.removePrefix("# "), MaterialTheme.typography.headlineSmall)

                line.trim() == "---" || line.trim() == "***" -> {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                }

                line.trimStart().startsWith("> ") -> {
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Box4(color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(8.dp))
                        Text(inlineMarkdown(line.trimStart().removePrefix("> ")), color = StatusClean)
                    }
                }

                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    Row(Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                        Text("•  ", fontWeight = FontWeight.Bold)
                        Text(inlineMarkdown(line.trimStart().removePrefix("- ").removePrefix("* ")))
                    }
                }

                Regex("""^\d+\.\s""").containsMatchIn(line.trimStart()) -> {
                    Row(Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                        Text(inlineMarkdown(line.trimStart()))
                    }
                }

                else -> Text(
                    inlineMarkdown(line),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            i++
        }
    }
}

@Composable
private fun MdHeading(text: String, style: androidx.compose.ui.text.TextStyle) {
    Text(inlineMarkdown(text), style = style, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun Box4(color: Color) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(width = 3.dp, height = 20.dp).background(color, RoundedCornerShape(2.dp))
    )
}

@Composable
private fun CodeBlock(code: String, language: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        if (language.isNotBlank()) {
            Text(language, style = MaterialTheme.typography.labelSmall, color = StatusClean, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(
            code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
    }
}

/** Inline emphasis: bold, italic, and inline code. Applied within a single line/paragraph. */
private fun inlineMarkdown(raw: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val pattern = Regex("""\*\*(.+?)\*\*|`([^`]+?)`|\*(.+?)\*|_(.+?)_|\[([^]]+)]\(([^)]+)\)""")
    var last = 0
    for (m in pattern.findAll(raw)) {
        if (m.range.first > last) builder.append(raw.substring(last, m.range.first))
        when {
            m.groupValues[1].isNotEmpty() -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
            m.groupValues[2].isNotEmpty() -> builder.withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22808080))
            ) { append(m.groupValues[2]) }
            m.groupValues[3].isNotEmpty() -> builder.withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(m.groupValues[3]) }
            m.groupValues[4].isNotEmpty() -> builder.withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(m.groupValues[4]) }
            m.groupValues[5].isNotEmpty() -> builder.withStyle(
                SpanStyle(color = Color(0xFF4FB6FF), textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
            ) { append(m.groupValues[5]) }
        }
        last = m.range.last + 1
    }
    // No matches (or trailing text after the last match) — append what's left as plain text.
    if (last < raw.length) builder.append(raw.substring(last))
    return builder.toAnnotatedString()
}
