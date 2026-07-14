package willykez.gitflowmobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The signature GitFlow Mobile card: a translucent "pane of glass" — a
 * slightly-lighter-than-background fill with a thin hairline border instead
 * of a drawn shadow. Used everywhere a repo, credential, commit, or branch
 * is represented as a row/card. Colors come from MaterialTheme so this
 * reads correctly in both dark ("cockpit") and light mode rather than
 * always rendering the dark surface.
 *
 * [accent], when set, draws a thin vertical bar down the left edge —
 * used to signal branch/status color at a glance without extra text.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        if (accent != null) {
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxHeight().width(3.dp).background(accent))
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            content()
        }
    }
}
