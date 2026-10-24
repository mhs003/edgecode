package dev.edgecode.ui.screens

import androidx.compose.foundation.background
import dev.edgecode.core.design.Stroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.ThemeFamily
import dev.edgecode.core.design.ThemeMode
import dev.edgecode.core.design.components.Chip
import dev.edgecode.core.design.components.EdgeSwitch
import dev.edgecode.core.design.components.ThemePickerSheet
import dev.edgecode.core.design.components.ThemeSwatch
import dev.edgecode.core.design.components.EdgeMark
import dev.edgecode.core.design.components.EdgeSecondaryButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.components.EdgeTopBar
import dev.edgecode.core.design.components.Section
import dev.edgecode.core.design.components.SettingsRow
import dev.edgecode.core.design.components.bottomSystemInset
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.design.components.topSystemInset
import dev.edgecode.core.model.ConnectionState
import dev.edgecode.ui.AppViewModel

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val settings by viewModel.settings_.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val context = LocalContext.current
    var showThemes by remember { mutableStateOf(false) }

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .topSystemInset()
            .bottomSystemInset(),
    ) {
        EdgeTopBar(
            title = "Settings",
            onBack = { viewModel.back() },
        )

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

            ConnectionCard(
                connection = connection,
                onDisconnect = { viewModel.disconnect() },
            )

            Section(title = "Appearance") {
                SettingsRow(
                    title = "Theme",
                    subtitle = "${settings.themeFamily.lightVariant} · ${settings.themeFamily.darkVariant}",
                    onClick = { showThemes = true },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ThemeSwatch(family = settings.themeFamily)
                            Spacer(Modifier.width(Space.sm))
                            Icon(
                                imageVector = EdgeIcons.ChevronRight,
                                contentDescription = null,
                                tint = edge.faint,
                                modifier = Modifier.size(Size.iconSm),
                            )
                        }
                    },
                )
                Text(
                    text = "Mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = edge.faint,
                    modifier = Modifier.padding(
                        start = Space.gutter,
                        end = Space.gutter,
                        top = Space.md,
                        bottom = Space.xs,
                    ),
                )
                ModeSelector(
                    current = settings.themeMode,
                    onSelect = { viewModel.setTheme(it) },
                )
            }

            Section(title = "Chat") {
                ToggleRow(
                    title = "Show thinking",
                    subtitle = "Display the model's reasoning above its answer",
                    checked = settings.showReasoning,
                    onToggle = { viewModel.setShowReasoning(it) },
                )
                ToggleRow(
                    title = "Expand tool calls",
                    subtitle = "Open tool input and output by default",
                    checked = settings.expandTools,
                    onToggle = { viewModel.setExpandTools(it) },
                )
                ToggleRow(
                    title = "Send on Enter",
                    subtitle = "Off means Enter adds a newline",
                    checked = settings.sendOnEnter,
                    onToggle = { viewModel.setSendOnEnter(it) },
                )
            }

            Section(title = "About") {
                SettingsRow(
                    title = "EdgeCode",
                    subtitle = "Version $version",
                    icon = EdgeIcons.Info,
                )
                SettingsRow(
                    title = "OpenCode server",
                    subtitle = (connection as? ConnectionState.Connected)?.info?.version
                        ?.let { "v$it" } ?: "Not connected",
                    icon = EdgeIcons.Terminal,
                )
                Column(
                    modifier = Modifier.padding(Space.gutter),
                ) {
                    Text(
                        text = "This build runs against a local demo dataset. " +
                            "Tool calls, streaming replies and permission prompts are " +
                            "simulated in-process and never leave the device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = edge.faint,
                    )
                }
            }

            Spacer(Modifier.height(Space.xxxl))
        }
    }

    if (showThemes) {
        ThemePickerSheet(
            selected = settings.themeFamily,
            onSelect = {
                viewModel.setThemeFamily(it)
                showThemes = false
            },
            onDismiss = { showThemes = false },
        )
    }
}

@Composable
private fun ConnectionCard(
    connection: ConnectionState,
    onDisconnect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val connected = connection as? ConnectionState.Connected

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.md)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.card))
            .background(scheme.surfaceContainerLow)
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EdgeMark(size = Size.cardIcon)
            Spacer(Modifier.width(Space.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = connected?.profile?.name
                        ?.takeIf { it != connected.profile?.url }
                        ?: if (connected != null) "Server" else "Not connected",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = connected?.profile?.url ?: "No server configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = edge.faint,
                    maxLines = 1,
                )
            }
            connected?.let {
                Chip(text = it.info.label)
            }
        }
        if (connected != null) {
            Text(
                text = "Connected to an OpenCode server. The session list, models and " +
                    "questions all come from this endpoint.",
                style = MaterialTheme.typography.bodySmall,
                color = edge.faint,
            )
            EdgeSecondaryButton(
                text = "Disconnect",
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ModeSelector(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        ThemeMode.entries.forEach { mode ->
            val selected = mode == current
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Size.touchSm)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.md))
                    .background(if (selected) scheme.surfaceContainerHigh else scheme.surfaceContainerLow)
                    .border(
                        Stroke.hairline,
                        if (selected) scheme.onSurface.copy(alpha = 0.28f) else scheme.outlineVariant,
                        androidx.compose.foundation.shape.RoundedCornerShape(Radius.md),
                    )
                    .tappable { onSelect(mode) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (mode) {
                        ThemeMode.System -> "System"
                        ThemeMode.Light -> "Light"
                        ThemeMode.Dark -> "Dark"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) scheme.onSurface else edge.faint,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable { onToggle(!checked) }
            .padding(horizontal = Space.gutter, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = edge.faint,
            )
        }
        EdgeSwitch(
            checked = checked,
            onCheckedChange = onToggle,
        )
    }
}
