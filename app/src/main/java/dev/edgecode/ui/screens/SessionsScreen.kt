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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.components.Chip
import dev.edgecode.core.design.components.EdgeButton
import dev.edgecode.core.design.components.EdgeTextField
import dev.edgecode.core.design.components.CircleButton
import dev.edgecode.core.design.components.EdgeSearchBar
import dev.edgecode.core.design.components.EdgeTopBar
import dev.edgecode.core.design.components.StatusDot
import dev.edgecode.core.design.components.EmptyState
import dev.edgecode.core.design.components.IconAction
import dev.edgecode.core.design.components.InlineProgress
import dev.edgecode.core.design.components.RowDivider
import dev.edgecode.core.design.components.Section
import dev.edgecode.core.design.components.SettingsRow
import dev.edgecode.core.design.components.StatusDot
import dev.edgecode.core.design.components.bottomSystemInset
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.design.components.topSystemInset
import dev.edgecode.core.model.ConnectionState
import dev.edgecode.core.model.SessionStatus
import dev.edgecode.core.model.SessionSummary
import dev.edgecode.core.util.formatCost
import dev.edgecode.core.util.formatRelative
import dev.edgecode.core.util.formatTokens
import dev.edgecode.core.util.shortenPath
import dev.edgecode.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val connection by viewModel.connection.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val catalog by viewModel.catalog.collectAsState()

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SessionSummary?>(null) }
    var deleting by remember { mutableStateOf<SessionSummary?>(null) }
    var creating by remember { mutableStateOf(false) }

    val filtered = remember(sessions, query) {
        if (query.isBlank()) {
            sessions
        } else {
            sessions.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.directory.contains(query, ignoreCase = true)
            }
        }
    }
    val active = filtered.filter { it.status != SessionStatus.Idle }
    val recent = filtered.filter { it.status == SessionStatus.Idle }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .topSystemInset()
            .bottomSystemInset(),
    ) {
        // Search takes over the bar rather than appearing beneath it: two
        // headers on screen at once was the thing that read as broken.
        if (searchOpen) {
            EdgeSearchBar(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search sessions",
                onClose = { searchOpen = false; query = "" },
            )
        } else {
            EdgeTopBar(
                title = "Sessions",
                subtitle = (connection as? ConnectionState.Connected)?.profile?.url,
                actions = {
                    CircleButton(
                        icon = EdgeIcons.Search,
                        contentDescription = "Search sessions",
                        onClick = { searchOpen = true },
                    )
                    Spacer(Modifier.width(Space.sm))
                    CircleButton(
                        icon = EdgeIcons.Plus,
                        contentDescription = "New session",
                        onClick = { creating = true },
                    )
                    Spacer(Modifier.width(Space.sm))
                    Box {
                        CircleButton(
                            icon = EdgeIcons.More,
                            contentDescription = "More",
                            onClick = { menuOpen = true },
                        )
                        SessionsMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            serverUrl = (connection as? ConnectionState.Connected)?.profile?.url,
                            sessionCount = sessions.size,
                            onNewSession = { menuOpen = false; creating = true },
                            onRefresh = { menuOpen = false; viewModel.refresh() },
                            onSettings = { menuOpen = false; viewModel.openSettings() },
                            onDisconnect = { menuOpen = false; viewModel.disconnect() },
                        )
                    }
                },
            )
        }

        ConnectionBanner(
            connection = connection,
            onReconnect = { viewModel.retryConnect() },
        )

        if (sessions.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "No sessions yet",
                    message = "Sessions live on the server. Start one here and it will " +
                        "appear in the OpenCode TUI too.",
                    icon = EdgeIcons.Terminal,
                    action = {
                        EdgeButton(
                            text = "New session",
                            icon = EdgeIcons.Plus,
                            onClick = { creating = true },
                        )
                    },
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                // Rows are separated by space rather than by rules: a divider
                // per row turns a list of sessions into a table.
                if (active.isNotEmpty()) {
                    item { SectionTitle("Active") }
                    itemsIndexed(active, key = { _, s -> s.id }) { _, session ->
                        SessionRow(
                            session = session,
                            onOpen = { viewModel.openSession(session.id) },
                            onRename = { renaming = session },
                            onDelete = { deleting = session },
                        )
                    }
                }
                if (recent.isNotEmpty()) {
                    item { SectionTitle("Recent") }
                    itemsIndexed(recent, key = { _, s -> s.id }) { _, session ->
                        SessionRow(
                            session = session,
                            onOpen = { viewModel.openSession(session.id) },
                            onRename = { renaming = session },
                            onDelete = { deleting = session },
                        )
                    }
                }
                item { Spacer(Modifier.height(Space.xxxl)) }
            }
        }
    }

    if (creating) {
        NewSessionSheet(
            directories = catalog.projects.map { it.directory },
            onDismiss = { creating = false },
            onCreate = { directory, title ->
                creating = false
                viewModel.createSession(directory, title)
            },
        )
    }

    renaming?.let { session ->
        RenameDialog(
            initial = session.title,
            onDismiss = { renaming = null },
            onConfirm = { title ->
                viewModel.renameSession(session.id, title)
                renaming = null
            },
        )
    }

    deleting?.let { session ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = scheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
            title = { Text("Delete session?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = "\"${session.title}\" and its messages will be removed from the server. " +
                        "This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = edge.faint,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(session.id)
                    deleting = null
                }) {
                    Text("Delete", color = scheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Overflow menu.
 *
 * The old one listed three verbs with no context. This states what you are
 * connected to and how much is there before offering the actions, so the menu
 * answers "what am I looking at" as well as "what can I do".
 */
@Composable
private fun SessionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    serverUrl: String?,
    sessionCount: Int,
    onNewSession: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                StatusDot(scheme.primary)
                Text(
                    text = serverUrl ?: "Local demo",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (sessionCount == 1) "1 session" else "$sessionCount sessions",
                style = MaterialTheme.typography.labelSmall,
                color = edge.faint,
            )
        }
        HorizontalDivider(color = scheme.outlineVariant)

        DropdownMenuItem(
            text = { Text("New session") },
            leadingIcon = { MenuIcon(EdgeIcons.Plus) },
            onClick = onNewSession,
        )
        DropdownMenuItem(
            text = { Text("Refresh") },
            leadingIcon = { MenuIcon(EdgeIcons.Refresh) },
            onClick = onRefresh,
        )
        DropdownMenuItem(
            text = { Text("Settings") },
            leadingIcon = { MenuIcon(EdgeIcons.Settings) },
            onClick = onSettings,
        )

        HorizontalDivider(color = scheme.outlineVariant)

        DropdownMenuItem(
            text = { Text("Disconnect", color = scheme.error) },
            leadingIcon = { MenuIcon(EdgeIcons.Server, tint = scheme.error) },
            onClick = onDisconnect,
        )
    }
}

