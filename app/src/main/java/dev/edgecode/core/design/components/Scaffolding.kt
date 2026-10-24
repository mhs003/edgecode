package dev.edgecode.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space

/**
 * Screen header.
 *
 * Flat and seamless: it paints the canvas colour and draws no rule, so the
 * content below is separated by space rather than by a line. The optional mark
 * and badge turn the session list into a product header without adding a second
 * band of chrome.
 */
@Composable
fun EdgeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: String? = null,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth().background(scheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Size.topBar)
                .padding(start = if (onBack != null || leading != null) Space.xxs else Space.gutter, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                BarIcon(
                    icon = EdgeIcons.Back,
                    contentDescription = "Back",
                    onClick = onBack,
                )
            }
            if (leading != null) {
                leading()
                Spacer(Modifier.width(Space.sm))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(Space.sm))
                        Chip(text = badge)
                    }
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalEdgeColors.current.faint,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
    }
}

/**
 * Inline search that takes over the header.
 *
 * Appending a field below the bar left two competing headers on screen and
 * pushed the whole list down. This replaces the bar instead, so entering search
 * is a swap rather than an insertion and the field is the only thing on the row.
 * The field is a tonal pill like the composer, not an outlined box.
 */
@Composable
fun EdgeSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val interaction = remember { MutableInteractionSource() }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Size.topBar)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIcon(
            icon = EdgeIcons.Back,
            contentDescription = "Close search",
            onClick = onClose,
        )
        Spacer(Modifier.width(Space.sm))
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = Size.input)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.md))
                .background(scheme.surfaceContainer)
                .padding(start = Space.lg, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                interactionSource = interaction,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = edge.faint,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            if (value.isNotEmpty()) {
                IconAction(
                    icon = EdgeIcons.Close,
                    contentDescription = "Clear search",
                    onClick = { onValueChange("") },
                    size = Size.touchSm,
                    iconSize = Size.iconSm,
                )
            }
        }
    }
}

/** Labelled group of rows. A heading and spacing, not a card. */
@Composable
fun Section(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = LocalEdgeColors.current.faint,
                modifier = Modifier.padding(
                    start = Space.gutter,
                    end = Space.gutter,
                    top = Space.md,
                    bottom = Space.xs,
                ),
            )
        }
        Column(content = content)
    }
}

/** A settings-style row: optional icon, title, secondary line, trailing slot. */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val titleColor = if (destructive) scheme.error else scheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.tappable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) scheme.error else edge.faint,
                modifier = Modifier.size(Size.icon),
            )
            Spacer(Modifier.width(Space.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = edge.faint,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.sm))
            trailing()
        } else if (onClick != null && !destructive) {
            Icon(
                imageVector = EdgeIcons.ChevronRight,
                contentDescription = null,
                tint = edge.faint.copy(alpha = 0.7f),
                modifier = Modifier.size(Size.iconSm),
            )
        }
    }
}

@Composable
fun RowDivider(modifier: Modifier = Modifier, indent: androidx.compose.ui.unit.Dp = Space.gutter) {
    HorizontalDivider(
        modifier = modifier.padding(start = indent),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Empty state: one line of intent plus an optional action. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val edge = LocalEdgeColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xxl, vertical = Space.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.full))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = edge.faint,
                    modifier = Modifier.size(Size.iconLg),
                )
            }
            Spacer(Modifier.height(Space.sm))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = edge.faint,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(Space.sm))
            action()
        }
    }
}

/** Inline error with an optional retry. */
@Composable
fun ErrorNotice(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.error.copy(alpha = 0.10f))
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(
            imageVector = EdgeIcons.Warning,
            contentDescription = null,
            tint = scheme.error,
            modifier = Modifier.size(Size.iconSm),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            TextAction(text = "Retry", onClick = onRetry)
        }
    }
}

/** Small centred label for in-progress work. */
@Composable
fun InlineProgress(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Space.xl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(Size.icon),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalEdgeColors.current.faint,
        )
    }
}

/** Header for a modal sheet. */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = Space.sm, bottom = Space.sm),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(3.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.full))
                    .background(scheme.outline),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.gutter, end = Space.sm, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalEdgeColors.current.faint,
                    )
                }
            }
            if (onClose != null) {
                BarIcon(
                    icon = EdgeIcons.Close,
                    contentDescription = "Close",
                    onClick = onClose,
                )
            }
        }
    }
}

/** Coloured accent bar used for diff blocks and file rows. */
@Composable
fun AccentBar(color: Color, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp = 3.dp) {
    Box(
        modifier = modifier
            .width(width)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.full))
            .background(color),
    )
}
