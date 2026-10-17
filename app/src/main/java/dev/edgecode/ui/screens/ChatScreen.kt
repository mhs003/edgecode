package dev.edgecode.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.EdgeText
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.components.EdgeTopBar
import dev.edgecode.core.design.components.EmptyState
import dev.edgecode.core.design.components.ErrorNotice
import dev.edgecode.core.design.components.IconAction
import dev.edgecode.core.design.components.InlineProgress
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.AttachmentKind
import dev.edgecode.core.model.ModelRef
import dev.edgecode.core.util.formatCost
import dev.edgecode.core.util.formatTokens
import dev.edgecode.core.util.queryFileMetadata
import dev.edgecode.ui.AppViewModel
import dev.edgecode.ui.chat.AgentPickerSheet
import dev.edgecode.ui.chat.Composer
import dev.edgecode.ui.chat.MessageRow
import dev.edgecode.ui.chat.ModelPickerSheet
import dev.edgecode.ui.chat.PermissionCard
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The transcript.
 *
 * Laid out with `reverseLayout` so the newest message is anchored to the bottom:
 * that keeps streaming content pinned without fighting the scroll position, and
 * makes "load older" a natural extension later.
 */
@Composable
fun ChatScreen(
    sessionId: String,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val context = LocalContext.current

    val state by viewModel.chat(sessionId).collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val settings by viewModel.settings_.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedAgent by viewModel.selectedAgent.collectAsState()

    var draft by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var showModels by remember { mutableStateOf(false) }
    var showAgents by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val session = remember(sessions, sessionId) { sessions.firstOrNull { it.id == sessionId } }
    val pending = remember(permissions, sessionId) {
        permissions.filter { it.sessionId == sessionId }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val meta = queryFileMetadata(context, uri.toString())
            if (meta != null) {
                val (name, mime, size) = meta
                val kind = when {
                    mime.startsWith("image/") -> AttachmentKind.Image
                    mime.startsWith("text/") -> AttachmentKind.Text
                    mime.startsWith("application/") -> AttachmentKind.Document
                    else -> AttachmentKind.Other
                }
                attachments = attachments + Attachment(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    mime = mime,
                    sizeBytes = size,
                    kind = kind,
                    uri = uri.toString(),
                )
            }
        }
    }

    val streaming = state.isStreaming
    val messages = state.messages
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount == 0 || listState.firstVisibleItemIndex <= 1
        }
    }

    // A new turn arrives: follow it only if the reader had not scrolled away.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && atBottom) listState.animateScrollToItem(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .systemBarsPadding(),
    ) {
        EdgeTopBar(
            title = session?.title ?: "Session",
            subtitle = session?.directory,
            onBack = { viewModel.back() },
            actions = {
                Box {
                    IconAction(
                        icon = EdgeIcons.More,
                        contentDescription = "Session menu",
                        onClick = { menuOpen = true },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("New session") },
                            onClick = { menuOpen = false; viewModel.back(); },
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = { menuOpen = false; viewModel.openSettings() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete session", color = scheme.error) },
                            onClick = { menuOpen = false; viewModel.deleteSession(sessionId) },
                        )
                    }
                }
            },
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.loading -> InlineProgress(
                    label = "Loading session",
                    modifier = Modifier.align(Alignment.Center),
                )

                state.error != null -> Box(
                    modifier = Modifier.align(Alignment.Center).padding(Space.xl),
                ) {
                    ErrorNotice(
                        message = state.error ?: "",
                        onRetry = { viewModel.openSession(sessionId) },
                    )
                }

                messages.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        title = "Nothing here yet",
                        message = "Ask for a change, or open a command with /.",
                        icon = EdgeIcons.Terminal,
                    )
                }

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    SessionMetaBar(
                        cost = session?.cost ?: state.lastAssistant?.cost ?: 0.0,
                        tokens = session?.tokens?.total ?: state.lastAssistant?.tokens?.total ?: 0,
                        contextUsed = session?.tokens?.contextUsed ?: 0,
                        contextLimit = catalog.model(selectedModel)?.contextLimit
                            ?: catalog.defaultModel?.contextLimit ?: 0,
                        additions = session?.additions ?: 0,
                        deletions = session?.deletions ?: 0,
                    )

                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(
                            top = Space.lg,
                            bottom = Space.lg,
                        ),
                    ) {
                        items(
                            items = messages.asReversed(),
                            key = { it.id },
                        ) { message ->
                            MessageRow(
                                message = message,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Space.gutter, vertical = Space.md)
                                    .widthIn(max = Space.measure),
                                showReasoning = settings.showReasoning,
                                expandTools = settings.expandTools,
                                modelName = { ref: ModelRef? -> catalog.model(ref)?.name },
                            )
                        }
                    }
                }
            }

            ScrollToLatest(
                visible = !atBottom && messages.isNotEmpty(),
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Space.lg, bottom = Space.lg),
            )
        }

        if (pending.isNotEmpty()) {
            pending.take(1).forEach { request ->
                PermissionCard(
                    request = request,
                    onDecide = { decision, remember ->
                        viewModel.respondPermission(request, decision, remember)
                    },
                )
            }
        }

        HorizontalDivider(color = scheme.outlineVariant)

        Composer(
            value = draft,
            onValueChange = { draft = it },
            attachments = attachments,
            onRemoveAttachment = { id -> attachments = attachments.filterNot { it.id == id } },
            onPickAttachment = { picker.launch("*/*") },
            onSend = {
                if (draft.isNotBlank() || attachments.isNotEmpty()) {
                    viewModel.send(sessionId, draft, attachments)
                    draft = ""
                    attachments = emptyList()
                }
            },
            onStop = { viewModel.abort(sessionId) },
            streaming = streaming,
            modelLabel = viewModel.currentModelLabel(),
            agentLabel = viewModel.agentInfo(selectedAgent)?.name ?: selectedAgent,
            onModelClick = { showModels = true },
            onAgentClick = { showAgents = true },
            commands = catalog.commands,
            onCommandSelected = { command ->
                draft = ""
                // Commands the client implements open a surface; the rest are
                // the server's to execute.
                when (command.name) {
                    "models" -> showModels = true
                    "agents" -> showAgents = true
                    "help" -> Unit
                    else -> viewModel.runCommand(sessionId, command.name, "")
                }
            },
            sendOnEnter = settings.sendOnEnter,
            modifier = Modifier.navigationBarsPadding().imePadding(),
        )
    }

    if (showModels) {
        ModelPickerSheet(
            models = catalog.models,
            selected = selectedModel,
            onSelect = { model ->
                viewModel.setModel(ModelRef(model.providerId, model.id))
                showModels = false
            },
            onDismiss = { showModels = false },
        )
    }

    if (showAgents) {
        AgentPickerSheet(
            agents = catalog.agents,
            selected = selectedAgent,
            onSelect = { agent ->
                viewModel.setAgent(agent.id)
                showAgents = false
            },
            onDismiss = { showAgents = false },
        )
    }
}

