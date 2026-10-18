package dev.edgecode.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.ThemeMode
import dev.edgecode.core.design.components.Chip
import dev.edgecode.core.design.components.EdgeSecondaryButton
import dev.edgecode.core.design.components.EdgeTopBar
import dev.edgecode.core.design.components.RowDivider
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

            Section(title = "Appearance") {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(
                        start = Space.gutter,
                        end = Space.gutter,
                        bottom = Space.sm,
                    ),
                )
                ThemeSelector(
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
                RowDivider()
                ToggleRow(
                    title = "Expand tool calls",
                    subtitle = "Open tool input and output by default",
                    checked = settings.expandTools,
                    onToggle = { viewModel.setExpandTools(it) },
                )
                RowDivider()
                ToggleRow(
                    title = "Send on Enter",
                    subtitle = "Off means Enter adds a newline",
                    checked = settings.sendOnEnter,
                    onToggle = { viewModel.setSendOnEnter(it) },
                )
            }

            Section(title = "Server") {
                val connected = connection as? ConnectionState.Connected
                SettingsRow(
                    title = connected?.profile?.name ?: "Not connected",
                    subtitle = connected?.profile?.url ?: "No server configured",
                    icon = EdgeIcons.Server,
                    trailing = {
                        connected?.let {
                            Chip(
                                text = it.info.label,
                                accent = if (it.info.api.name == "Demo") edge.info else edge.success,
                            )
                        }
                    },
                )
                if (connected != null) {
                    RowDivider()
                    Box(modifier = Modifier.padding(Space.gutter)) {
                        EdgeSecondaryButton(
                            text = "Disconnect",
                            onClick = { viewModel.disconnect() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Section(title = "About") {
                SettingsRow(
                    title = "EdgeCode",
                    subtitle = "Version $version",
                    icon = EdgeIcons.Info,
                )
                RowDivider()
                SettingsRow(
                    title = "OpenCode server",
                    subtitle = (connection as? ConnectionState.Connected)?.info?.version
                        ?.let { "v$it" } ?: "Not connected",
                    icon = EdgeIcons.Terminal,
                )
                RowDivider()
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
}

@Composable
private fun ThemeSelector(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
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
                    .clip(MaterialTheme.shapes.small)
                    .background(if (selected) scheme.primaryContainer else scheme.surfaceContainer)
                    .tappable { onSelect(mode) }
                    .padding(vertical = Space.md),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = when (mode) {
                        ThemeMode.Dark -> EdgeIcons.Moon
                        ThemeMode.Light -> EdgeIcons.Sun
                        ThemeMode.System -> EdgeIcons.Settings
                    },
                    contentDescription = null,
                    tint = if (selected) scheme.onPrimaryContainer else edge.faint,
                    modifier = Modifier.size(Size.iconSm),
                )
                Spacer(Modifier.size(Space.sm))
                Text(
                    text = mode.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
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
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = scheme.onPrimary,
                checkedTrackColor = scheme.primary,
                uncheckedThumbColor = edge.faint,
                uncheckedTrackColor = scheme.surfaceContainerHigh,
                uncheckedBorderColor = scheme.outline,
            ),
        )
    }
}
