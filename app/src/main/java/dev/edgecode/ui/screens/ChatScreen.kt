package dev.edgecode.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.components.CircleButton
import dev.edgecode.core.design.components.EmptyState
import dev.edgecode.core.design.components.ErrorNotice
import dev.edgecode.core.design.components.InlineProgress
import dev.edgecode.core.design.components.bottomSystemInset
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.design.components.topSystemInset
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.AttachmentKind
import dev.edgecode.core.model.ChatMessage
import dev.edgecode.core.model.ModelRef
import dev.edgecode.core.model.Part
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

/** Test tag for the transcript list, used by the scrolling regression tests. */
const val TranscriptTestTag = "transcript"

/** Height of the floating header's control row, excluding the status-bar inset. */
private val HeaderHeight = 60.dp

/**
 * Jumps to the very end of the transcript.
 *
 * `scrollToItem` alone lands on the *top* of the final item, which is wrong
 * whenever that item is taller than the viewport — a long reply, or a reply
 * mid-stream. Scrolling past it clamps at the true bottom instead, so the
 * newest line is what the reader sees.
 */
private suspend fun LazyListState.scrollToEnd(index: Int) {
    scrollToItem(index)
    scrollBy(100_000f)
}

/**
 * The transcript.
 *
 * There is no app bar. A row of circular controls floats over the top of the
 * transcript and the composer floats over the bottom, so the conversation runs
 * edge to edge between them and the window reads as one surface rather than a
 * stack of framed bands.
 *
 * Laid out with `reverseLayout` so the newest message is anchored to the bottom:
 * that keeps streaming content pinned without fighting the scroll position.
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

    // Held in a remember so the collector is not restarted if the repository
    // ever hands back a different instance.
    val chatFlow = remember(sessionId) { viewModel.chat(sessionId) }
    val state by chatFlow.collectAsState()
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

    // Measured, not assumed: the composer grows with the prompt, and the
    // transcript has to reserve exactly as much room as it currently occupies
    // so the last turn is never trapped behind it.
    var composerHeight by remember { mutableStateOf(0) }
    val composerHeightDp = with(LocalDensity.current) { composerHeight.toDp() }

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

    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }

    // How much text the newest reply currently holds. Used as a scroll trigger
    // so the view follows the reply as it streams, not only when a turn lands.
    val streamingGrowth = messages.lastOrNull()
        ?.let { last -> (last as? ChatMessage.Assistant)?.parts }
        ?.sumOf { (it as? Part.Text)?.text?.length ?: 0 }
        ?: 0

    // Top-anchored, so the transcript only needs steering while the reader is
    // already at the end. Scrolling away is respected.
    LaunchedEffect(messages.size, streamingGrowth) {
        if (messages.isNotEmpty() && atBottom) listState.scrollToEnd(messages.lastIndex)
    }

    // Opening a session should land on the newest turn, not the first.
    LaunchedEffect(sessionId, messages.isNotEmpty()) {
        if (messages.isNotEmpty()) listState.scrollToEnd(messages.lastIndex)
    }

    // Context pressure belongs to the model that actually served the session.
    // Dividing by the currently selected model reported 146% for a GPT-6
    // session, because it was being divided by Opus's 200k window.
    val servingModel = catalog.model(state.lastAssistant?.model)
        ?: catalog.model(selectedModel)
        ?: catalog.defaultModel
    val contextUsed = session?.tokens?.contextUsed ?: 0
    val contextLimit = servingModel?.contextLimit ?: 0
    val contextPercent = if (contextLimit > 0) contextUsed.toFloat() / contextLimit else 0f

    // One Box, no bands. The transcript fills the whole surface and both bars
    // float over it, so the conversation runs edge to edge and stays visible in
    // the gaps beside the controls and around the composer.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            // The root owns the bottom inset once and only once, so the
            // transcript and the composer move together above the keyboard.
            .bottomSystemInset(),
    ) {
        Box(modifier = Modifier.fillMaxSize().testTag(TranscriptTestTag)) {
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

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // Clears the floating header so the first turn is never
                    // trapped underneath it.
                    contentPadding = PaddingValues(
                        top = HeaderHeight,
                        bottom = composerHeightDp + Space.md,
                    ),
                ) {
                    items(
                        items = messages,
                        key = { it.id },
                    ) { message ->
                        MessageRow(
                            message = message,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.lg, vertical = Space.md)
                                .widthIn(max = Space.measure),
                            showReasoning = settings.showReasoning,
                            expandTools = settings.expandTools,
                        )
                    }
                }
            }

            // Floating header. It draws nothing behind itself, so the chat body
            // shows between the controls; the controls keep their own fill and
            // stay legible over whatever passes beneath them. It is the only
            // owner of the top inset.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .topSystemInset()
                    .padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(
                    icon = EdgeIcons.Back,
                    contentDescription = "Back to sessions",
                    onClick = { viewModel.back() },
                )

                Spacer(Modifier.weight(1f))

                if (contextPercent > 0.8f) {
                    ContextChip(percent = contextPercent)
                    Spacer(Modifier.width(Space.sm))
                }

                CircleButton(
                    icon = EdgeIcons.Plus,
                    contentDescription = "New session",
                    onClick = { viewModel.back() },
                )
                Spacer(Modifier.width(Space.sm))
                Box {
                    CircleButton(
                        icon = EdgeIcons.More,
                        contentDescription = "Session menu",
                        onClick = { menuOpen = true },
                    )
                    SessionMenu(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        directory = session?.directory,
                        tokens = session?.tokens?.total ?: 0,
                        cost = session?.cost ?: 0.0,
                        additions = session?.additions ?: 0,
                        deletions = session?.deletions ?: 0,
                        contextPercent = contextPercent,
                        onNewSession = { menuOpen = false; viewModel.back() },
                        onSettings = { menuOpen = false; viewModel.openSettings() },
                        onDelete = { menuOpen = false; viewModel.deleteSession(sessionId) },
                    )
                }
            }

            ScrollToLatest(
                visible = !atBottom && messages.isNotEmpty(),
                onClick = { scope.launch { listState.scrollToEnd(messages.lastIndex) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Space.lg, bottom = composerHeightDp + Space.md),
            )
        }

        // The composer floats over the transcript. It reserves its measured
        // height in the list's bottom padding, so the last turn can always be
        // scrolled clear of it while everything else passes underneath.
        if (pending.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = composerHeightDp),
            ) {
                pending.take(1).forEach { request ->
                    PermissionCard(
                        request = request,
                        onDecide = { decision, remember ->
                            viewModel.respondPermission(request, decision, remember)
                        },
                    )
                }
            }
        }

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
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { composerHeight = it.height },
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
 * Context pressure, shown only once it is worth acting on. This is the one
 * session statistic that changes what you should do next, so it earns a place
 * in the header; cost and token totals live in the overflow menu.
 */
