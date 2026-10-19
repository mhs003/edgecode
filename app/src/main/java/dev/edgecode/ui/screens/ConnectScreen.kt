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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.Stroke
import dev.edgecode.core.design.components.EdgeButton
import dev.edgecode.core.design.components.EdgeSecondaryButton
import dev.edgecode.core.design.components.EdgeTextField
import dev.edgecode.core.design.components.EdgeTopBar
import dev.edgecode.core.design.components.ErrorNotice
import dev.edgecode.core.design.components.IconAction
import dev.edgecode.core.design.components.Section
import dev.edgecode.core.design.components.TextAction
import dev.edgecode.core.design.components.bottomSystemInset
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.design.components.topSystemInset
import dev.edgecode.core.model.ConnectionState
import dev.edgecode.core.util.formatRelative
import dev.edgecode.data.SavedConnection
import dev.edgecode.ui.AppViewModel

/**
 * First screen: point the app at a server.
 *
 * The servers the user has already reached come first, because on every launch
 * after the first that is the likely target. The add form is disclosed rather
 * than always present, so the list — not a blank URL field — is the focus.
 */
@Composable
fun ConnectScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val connection by viewModel.connection.collectAsState()
    val saved = viewModel.savedConnections.collectAsState().value

    var formOpen by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var saveForLater by remember { mutableStateOf(true) }
    var prompting by remember { mutableStateOf<SavedConnection?>(null) }
    var removing by remember { mutableStateOf<SavedConnection?>(null) }

    val connecting = connection is ConnectionState.Connecting
    // Which saved server is being dialled, so that row can say so. The state
    // carries the profile, so no separate "connecting to" flag is needed.
    val connectingId = (connection as? ConnectionState.Connecting)?.profile?.id
    val failure = (connection as? ConnectionState.Failed)?.message

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .topSystemInset()
            .bottomSystemInset(),
    ) {
        EdgeTopBar(title = "Connect")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (saved.isNotEmpty()) {
                Section(title = "Saved") {
                    saved.forEach { savedConnection ->
                        SavedRow(
                            connection = savedConnection,
                            connecting = connectingId == savedConnection.id,
                            enabled = !connecting,
                            onConnect = {
                                if (savedConnection.needsPassword) {
                                    viewModel.forgetSaved(savedConnection.id)
                                    prompting = savedConnection
                                } else {
                                    viewModel.connectSaved(savedConnection, null)
                                }
                            },
                            onRemove = { removing = savedConnection },
                        )
                    }
                }
            }

            if (!formOpen) {
                EdgeSecondaryButton(
                    text = "New connection",
                    icon = EdgeIcons.Plus,
                    onClick = { formOpen = true },
                    enabled = !connecting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter, vertical = Space.lg),
                )
            } else {
                Section(title = "New connection") {
                    Column(
                        modifier = Modifier.padding(horizontal = Space.gutter),
                        verticalArrangement = Arrangement.spacedBy(Space.lg),
                    ) {
                        EdgeTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = "Server address",
                            placeholder = "192.168.1.10:4096",
                            leadingIcon = EdgeIcons.Server,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                            enabled = !connecting,
                        )

                        EdgeTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = "Username",
                            placeholder = "opencode",
                            leadingIcon = EdgeIcons.Person,
                            enabled = !connecting,
                        )

                        EdgeTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Password",
                            placeholder = "Server password",
                            leadingIcon = EdgeIcons.Lock,
                            singleLine = true,
                            enabled = !connecting,
                            imeAction = ImeAction.Done,
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailing = {
                                TextAction(
                                    text = if (showPassword) "Hide" else "Show",
                                    onClick = { showPassword = !showPassword },
                                )
                            },
                        )

                        SaveToggle(
                            checked = saveForLater,
                            onToggle = { saveForLater = !saveForLater },
                        )

                        if (failure != null) {
                            ErrorNotice(message = failure)
                        }

                        EdgeButton(
                            text = if (connecting) "Connecting" else "Connect",
                            onClick = {
                                viewModel.connect(url, username, password, saveForLater)
                            },
                            enabled = url.isNotBlank(),
                            loading = connecting,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                EdgeSecondaryButton(
                    text = "Explore the demo",
                    icon = EdgeIcons.Terminal,
                    onClick = { viewModel.connectDemo() },
                    enabled = !connecting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "No server yet? The demo runs the whole interface against " +
                        "sample data, including streaming replies, tool calls and " +
                        "permission prompts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalEdgeColors.current.faint,
                )
            }

            Spacer(Modifier.height(Space.xxxl))
        }
    }

    prompting?.let { target ->
        PasswordPrompt(
            connection = target,
            onDismiss = { prompting = null },
            onConfirm = { entered ->
                prompting = null
                viewModel.connectSaved(target, entered)
            },
        )
    }

    removing?.let { target ->
        RemoveDialog(
            connection = target,
            onDismiss = { removing = null },
            onConfirm = {
                removing = null
                viewModel.removeSavedConnection(target.id)
            },
        )
    }
}

/**
 * A server in the saved list. The last failure, when there is one, takes the
 * place of the relative time so a dead server reads as dead before it is tapped.
 *
 * While this server is the one being dialled the meta line says so and the
 * trailing control becomes a spinner. Tapping a row and getting no feedback for
 * the length of a connection attempt reads as a dead tap.
 */
@Composable
private fun SavedRow(
    connection: SavedConnection,
    connecting: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit,
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable(enabled = enabled, onClick = onConnect)
            .padding(start = Space.gutter, end = Space.xxs, top = Space.md, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = connection.name,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = connection.url,
                style = MaterialTheme.typography.labelSmall,
                color = edge.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xxs))
            val status = connection.lastStatus
            if (connecting) {
                Text(
                    text = "Connecting…",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                )
            } else if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = edge.warning,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (connection.lastUsedAt > 0L) {
                Text(
                    text = formatRelative(connection.lastUsedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = edge.faint,
                )
            }
        }

        if (connecting) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = Space.md)
                    .size(Size.iconSm),
                color = scheme.primary,
                strokeWidth = Stroke.thin,
            )
        } else {
            Box {
                IconAction(
                    icon = EdgeIcons.More,
                    contentDescription = "Connection actions",
                    onClick = { menu = true },
                    size = Size.touchSm,
                    iconSize = Size.iconSm,
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Remove", color = scheme.error) },
                        onClick = {
                            menu = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveToggle(checked: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Save for later",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
            )
            Text(
                text = "Keep this server in the list on this device",
                style = MaterialTheme.typography.bodySmall,
                color = edge.faint,
            )
        }
        Spacer(Modifier.width(Space.sm))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
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

/** Asks for the password a saved server needs; it is never written to disk. */
@Composable
private fun PasswordPrompt(
    connection: SavedConnection,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    var value by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(
                text = connection.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(
                    text = "This server needs a password. It is kept in memory for " +
                        "this session and never saved to the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = edge.faint,
                )
                EdgeTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = "Password",
                    leadingIcon = EdgeIcons.Lock,
                    singleLine = true,
                    imeAction = ImeAction.Done,
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailing = {
                        TextAction(
                            text = if (visible) "Hide" else "Show",
                            onClick = { visible = !visible },
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
            ) { Text("Connect") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun RemoveDialog(
    connection: SavedConnection,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        title = { Text("Remove server?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = "\"${connection.name}\" will be removed from this list. " +
                    "Any password for it is already memory-only and is forgotten.",
                style = MaterialTheme.typography.bodyMedium,
                color = edge.faint,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Remove", color = scheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private val SavedConnection.needsPassword: Boolean
    get() = lastStatus?.contains("password", ignoreCase = true) == true
