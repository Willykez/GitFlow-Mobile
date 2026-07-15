package willykez.gitflowmobile.ui.screens.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import willykez.gitflowmobile.ui.theme.StatusClean
import willykez.gitflowmobile.ui.theme.currentSyntaxColors

/** Font size / line height shared by the gutter, the text field, and the caller's
 *  scroll-to-line math (all three must agree, or line numbers / jump-to-line drift
 *  out of alignment with the actual rendered lines). */
val EditorFontSize = 13.sp
val EditorLineHeight = 19.sp

/**
 * A code editor: a fixed line-number gutter beside a syntax-highlighted,
 * non-wrapping text field. Both live in one shared vertical [ScrollState]
 * so the gutter and the text always scroll together; the text field alone
 * also scrolls horizontally for long lines (the gutter stays pinned).
 *
 * Soft-wrap is deliberately off — with wrapping on, one logical line can
 * span several visual rows, and the gutter (one number per logical line)
 * would drift out of sync with what's actually on screen.
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

    Row(modifier.fillMaxSize().verticalScroll(verticalScrollState)) {
        // Gutter — one Text per logical line, same line height as the field so rows line up.
        Column(
            Modifier
                .fillMaxHeight()
                .widthIn(min = (gutterDigits * 9 + 20).dp)
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

        Box(Modifier.weight(1f).fillMaxHeight()) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
                    .padding(12.dp),
                textStyle = codeTextStyle,
                visualTransformation = SyntaxHighlightTransformation(language, syntaxColors),
                // No `singleLine`/maxLines cap (this is a multi-line editor), and no
                // explicit "no wrap" flag needed: `horizontalScroll` above gives this
                // field unbounded width, so each logical line measures at its full
                // width instead of wrapping — which is what keeps it exactly one
                // visual row per gutter line number.
            )
        }
    }
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
