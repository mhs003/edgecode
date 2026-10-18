package dev.edgecode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.EdgeText
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.Stroke
import dev.edgecode.core.design.components.TextAction
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.model.PermissionDecision
import dev.edgecode.core.model.PermissionKind
import dev.edgecode.core.model.PermissionRequest

/**
 * Tool approval.
 *
 * Pinned above the composer rather than dropped into the transcript: it is a
 * blocking decision, so it must not be possible to scroll past it and wonder why
 * the session went quiet.
 */
@Composable
fun PermissionCard(
    request: PermissionRequest,
    onDecide: (PermissionDecision, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.sm)
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceContainer)
            .border(Stroke.hairline, scheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Icon(
                imageVector = when (request.kind) {
                    PermissionKind.Edit -> EdgeIcons.Edit
                    PermissionKind.Bash -> EdgeIcons.Terminal
                    PermissionKind.Read -> EdgeIcons.File
                    PermissionKind.Network -> EdgeIcons.Server
                    PermissionKind.Other -> EdgeIcons.Warning
                },
                contentDescription = null,
                tint = edge.warning,
                modifier = Modifier.size(Size.iconSm),
            )
            Text(
                text = kindLabel(request.kind),
                style = MaterialTheme.typography.labelMedium,
                color = edge.warning,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(
                text = request.title,
                style = EdgeText.codeSmall,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            request.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        if (request.resources.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                request.resources.take(3).forEach { resource ->
                    Text(
                        text = resource,
                        style = EdgeText.codeSmall,
                        color = edge.faint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (request.resources.size > 3) {
                    Text(
                        text = "+${request.resources.size - 3} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = edge.faint,
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.xxs))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            TextAction(
                text = "Reject",
                color = scheme.error,
                onClick = { onDecide(PermissionDecision.Reject, false) },
            )
            Spacer(Modifier.weight(1f))
            TextAction(
                text = "Always",
                onClick = { onDecide(PermissionDecision.Always, true) },
            )
            Spacer(Modifier.width(Space.xs))
            PermitButton(onClick = { onDecide(PermissionDecision.Once, false) })
        }
    }
}

@Composable
private fun PermitButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = "Allow once",
        style = MaterialTheme.typography.labelLarge,
        color = scheme.onPrimary,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(scheme.primary)
            .tappable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.sm),
    )
}

private fun kindLabel(kind: PermissionKind): String = when (kind) {
    PermissionKind.Edit -> "Wants to edit a file"
    PermissionKind.Bash -> "Wants to run a command"
    PermissionKind.Read -> "Wants to read a file"
    PermissionKind.Network -> "Wants to reach the network"
    PermissionKind.Other -> "Needs your approval"
}