@Composable
private fun MenuIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color? = null) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint ?: LocalEdgeColors.current.faint,
        modifier = Modifier.size(Size.icon),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalEdgeColors.current.faint,
        modifier = Modifier.padding(
            start = Space.gutter,
            end = Space.gutter,
            top = Space.lg,
            bottom = Space.sm,
        ),
    )
}

@Composable
private fun ConnectionBanner(
    connection: ConnectionState,
    onReconnect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current

    when (connection) {
        ConnectionState.Disconnected -> return

        is ConnectionState.Connecting -> BannerRow(
            accent = edge.warning,
            label = "Connecting…",
        )

        is ConnectionState.Failed -> BannerRow(
            accent = scheme.error,
            label = connection.message,
            onReconnect = onReconnect,
        )

        is ConnectionState.Connected -> BannerRow(
            accent = if (connection.info.healthy) edge.success else scheme.error,
            label = connection.info.label,
            isSampleData = connection.info.api.name == "Demo",
        )
    }
}

@Composable
private fun BannerRow(
    accent: androidx.compose.ui.graphics.Color,
    label: String,
    isSampleData: Boolean = false,
    onReconnect: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        StatusDot(accent)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (isSampleData) {
            Chip(text = "Sample data", icon = EdgeIcons.Info, accent = edge.info)
        }
        Spacer(Modifier.weight(1f))
        if (onReconnect != null) {
            IconAction(
                icon = EdgeIcons.Refresh,
                contentDescription = "Reconnect",
                onClick = onReconnect,
            )
        }
    }
    HorizontalDivider(color = scheme.outlineVariant)
}

