package dev.edgecode.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.Stroke

/**
 * A compact confirmation surface in the app's own geometry.
 *
 * Material's alert dialog brings its own surface, shape and button treatment;
 * this keeps the same blocking semantics while matching the rest of the
 * interface. Destructive actions are the only place the error colour appears
 * in the action row.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = Space.xxl)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.card))
                .background(scheme.surfaceContainer)
                .border(
                    Stroke.hairline,
                    scheme.outlineVariant,
                    androidx.compose.foundation.shape.RoundedCornerShape(Radius.card),
                )
                .padding(Space.lg),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = edge.faint,
            )
            Spacer(Modifier.height(Space.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextAction(
                    text = "Cancel",
                    color = scheme.onSurfaceVariant,
                    onClick = onDismiss,
                )
                Spacer(Modifier.height(Space.xs))
                TextAction(
                    text = confirmLabel,
                    color = if (destructive) scheme.error else scheme.primary,
                    onClick = onConfirm,
                )
            }
        }
    }
}
