package dev.edgecode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import dev.edgecode.core.design.components.EdgeMark
import dev.edgecode.core.design.components.BarIcon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.components.ActionSheet
import dev.edgecode.core.design.components.Chip
import dev.edgecode.core.design.components.EdgeButton
import dev.edgecode.core.design.components.SheetHeader
import dev.edgecode.core.design.components.ConfirmDialog
import dev.edgecode.core.design.components.EdgeTextField
import dev.edgecode.core.design.components.EdgeSearchBar
import dev.edgecode.core.design.components.EdgeTopBar
import dev.edgecode.core.design.components.StatusDot
import dev.edgecode.core.design.components.EmptyState
import dev.edgecode.core.design.components.IconAction
import dev.edgecode.core.design.components.InlineProgress
import dev.edgecode.core.design.components.RowDivider
import dev.edgecode.core.design.components.Section
import dev.edgecode.core.design.components.SheetAction
import dev.edgecode.core.design.components.SettingsRow
import dev.edgecode.core.design.components.StatusDot
import dev.edgecode.core.design.components.bottomSystemInset
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.design.components.topSystemInset
import dev.edgecode.core.model.ConnectionState
import dev.edgecode.core.model.SessionStatus
import dev.edgecode.core.model.ModelRef
import dev.edgecode.core.model.SessionSummary
import dev.edgecode.core.util.formatCost
import dev.edgecode.core.util.formatRelative
import dev.edgecode.core.util.shortenPath
import dev.edgecode.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val connection by viewModel.connection.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val sessionsLoading by viewModel.sessionsLoading.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val catalog by viewModel.catalog.collectAsState()

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SessionSummary?>(null) }
    var deleting by remember { mutableStateOf<SessionSummary?>(null) }
    var actionSession by remember { mutableStateOf<SessionSummary?>(null) }
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
    val questionSessions = remember(questions) { questions.map { it.sessionId }.toSet() }

    val badge = when (val current = connection) {
        is ConnectionState.Connected -> if (current.info.api.name == "Demo") "Demo" else "Server"
        is ConnectionState.Connecting -> "Connecting"
        is ConnectionState.Failed -> "Offline"
        ConnectionState.Disconnected -> null
    }

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
                title = "EdgeCode",
                badge = badge,
                leading = { EdgeMark() },
                actions = {
                    BarIcon(
                        icon = EdgeIcons.Search,
                        contentDescription = "Search sessions",
                        onClick = { searchOpen = true },
                    )
                    BarIcon(
                        icon = EdgeIcons.More,
                        contentDescription = "More",
                        onClick = { menuOpen = true },
                    )
                },
            )
        }

        ConnectionBanner(
            connection = connection,
            onReconnect = { viewModel.retryConnect() },
        )

        if (sessions.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (sessionsLoading) {
                    InlineProgress("Loading sessions")
                } else {
                    EmptyState(
                        title = "No sessions yet",
                        message = "Sessions live on the server. Start one here and it will " +
                            "appear in your terminal too.",
                        icon = EdgeIcons.Chat,
                        action = {
                            EdgeButton(
                                text = "New session",
                                icon = EdgeIcons.Plus,
                                onClick = { creating = true },
                            )
                        },
                    )
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 104.dp),
                ) {
                    if (active.isNotEmpty()) {
                        item { SectionTitle("Active") }
                        itemsIndexed(active, key = { _, s -> s.id }) { _, session ->
                            SessionRow(
                                session = session,
                                modelName = session.modelLabel?.let { raw ->
                                    ModelRef.parse(raw)?.let { catalog.model(it)?.name }
                                        ?: raw.substringAfterLast('/')
                                },
                                hasQuestion = session.id in questionSessions,
                                onOpen = { viewModel.openSession(session.id) },
                                onActions = { actionSession = session },
                            )
                        }
                    }
                    if (recent.isNotEmpty()) {
                        item { SectionTitle("Recent") }
                        itemsIndexed(recent, key = { _, s -> s.id }) { _, session ->
                            SessionRow(
                                session = session,
                                modelName = session.modelLabel?.let { raw ->
                                    ModelRef.parse(raw)?.let { catalog.model(it)?.name }
                                        ?: raw.substringAfterLast('/')
                                },
                                hasQuestion = session.id in questionSessions,
                                onOpen = { viewModel.openSession(session.id) },
                                onActions = { actionSession = session },
                            )
                        }
                    }
                }

                NewSessionPill(
                    onClick = { creating = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = Space.lg, bottom = Space.lg),
                )
            }
        }
    }

    if (menuOpen) {
        ActionSheet(
            title = (connection as? ConnectionState.Connected)?.profile?.url ?: "Local demo",
            subtitle = if (sessions.size == 1) "1 session" else "${sessions.size} sessions",
            onDismiss = { menuOpen = false },
            actions = listOf(
                SheetAction("New session", EdgeIcons.Plus) { creating = true },
                SheetAction("Refresh", EdgeIcons.Refresh) { viewModel.refresh() },
                SheetAction("Settings", EdgeIcons.Settings) { viewModel.openSettings() },
                SheetAction("Disconnect", EdgeIcons.Server, destructive = true) {
                    viewModel.disconnect()
                },
            ),
        )
    }

    actionSession?.let { session ->
        ActionSheet(
            title = "Session actions",
            subtitle = session.title,
            onDismiss = { actionSession = null },
            actions = listOf(
                SheetAction("Rename", EdgeIcons.Edit) { renaming = session },
                SheetAction("Delete", EdgeIcons.Trash, destructive = true) {
                    deleting = session
                },
            ),
        )
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
        RenameSheet(
            initial = session.title,
            onDismiss = { renaming = null },
            onConfirm = { title ->
                viewModel.renameSession(session.id, title)
                renaming = null
            },
        )
    }

    deleting?.let { session ->
        ConfirmDialog(
            title = "Delete session?",
            message = "\"${session.title}\" and its messages will be removed from the server. " +
                "This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { deleting = null },
            onConfirm = {
                viewModel.deleteSession(session.id)
                deleting = null
            },
        )
    }
}

