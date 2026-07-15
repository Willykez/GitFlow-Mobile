package willykez.gitflowmobile.ui.screens.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import willykez.gitflowmobile.ui.theme.CommandBlue
import willykez.gitflowmobile.ui.theme.StatusClean
import willykez.gitflowmobile.ui.theme.currentSyntaxColors

/** Font size / line height shared by the gutter, the text field, and the caller's
 *  scroll-to-line math (all three must agree, or line numbers / jump-to-line drift
 *  out of alignment with the actual rendered lines). */
val EditorFontSize = 13.sp
val EditorLineHeight = 19.sp

/** Quick-insert symbols shown above the keyboard — the punctuation mobile
 *  keyboards bury behind a symbols/shift layer, surfaced as one tap instead.
 *  "→" inserts a soft-tab (4 spaces) rather than a literal tab character,
 *  since tab-width rendering is inconsistent across fonts. */
private val QuickSymbols = listOf(
    "→" to "    ", "{" to "{", "}" to "}", "(" to "(", ")" to ")",
    "[" to "[", "]" to "]", ";" to ";", "=" to "=", "\"" to "\"",
    "'" to "'", "<" to "<", ">" to ">", "/" to "/", "\\" to "\\",
    "+" to "+", "-" to "-", "*" to "*", "_" to "_", "#" to "#",
    "@" to "@", "!" to "!", "&" to "&", "|" to "|", ":" to ":",
)

/**
 * A code editor: a fixed line-number gutter beside a syntax-highlighted,
 * non-wrapping text field, plus a quick-symbol toolbar above the keyboard.
 *
 * The gutter and the field share one vertical [ScrollState] so they always
 * scroll together. Neither uses `fillMaxHeight()` — under a scrollable
 * container children are given an *unbounded* height to measure against,
 * and asking a child to "fill" an unbounded height silently breaks its
 * layout instead of erroring. Both are left to size to their own natural
 * (content-driven) height instead, which is both correct and exactly what
 * you want here: the row's total height comes out to line-count × line-height.
 */
@Composable
fun CodeEditorField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    language: CodeLanguage,
    verticalScrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val syntaxColors = currentSyntaxColors()
    val lineCount = remember(value.text) { value.text.count { it == '\n' } + 1 }
    val gutterDigits = remember(lineCount) { lineCount.toString().length.coerceAtLeast(2) }
    val horizontalScrollState = rememberScrollState()

    val codeTextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = EditorFontSize,
        lineHeight = EditorLineHeight,
        color = MaterialTheme.colorScheme.onSurface,
    )

    Column(modifier) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(verticalScrollState)
        ) {
            // Gutter — one Text per logical line, same line height as the field so rows line up.
            // Fixed exact width, not just a minimum: every Text inside uses fillMaxWidth()
            // to right-align its number, which gives Compose no smaller intrinsic width
            // to prefer — with only `widthIn(min = ...)` (no max), the gutter would happily
            // expand to fill whatever width the row hands it, pushing the actual text field
            // off-screen. `.width(...)` pins it to exactly the size the digits need.
            Column(
                Modifier
                    .width((gutterDigits * 9 + 20).dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                for (n in 1..lineCount) {
                    Text(
                        text = n.toString(),
                        style = codeTextStyle.copy(color = StatusClean),
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScrollState)
                    .padding(12.dp),
                textStyle = codeTextStyle,
                visualTransformation = SyntaxHighlightTransformation(language, syntaxColors),
                // No `singleLine`/maxLines cap (this is a multi-line editor). No explicit
                // "no wrap" flag exists on this overload — `horizontalScroll` above gives
                // this field unbounded width instead, so each logical line measures at
                // its full width rather than wrapping, which is what keeps it exactly
                // one visual row per gutter line number.
            )
        }

        HorizontalDivider()
        QuickSymbolToolbar(
            onInsert = { symbol -> onValueChange(insertAtCursor(value, symbol)) },
        )
    }
}

@Composable
private fun QuickSymbolToolbar(onInsert: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState()),
    ) {
        QuickSymbols.forEach { (label, insertText) ->
            Box(
                Modifier
                    .fillMaxSize()
                    .width(40.dp)
                    .clickable { onInsert(insertText) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    color = if (label == "→") CommandBlue else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Replaces the current selection (or inserts at the cursor, if nothing is
 *  selected) with [insert], and places the cursor right after it. */
private fun insertAtCursor(value: TextFieldValue, insert: String): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val newText = value.text.replaceRange(start, end, insert)
    val newCursor = start + insert.length
    return TextFieldValue(newText, TextRange(newCursor, newCursor))
}

/** 1-based (line, column) for a character offset into [text] — used both to
 *  show "Ln X, Col Y" and to convert a typed "156:13" back into an offset. */
fun lineColForOffset(text: String, offset: Int): Pair<Int, Int> {
    val clamped = offset.coerceIn(0, text.length)
    var line = 1
    var lastNewline = -1
    for (i in 0 until clamped) {
        if (text[i] == '\n') {
            line++
            lastNewline = i
        }
    }
    val col = clamped - lastNewline
    return line to col
}

/** Inverse of [lineColForOffset] — 1-based line/column back to a character offset,
 *  clamped to the actual text bounds so an out-of-range "go to line" can't crash. */
fun offsetForLineCol(text: String, line: Int, column: Int): Int {
    if (line <= 1) return (column - 1).coerceIn(0, text.length)
    var currentLine = 1
    var i = 0
    while (i < text.length && currentLine < line) {
        if (text[i] == '\n') currentLine++
        i++
    }
    return (i + (column - 1)).coerceIn(0, text.length)
}