@Composable
private fun SessionRow(
    session: SessionSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    var menu by remember { mutableStateOf(false) }

    // Status is carried by the title's weight of colour rather than by a dot:
    // a dot per row is a column of noise when almost every row is idle.
    val titleColor = when (session.status) {
        SessionStatus.Working -> scheme.primary
        SessionStatus.Error -> scheme.error
        SessionStatus.Idle -> scheme.onSurface
    }

    // The leaf directory, not the full path: the list is scanned by title, and a
    // 26-character absolute path crowds out the model and token figures. The
    // full path is shown in the session's own overflow menu.
    val meta = buildList {
        add(session.directory.trimEnd('/').substringAfterLast('/'))
        session.modelLabel?.let { add(it) }
        if (session.tokens.total > 0) add("${formatTokens(session.tokens.total)} tok")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable(onClick = onOpen)
            .padding(start = Space.gutter, end = Space.xxs, top = Space.md, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Only the metadata is weighted. Two weighted children would
                // split the row evenly and truncate the model and token figures
                // to make room for a timestamp that needs a fraction of them.
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = edge.faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = formatRelative(session.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = edge.faint,
                    maxLines = 1,
                )
            }
        }

        Box {
            IconAction(
                icon = EdgeIcons.More,
                contentDescription = "Session actions",
                onClick = { menu = true },
                size = Size.touchSm,
                iconSize = Size.iconSm,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menu = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = scheme.error) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSessionSheet(
    directories: List<String>,
    onDismiss: () -> Unit,
    onCreate: (String?, String?) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var directory by remember { mutableStateOf(directories.firstOrNull()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surface,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.padding(bottom = Space.xl)) {
            dev.edgecode.core.design.components.SheetHeader(
                title = "New session",
                subtitle = "Choose where the agent should work",
                onClose = onDismiss,
            )

            EdgeTextField(
                value = title,
                onValueChange = { title = it },
                label = "Title",
                placeholder = "Optional — the server will name it",
                imeAction = ImeAction.Done,
                modifier = Modifier.padding(horizontal = Space.gutter),
            )

            Section(title = "Directory") {
                directories.forEach { path ->
                    SettingsRow(
                        title = path.substringAfterLast('/').ifBlank { path },
                        subtitle = shortenPath(path, 52),
                        icon = EdgeIcons.Folder,
                        onClick = { directory = path },
                        trailing = {
                            if (directory == path) {
                                Icon(
                                    imageVector = EdgeIcons.Check,
                                    contentDescription = "Selected",
                                    tint = scheme.primary,
                                    modifier = Modifier.size(Size.iconSm),
                                )
                            }
                        },
                    )
                }
                if (directories.isEmpty()) {
                    Text(
                        text = "No projects reported by the server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = edge.faint,
                        modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.md),
                    )
                }
            }

            Spacer(Modifier.height(Space.lg))

            EdgeButton(
                text = "Create session",
                icon = EdgeIcons.Plus,
                onClick = { onCreate(directory, title.ifBlank { null }) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter),
            )
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        title = { Text("Rename session", style = MaterialTheme.typography.titleLarge) },
        text = {
            EdgeTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = "Session title",
                imeAction = ImeAction.Done,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
