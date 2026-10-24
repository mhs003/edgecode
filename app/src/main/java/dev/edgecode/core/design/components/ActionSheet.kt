package dev.edgecode.core.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space

/** One verb in an [ActionSheet]. */
data class SheetAction(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * A short list of verbs in a bottom sheet.
 *
 * The three-dot controls used to open a dropdown anchored to the control, which
 * put the options in a small floating card at the edge of the screen. A sheet
 * keeps the choices at thumb height and gives the header room to say what the
 * actions apply to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
    title: String,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.background,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.padding(bottom = Space.xl)) {
            SheetHeader(title = title, subtitle = subtitle, onClose = onDismiss)
            actions.forEach { action ->
                ActionRow(action = action, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun ActionRow(action: SheetAction, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val tint = if (action.destructive) scheme.error else edge.faint

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable {
                onDismiss()
                action.onClick()
            }
            .padding(horizontal = Space.gutter, vertical = Space.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (action.icon != null) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(Size.icon),
            )
            Spacer(Modifier.width(Space.md))
        }
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (action.destructive) scheme.error else scheme.onSurface,
        )
    }
}
