package dev.edgecode.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.EdgeText
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.components.BarIcon
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.Stroke
import dev.edgecode.core.design.components.CircleButton
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.AttachmentKind
import dev.edgecode.core.model.SlashCommand
import dev.edgecode.core.util.loadThumbnail

/** Test tag for the composer container, used by the inset regression tests. */
const val ComposerTestTag = "composer"

/** Test tag for the editable field, so tests can type without hunting the placeholder. */
const val ComposerInputTestTag = "composer-input"

/**
 * Message input.
 *
 * A single rounded pill floating clear of the window edges, with the controls
 * that matter on the inside: attach, what will answer (model), how (agent), and
 * send. Keeping those in the composer rather than in a settings screen is what
 * makes the app feel like a client for a running session rather than a form.
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    attachments: List<Attachment>,
    onRemoveAttachment: (String) -> Unit,
    onPickAttachment: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    streaming: Boolean,
    modelLabel: String,
    agentLabel: String,
    onModelClick: () -> Unit,
    onAgentClick: () -> Unit,
    commands: List<SlashCommand>,
    onCommandSelected: (SlashCommand) -> Unit,
    sendOnEnter: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val slashQuery = remember(value) {
        val trimmed = value.trimStart()
        if (trimmed.startsWith("/") && !trimmed.contains(' ')) trimmed.drop(1) else null
    }
    val matches = remember(slashQuery, commands) {
        if (slashQuery == null) emptyList()
        else commands.filter { it.name.startsWith(slashQuery, ignoreCase = true) }.take(6)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Space.md + 2.dp,
                end = Space.md + 2.dp,
                top = Space.xs,
                bottom = Space.md + 2.dp,
            )
            .testTag(ComposerTestTag),
    ) {
        AnimatedVisibility(
            visible = matches.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SlashCommandList(commands = matches, onSelect = onCommandSelected)
        }

        if (attachments.isNotEmpty()) {
            AttachmentStrip(attachments = attachments, onRemove = onRemoveAttachment)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.composer))
                .background(scheme.surfaceContainerLow)
                .border(
                    Stroke.hairline,
                    scheme.outlineVariant,
                    RoundedCornerShape(Radius.composer),
                ),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = EdgeText.composer.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                interactionSource = interaction,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
                // Grows with the prompt up to a fixed number of lines, then
                // scrolls inside the card. Without a cap a pasted block or a
                // run of blank lines pushes the composer over the transcript.
                maxLines = Size.composerMaxLines,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.lg, end = Space.lg, top = Space.md + 2.dp, bottom = Space.sm)
                    .testTag(ComposerInputTestTag)
                    .onPreviewKeyEvent { event ->
                        if (sendOnEnter &&
                            event.type == KeyEventType.KeyDown &&
                            event.key == Key.Enter &&
                            !event.isShiftPressed
                        ) {
                            onSend()
                            true
                        } else {
                            false
                        }
                    },
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "Type something...",
                                style = EdgeText.composer,
                                color = edge.faint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Space.sm,
                        end = Space.sm,
                        top = Space.xxs,
                        bottom = Space.sm,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BarIcon(
                        icon = EdgeIcons.Plus,
                        contentDescription = "Add attachment",
                        onClick = onPickAttachment,
                        tint = scheme.onSurfaceVariant,
                        size = 34.dp,
                        iconSize = Size.iconMd,
                    )
                    ComposerChip(
                        label = modelLabel,
                        onClick = onModelClick,
                        trailing = EdgeIcons.ChevronDown,
                        emphasized = true,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (agentLabel.isNotBlank()) {
                        ComposerChip(
                            label = agentLabel,
                            onClick = onAgentClick,
                        )
                    }
                }

                Spacer(Modifier.width(Space.sm))

                val active = streaming || value.isNotBlank() || attachments.isNotEmpty()
                CircleButton(
                    icon = if (streaming) EdgeIcons.Stop else EdgeIcons.Send,
                    contentDescription = if (streaming) "Stop" else "Send",
                    onClick = { if (streaming) onStop() else onSend() },
                    size = Size.send,
                    iconSize = Size.iconMd,
                    fill = if (active) scheme.primary else scheme.surfaceContainerHigh,
                    tint = if (active) scheme.onPrimary else edge.faint,
                    enabled = streaming || value.isNotBlank() || attachments.isNotEmpty(),
                )
            }
        }
    }
}

/**
 * A text control inside the composer.
 *
 * The target treats the active model as a label with a disclosure chevron, not
 * as a filled pill. Keeping the fill off the row leaves the composer quiet and
 * gives the send control the only strong shape.
 */
@Composable
private fun ComposerChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    emphasized: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val content = if (emphasized) scheme.onSurface else scheme.onSurfaceVariant
    Row(
        modifier = modifier
            .heightIn(min = 26.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .tappable(onClick = onClick)
            .padding(horizontal = Space.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(
                imageVector = leading,
                contentDescription = null,
                tint = edge.faint,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(Space.xs))
        }
        Text(
            text = label,
            style = EdgeText.meta,
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = if (emphasized) 208.dp else 64.dp),
        )
        if (trailing != null) {
            Spacer(Modifier.width(Space.xxs))
            Icon(
                imageVector = trailing,
                contentDescription = null,
                tint = edge.faint,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun SlashCommandList(
    commands: List<SlashCommand>,
    onSelect: (SlashCommand) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(scheme.surfaceContainer)
            .padding(vertical = Space.xs),
    ) {
        Text(
            text = "COMMANDS",
            style = MaterialTheme.typography.labelSmall,
            color = edge.faint,
            modifier = Modifier.padding(start = Space.lg, top = Space.xs, bottom = Space.xs),
        )
        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
            items(commands, key = { it.name }) { command ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tappable { onSelect(command) }
                        .padding(horizontal = Space.md, vertical = Space.xs + 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    Text(
                        text = "/${command.name}",
                        style = EdgeText.monoButton,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = command.description.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = edge.faint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentStrip(
    attachments: List<Attachment>,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        attachments.forEach { attachment ->
            AttachmentThumb(attachment = attachment, onRemove = { onRemove(attachment.id) })
        }
    }
}

@Composable
private fun AttachmentThumb(
    attachment: Attachment,
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val context = LocalContext.current

    val bitmap by produceState<ImageBitmap?>(initialValue = null, attachment.uri) {
        value = if (attachment.kind == AttachmentKind.Image && attachment.uri != null) {
            loadThumbnail(context, attachment.uri)
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .size(Size.attachment)
            .clip(RoundedCornerShape(Radius.sm))
            .background(scheme.surfaceContainer),
    ) {
        val image = bitmap
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = attachment.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(min = Size.attachment),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = if (attachment.kind == AttachmentKind.Image) {
                        EdgeIcons.Image
                    } else {
                        EdgeIcons.File
                    },
                    contentDescription = null,
                    tint = edge.faint,
                    modifier = Modifier.size(Size.icon),
                )
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = edge.faint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Space.xxs)
                .size(20.dp)
                .clip(RoundedCornerShape(Radius.full))
                .background(scheme.surfaceContainerHigh)
                .tappable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = EdgeIcons.Close,
                contentDescription = "Remove ${attachment.name}",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}