@Composable
private fun NewSessionPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .background(scheme.surfaceContainer)
            .tappable(onClick = onClick)
            .padding(horizontal = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(
            imageVector = EdgeIcons.Plus,
            contentDescription = null,
            tint = scheme.onSurface,
            modifier = Modifier.size(Size.icon),
        )
        Text(
            text = "New session",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface,
        )
    }
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

        is ConnectionState.Connected -> {
            if (connection.info.api.name != "Demo") return
            BannerRow(
                accent = if (connection.info.healthy) edge.success else scheme.error,
                label = connection.info.label,
                isSampleData = true,
            )
        }
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
            .background(scheme.background)
            .padding(horizontal = Space.gutter, vertical = Space.xs),
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
            Chip(text = "Sample data", icon = EdgeIcons.Info)
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
}

@Composable
private fun SessionRow(
    session: SessionSummary,
    modelName: String?,
    hasQuestion: Boolean,
    onOpen: () -> Unit,
    onActions: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current

    val titleColor = when (session.status) {
        SessionStatus.Working -> scheme.onSurface
        SessionStatus.Error -> scheme.error
        SessionStatus.Idle -> scheme.onSurface
    }

    val meta = buildList {
        modelName?.let { add(it) }
        if (session.status == SessionStatus.Working) add("Working")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.xs)
            .clip(RoundedCornerShape(Radius.card))
            .background(scheme.surfaceContainerLow)
            .tappable(onClick = onOpen)
            .padding(start = Space.md, end = Space.xxs, top = Space.sm + 2.dp, bottom = Space.sm + 2.dp),
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
                imageVector = EdgeIcons.Chat,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(Size.icon),
            )
        }
        Spacer(Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (hasQuestion) {
                    Chip(text = "Question", icon = EdgeIcons.Info)
                }
            }
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = session.directory.trimEnd('/').substringAfterLast('/').ifBlank {
                    session.directory
                },
                style = MaterialTheme.typography.bodySmall,
                color = edge.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (session.status == SessionStatus.Working) {
                    StatusDot(scheme.onSurfaceVariant)
                    Spacer(Modifier.width(Space.xs))
                }
                Text(
                    text = meta.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (session.status == SessionStatus.Working) {
                        scheme.onSurfaceVariant
                    } else {
                        edge.faint
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatRelative(session.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = edge.faint,
                    maxLines = 1,
                )
            }
        }

        IconAction(
            icon = EdgeIcons.More,
            contentDescription = "Session actions",
            onClick = onActions,
            size = Size.touchSm,
            iconSize = Size.iconSm,
        )
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
        containerColor = scheme.background,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameSheet(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by remember { mutableStateOf(initial) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.background,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.padding(bottom = Space.xl)) {
            SheetHeader(
                title = "Rename session",
                subtitle = "A short title for the list",
                onClose = onDismiss,
            )
            EdgeTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = "Session title",
                imeAction = ImeAction.Done,
                modifier = Modifier.padding(horizontal = Space.gutter),
            )
            Spacer(Modifier.height(Space.lg))
            EdgeButton(
                text = "Save",
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter),
            )
        }
    }
}