@Composable
private fun ContextChip(percent: Float) {
    val edge = LocalEdgeColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.full))
            .background(edge.warning.copy(alpha = 0.16f))
            .padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Icon(
            imageVector = EdgeIcons.Warning,
            contentDescription = null,
            tint = edge.warning,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = "${(percent * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = edge.warning,
        )
    }
}

/**
 * Session facts and session actions, kept out of the transcript. The statistics
 * describe the whole session, so they belong behind a deliberate tap rather than
 * repeated under every reply.
 */
@Composable
private fun SessionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    directory: String?,
    tokens: Int,
    cost: Double,
    additions: Int,
    deletions: Int,
    contextPercent: Float,
    onNewSession: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current

    val stats = buildList {
        if (tokens > 0) add("${formatTokens(tokens)} tok")
        if (cost > 0) add(formatCost(cost))
        if (contextPercent > 0f) add("${(contextPercent * 100).toInt()}% ctx")
        if (additions > 0) add("+$additions")
        if (deletions > 0) add("−$deletions")
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
            if (directory != null) {
                Text(
                    text = directory,
                    style = MaterialTheme.typography.labelSmall,
                    color = edge.faint,
                )
            }
            if (stats.isNotEmpty()) {
                Text(
                    text = stats.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)
        DropdownMenuItem(
            text = { Text("New session") },
            onClick = onNewSession,
        )
        DropdownMenuItem(
            text = { Text("Settings") },
            onClick = onSettings,
        )
        DropdownMenuItem(
            text = { Text("Delete session", color = scheme.error) },
            onClick = onDelete,
        )
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
                .size(Size.control)
                .clip(RoundedCornerShape(Radius.full))
                .background(MaterialTheme.colorScheme.surfaceContainer)
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
