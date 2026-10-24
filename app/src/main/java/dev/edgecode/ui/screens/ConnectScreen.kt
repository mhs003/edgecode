package dev.edgecode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.components.ConfirmDialog
import dev.edgecode.core.design.components.BarIcon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.Stroke
import dev.edgecode.core.design.components.ActionSheet
import dev.edgecode.core.design.components.EdgeButton
import dev.edgecode.core.design.components.EdgeSecondaryButton
import dev.edgecode.core.design.components.EdgeSwitch
import dev.edgecode.core.design.components.EdgeTextField
import dev.edgecode.core.design.components.EdgeTopBar
import dev.edgecode.core.design.components.ErrorNotice
import dev.edgecode.core.design.components.IconAction
import dev.edgecode.core.design.components.Section
import dev.edgecode.core.design.components.SheetAction
import dev.edgecode.core.design.components.SheetHeader
import dev.edgecode.core.design.components.TextAction
import dev.edgecode.core.design.components.bottomSystemInset
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.design.components.topSystemInset
import dev.edgecode.core.model.ConnectionState
import dev.edgecode.core.model.ServerInfo
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
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var saveForLater by remember { mutableStateOf(true) }
    var prompting by remember { mutableStateOf<SavedConnection?>(null) }
    var removing by remember { mutableStateOf<SavedConnection?>(null) }
    var actionTarget by remember { mutableStateOf<SavedConnection?>(null) }
    var editing by remember { mutableStateOf<SavedConnection?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }

    val connecting = connection is ConnectionState.Connecting
    // Which saved server is being dialled, so that row can say so. The state
    // carries the profile, so no separate "connecting to" flag is needed.
    val connectingId = (connection as? ConnectionState.Connecting)?.profile?.id
    val failure = (connection as? ConnectionState.Failed)?.message

    fun testConnection() {
        testing = true
        testMessage = null
        testOk = false
        viewModel.testConnection(url, username, password) { result ->
            testing = false
            result.onSuccess {
                testOk = true
                testMessage = "Reachable · ${it.label}"
            }.onFailure {
                testOk = false
                testMessage = it.message ?: "Could not reach the server"
            }
        }
    }

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
                            onActions = { actionTarget = savedConnection },
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
                        verticalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        EdgeTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = "Name",
                            placeholder = "Display name (optional)",
                            leadingIcon = EdgeIcons.Server,
                        )

                        EdgeTextField(
                            value = url,
                            onValueChange = { url = it; testMessage = null },
                            label = "Server address",
                            placeholder = "192.168.1.10:4096",
                            leadingIcon = EdgeIcons.Server,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                            enabled = !connecting,
                        )

                        EdgeTextField(
                            value = username,
                            onValueChange = { username = it; testMessage = null },
                            label = "Username",
                            placeholder = "opencode",
                            leadingIcon = EdgeIcons.Person,
                            enabled = !connecting,
                        )

                        EdgeTextField(
                            value = password,
                            onValueChange = { password = it; testMessage = null },
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

                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            EdgeSecondaryButton(
                                text = "Test connection",
                                onClick = { testConnection() },
                                enabled = url.isNotBlank() && !connecting,
                                loading = testing,
                                modifier = Modifier.weight(1f),
                            )
                            EdgeButton(
                                text = if (connecting) "Connecting" else "Connect",
                                onClick = {
                                    testMessage = null
                                    viewModel.connect(url, username, password, saveForLater, name)
                                },
                                enabled = url.isNotBlank() && !testing,
                                loading = connecting,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        testMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (testOk) LocalEdgeColors.current.success else scheme.error,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.sm))

            Column(
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.md),
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
        PasswordSheet(
            connection = target,
            onDismiss = { prompting = null },
            onConfirm = { entered ->
                prompting = null
                viewModel.connectSaved(target, entered)
            },
        )
    }

    removing?.let { target ->
        ConfirmDialog(
            title = "Remove server?",
            message = "\"${target.name}\" will be removed from this list. Any password for it is already memory-only and is forgotten.",
            confirmLabel = "Remove",
            destructive = true,
            onDismiss = { removing = null },
            onConfirm = {
                removing = null
                viewModel.removeSavedConnection(target.id)
            },
        )
    }

    actionTarget?.let { target ->
        ActionSheet(
            title = target.name,
            subtitle = target.url.takeIf { it != target.name },
            onDismiss = { actionTarget = null },
            actions = listOf(
                SheetAction("Edit", EdgeIcons.Edit) { editing = target },
                SheetAction("Remove", EdgeIcons.Trash, destructive = true) {
                    removing = target
                },
            ),
        )
    }

    editing?.let { target ->
        EditConnectionSheet(
            connection = target,
            onDismiss = { editing = null },
            onTest = { testUrl, testUser, onResult ->
                viewModel.testConnection(testUrl, testUser, null, onResult)
            },
            onSave = { name, editUrl, editUser ->
                editing = null
                viewModel.updateSavedConnection(target.id, name, editUrl, editUser)
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
    onActions: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.xs)
            .clip(RoundedCornerShape(Radius.card))
            .background(scheme.surfaceContainerLow)
            .tappable(enabled = enabled, onClick = onConnect)
            .padding(start = Space.sm + 2.dp, end = Space.xxs, top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Size.cardIcon)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = EdgeIcons.Server,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(Size.icon),
            )
        }
        Spacer(Modifier.width(Space.sm + 2.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = connection.name.takeIf { it != connection.url } ?: "Saved server",
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = connection.url,
                style = MaterialTheme.typography.bodySmall,
                color = edge.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xs))
            val status = connection.lastStatus
            when {
                connecting -> Text(
                    text = "Connecting…",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                status != null -> Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                connection.lastUsedAt > 0L -> Text(
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
                color = scheme.onSurface,
                strokeWidth = Stroke.thin,
            )
        } else {
            IconAction(
                icon = EdgeIcons.More,
                contentDescription = "Connection actions",
                onClick = onActions,
                size = Size.controlSm,
                iconSize = Size.iconSm,
                circled = true,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditConnectionSheet(
    connection: SavedConnection,
    onDismiss: () -> Unit,
    onTest: (String, String?, (Result<ServerInfo>) -> Unit) -> Unit,
    onSave: (String, String, String?) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(connection.name) }
    var url by remember { mutableStateOf(connection.url) }
    var username by remember { mutableStateOf(connection.username.orEmpty()) }
    var testing by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }

    fun testConnection() {
        testing = true
        testMessage = null
        testOk = false
        onTest(url, username.trim().takeIf { it.isNotBlank() }) { result ->
            testing = false
            result.onSuccess {
                testOk = true
                testMessage = "Reachable · ${it.label}"
            }.onFailure {
                testOk = false
                testMessage = it.message ?: "Could not reach the server"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.background,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.padding(bottom = Space.xl)) {
            SheetHeader(
                title = "Edit connection",
                subtitle = connection.url,
                onClose = onDismiss,
            )

            Column(
                modifier = Modifier.padding(horizontal = Space.gutter),
                verticalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                EdgeTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Name",
                    placeholder = "Display name",
                    leadingIcon = EdgeIcons.Server,
                )
                EdgeTextField(
                    value = url,
                    onValueChange = { url = it; testMessage = null },
                    label = "Server address",
                    placeholder = "192.168.1.10:4096",
                    leadingIcon = EdgeIcons.Server,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                )
                EdgeTextField(
                    value = username,
                    onValueChange = { username = it; testMessage = null },
                    label = "Username",
                    placeholder = "opencode",
                    leadingIcon = EdgeIcons.Person,
                )
                Text(
                    text = "Passwords are never saved. Reconnecting asks for one " +
                        "when the server needs it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = edge.faint,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    EdgeSecondaryButton(
                        text = "Test connection",
                        onClick = { testConnection() },
                        enabled = url.isNotBlank(),
                        loading = testing,
                        modifier = Modifier.weight(1f),
                    )
                    EdgeButton(
                        text = "Save",
                        onClick = {
                            onSave(
                                name,
                                url,
                                username.trim().takeIf { it.isNotBlank() },
                            )
                        },
                        enabled = url.isNotBlank() && !testing,
                        modifier = Modifier.weight(1f),
                    )
                }

                testMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testOk) edge.success else scheme.error,
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
        EdgeSwitch(
            checked = checked,
            onCheckedChange = { onToggle() },
        )
    }
}

/** Asks for the password a saved server needs; it is never written to disk. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordSheet(
    connection: SavedConnection,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.background,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.padding(bottom = Space.xl)) {
            SheetHeader(
                title = connection.name,
                subtitle = "This server needs a password",
                onClose = onDismiss,
            )
            Column(
                modifier = Modifier.padding(horizontal = Space.gutter),
                verticalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                Text(
                    text = "The password is kept in memory for this session and never " +
                        "saved to the device.",
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
                EdgeButton(
                    text = "Connect",
                    onClick = { onConfirm(value) },
                    enabled = value.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val SavedConnection.needsPassword: Boolean
    get() = lastStatus?.contains("password", ignoreCase = true) == true
