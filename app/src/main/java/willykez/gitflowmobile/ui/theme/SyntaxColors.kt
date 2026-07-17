package willykez.gitflowmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Color tokens for code syntax highlighting. Kept separate from the main
 * MaterialTheme color scheme because syntax highlighting needs more distinct
 * hues (keyword vs. string vs. comment vs. number...) than a UI palette
 * does, and those hues need their own light/dark pairs so highlighted code
 * stays readable in both themes rather than just inheriting UI accent colors.
 */
data class SyntaxColorSet(
    val keyword: Color,
    val type: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val annotation: Color,
    val punctuation: Color,
    val tag: Color,       // XML/HTML tag names
    val attribute: Color, // XML/HTML attribute names
    val plain: Color,
    val matchHighlight: Color,        // find-in-file: other matches
    val currentMatchHighlight: Color, // find-in-file: the match you're on
)

private val DarkSyntax = SyntaxColorSet(
    keyword = Color(0xFF6FB6FF),     // command blue family
    type = Color(0xFF7EE0C3),
    string = Color(0xFFF2B33D),      // gold family — matches the app's "signal" accent
    number = Color(0xFFD5A6FF),
    comment = Color(0xFF6B7688),
    annotation = Color(0xFFFF9D5C),
    punctuation = Color(0xFF8792A3),
    tag = Color(0xFF6FB6FF),
    attribute = Color(0xFF7EE0C3),
    plain = Color(0xFFE7EAF0),
    matchHighlight = Color(0x554FB6FF),        // translucent command blue
    currentMatchHighlight = Color(0xAAF2B33D), // solid-ish gold — the one you're on stands out
)

private val LightSyntax = SyntaxColorSet(
    keyword = Color(0xFF0B5FA8),
    type = Color(0xFF0E7A5F),
    string = Color(0xFF9A6300),
    number = Color(0xFF7A3FB8),
    comment = Color(0xFF7A8494),
    annotation = Color(0xFFB5590E),
    punctuation = Color(0xFF5B6472),
    tag = Color(0xFF0B5FA8),
    attribute = Color(0xFF0E7A5F),
    plain = Color(0xFF11151C),
    matchHighlight = Color(0x664FB6FF),
    currentMatchHighlight = Color(0xAAF2B33D),
)

@Composable
fun currentSyntaxColors(): SyntaxColorSet = if (isSystemInDarkTheme()) DarkSyntax else LightSyntax
