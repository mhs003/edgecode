package dev.edgecode.core.design.components

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.Stroke

/**
 * Primary action. One per screen wherever possible — hierarchy comes from
 * restraint, not from decorating everything.
 */
@Composable
fun EdgeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val active = enabled && !loading
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (active) scheme.primary else scheme.surfaceContainerHigh)
            .tappable(enabled = active, onClick = onClick)
            .padding(horizontal = Space.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(Size.iconSm),
                strokeWidth = 2.dp,
                color = scheme.onPrimary,
            )
            Spacer(Modifier.width(Space.sm))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) scheme.onPrimary else LocalEdgeColors.current.faint,
                modifier = Modifier.size(Size.iconSm),
            )
            Spacer(Modifier.width(Space.sm))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) scheme.onPrimary else LocalEdgeColors.current.faint,
        )
    }
}

/** Secondary action. Tonal, not outlined-heavy. */
@Composable
fun EdgeSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceContainer)
            .border(Stroke.hairline, scheme.outlineVariant, MaterialTheme.shapes.medium)
            .tappable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(Size.iconSm),
            )
            Spacer(Modifier.width(Space.sm))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) scheme.onSurface else LocalEdgeColors.current.faint,
        )
    }
}

/**
 * Circular icon control.
 *
 * The transcript header and the composer both build from this: a row of circles
 * reads as a single toolbar rather than three separate buttons, and it is the
 * shape every current chat client uses for its primary affordances.
 */
@Composable
fun CircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = Size.control,
    iconSize: androidx.compose.ui.unit.Dp = Size.icon,
    fill: Color? = null,
    tint: Color? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    // The touch target is deliberately larger than the circle. Attaching the
    // gesture to the visual meant the hit area *was* the 38dp control, and a tap
    // a few dp outside the rim did nothing — worst at the screen edge, where the
    // send button sits. The visual size is unchanged; only the target grows, and
    // never below the 48dp platform minimum.
    Box(
        modifier = modifier
            .size(maxOf(size, Size.touch))
            .tappable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.full))
                .background(if (enabled) fill ?: scheme.surfaceContainer else scheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint ?: scheme.onSurfaceVariant else edge.faint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** Inline text action for low-emphasis affordances. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) color else LocalEdgeColors.current.faint,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .tappable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.xs, vertical = Space.xs),
    )
}

/**
 * Text input.
 *
 * Built on [BasicTextField] rather than a Material text field: the default
 * outlined treatment is heavier than this design system wants, and a tonal
 * fill with a single focus hairline reads more calmly.
 */
@Composable
fun EdgeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    supporting: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = 48.dp,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val borderColor = when {
        isError -> scheme.error
        focused -> scheme.primary
        else -> scheme.outlineVariant
    }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.xs, bottom = Space.xs),
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = textStyle.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (enabled) scheme.surfaceContainer else scheme.surfaceContainerLow)
                        .border(Stroke.hairline, borderColor, MaterialTheme.shapes.medium)
                        .padding(horizontal = Space.md, vertical = Space.sm),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = edge.faint,
                            modifier = Modifier.size(Size.icon),
                        )
                        Spacer(Modifier.width(Space.sm))
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = edge.faint,
                                maxLines = if (singleLine) 1 else 4,
                            )
                        }
                        inner()
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(Space.xs))
                        trailing()
                    }
                }
            },
        )

        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) scheme.error else edge.faint,
                modifier = Modifier.padding(start = Space.xs, top = Space.xs),
            )
        }
    }
}

/** Small tonal label, used for status and metadata. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = accent ?: scheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.full))
            .background(if (accent != null) accent.copy(alpha = 0.14f) else scheme.surfaceContainer)
            .padding(horizontal = Space.sm, vertical = Space.xxs + 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(Size.iconSm),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** Status dot. Always paired with a label so colour is never the only signal. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Size.statusDot)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.full))
            .background(color),
    )
}
