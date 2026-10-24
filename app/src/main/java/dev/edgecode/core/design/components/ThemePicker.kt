package dev.edgecode.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.Stroke
import dev.edgecode.core.design.ThemeFamily
import dev.edgecode.core.design.paletteFor

/**
 * A four-dot preview of a family: its light canvas, light accent, dark canvas
 * and dark accent. The dots are for recognition, not a substitute for the name.
 */
@Composable
fun ThemeSwatch(family: ThemeFamily, modifier: Modifier = Modifier, dark: Boolean? = null) {
    val light = paletteFor(family, false)
    val darkPalette = paletteFor(family, true)
    val colors = if (dark == null) {
        listOf(light.background, light.primary, darkPalette.background, darkPalette.primary)
    } else {
        val palette = if (dark) darkPalette else light
        listOf(palette.background, palette.surfaceContainer, palette.primary)
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(if (dark == null) 12.dp else 10.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(Stroke.hairline, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePickerSheet(
    selected: ThemeFamily,
    onSelect: (ThemeFamily) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.background,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.padding(bottom = Space.xl)) {
            SheetHeader(
                title = "Theme",
                subtitle = "The layout stays the same; the palette changes",
                onClose = onDismiss,
            )
            LazyColumn(
                modifier = Modifier.padding(horizontal = Space.md),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(ThemeFamily.entries, key = { it.name }) { family ->
                    val isSelected = family == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .background(if (isSelected) scheme.surfaceContainerHigh else scheme.background)
                            .tappable { onSelect(family) }
                            .padding(horizontal = Space.md, vertical = Space.xs + 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = family.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurface,
                            )
                            Text(
                                text = "${family.lightVariant} · ${family.darkVariant}",
                                style = MaterialTheme.typography.labelSmall,
                                color = edge.faint,
                            )
                        }
                        ThemeSwatch(family = family)
                        Spacer(Modifier.size(Space.md))
                        Box(modifier = Modifier.size(Size.icon)) {
                            if (isSelected) {
                                Icon(
                                    imageVector = EdgeIcons.Check,
                                    contentDescription = "Selected",
                                    tint = scheme.primary,
                                    modifier = Modifier.size(Size.icon),
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(Space.lg)) }
            }
        }
    }
}