/**
 * Session meta: cost, tokens, context pressure and diff size. Sits above the
 * transcript because it describes the whole session, not any one message.
 */
@Composable
private fun SessionMetaBar(
    cost: Double,
    tokens: Int,
    contextUsed: Int,
    contextLimit: Int,
    additions: Int,
    deletions: Int,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val percent = if (contextLimit > 0) (contextUsed.toFloat() / contextLimit) else 0f

    val parts = buildList {
        if (tokens > 0) add("${formatTokens(tokens)} tok")
        if (contextLimit > 0) add("${(percent * 100).toInt()}% ctx")
        if (cost > 0) add(formatCost(cost))
        if (additions > 0) add("+$additions")
        if (deletions > 0) add("−$deletions")
    }
    if (parts.isEmpty()) return

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter, vertical = Space.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = parts.joinToString("  ·  "),
                    style = EdgeText.meta,
                    color = edge.faint,
                )
                Spacer(Modifier.weight(1f))
                if (contextLimit > 0 && percent > 0.8f) {
                    Text(
                        text = "context filling",
                        style = MaterialTheme.typography.labelSmall,
                        color = edge.warning,
                    )
                }
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)
    }
}

/**
 * Floating affordance shown only when the reader has scrolled away from the
 * newest message. Extracted so its animation resolves against the top-level
 * [AnimatedVisibility] rather than the transcript's column scope.
 */
@Composable
private fun ScrollToLatest(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radius.full))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .tappable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = EdgeIcons.ChevronDown,
                contentDescription = "Jump to latest",
                tint = LocalEdgeColors.current.faint,
                modifier = Modifier.size(Size.icon),
            )
        }
    }
}
