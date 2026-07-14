package willykez.gitflowmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Dark is GitFlow Mobile's real identity — a cockpit, not a document. Light
// mode is supported for accessibility/system-preference but every design
// decision (glass cards, gold rationing, monospace metadata) is made for
// dark first.
private val DarkScheme = darkColorScheme(
    primary = CommandBlue,
    onPrimary = Color(0xFF00131F),
    primaryContainer = CommandBlueDeep,
    onPrimaryContainer = Color.White,
    secondary = SignalGold,
    onSecondary = Color(0xFF241A00),
    secondaryContainer = SignalGoldDeep,
    onSecondaryContainer = Color.White,
    background = Void,
    onBackground = Color(0xFFE7EAF0),
    surface = Hull,
    onSurface = Color(0xFFE7EAF0),
    surfaceVariant = Deck,
    onSurfaceVariant = StatusMuted,
    outline = HullBorder,
    error = StatusDeleted,
    onError = Color.White,
)

private val LightScheme = lightColorScheme(
    primary = CommandBlueDeep,
    onPrimary = Color.White,
    primaryContainer = CommandBlueDim,
    onPrimaryContainer = CommandBlueDeep,
    secondary = SignalGoldDeep,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Graphite,
    surface = Paper,
    onSurface = Graphite,
    surfaceVariant = PaperDim,
    onSurfaceVariant = StatusMuted,
    outline = Color(0x1F11151C), // ~12% graphite — light-mode counterpart to HullBorder
    error = StatusDeleted,
    onError = Color.White,
)

val GitFlowMobileShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun GitFlowMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = GitFlowMobileTypography,
        shapes = GitFlowMobileShapes,
        content = content
    )
}
