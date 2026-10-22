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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.components.Chip
import dev.edgecode.core.design.components.CircleButton
import dev.edgecode.core.design.components.EmptyState
import dev.edgecode.core.design.components.ErrorNotice
import dev.edgecode.core.design.components.InlineProgress
import dev.edgecode.core.design.components.SheetHeader
import dev.edgecode.core.design.components.bottomSystemInset
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.design.components.topSystemInset
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.AttachmentKind
import dev.edgecode.core.model.ChatMessage
import dev.edgecode.core.model.ModelRef
import dev.edgecode.core.model.Part
import dev.edgecode.core.model.SessionStatus
import dev.edgecode.core.model.SessionSummary
import dev.edgecode.core.util.formatCost
import dev.edgecode.core.util.formatTokens
import dev.edgecode.core.util.queryFileMetadata
import dev.edgecode.ui.AppViewModel
import dev.edgecode.ui.chat.AgentPickerSheet
import dev.edgecode.ui.chat.Composer
import dev.edgecode.ui.chat.MessageRow
import dev.edgecode.ui.chat.ModelPickerSheet
import dev.edgecode.ui.chat.PermissionCard
import dev.edgecode.ui.chat.WorkingIndicator
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
 * Top-anchored, like the reference clients: the first turn sits under the
 * header and the conversation grows downward. The view follows the newest
 * content only while the reader is at (or returning to) the end.
 */
@Composable
fun ChatScreen(
    sessionId: String,
    viewModel: AppViewModel,
    readOnly: Boolean = false,
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
    val allSubagents by viewModel.subagents.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val settings by viewModel.settings_.collectAsState()
    val selection by viewModel.modelSelection.collectAsState()
    // Resolved per chat: what was picked here but not sent yet, else what this
    // chat last sent with, else what was last used anywhere. Without the middle
    // layer a model picked in one chat showed up in every other.
    val selectedModel = selection.forSession(sessionId)
    val selectedAgent by viewModel.selectedAgent.collectAsState()

    var draft by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var showModels by remember { mutableStateOf(false) }
    var showAgents by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showSubagents by remember { mutableStateOf(false) }

    // Measured, not assumed: the composer grows with the prompt, and the
    // transcript has to reserve exactly as much room as it currently occupies
    // so the last turn is never trapped behind it.
    var composerHeight by remember { mutableStateOf(0) }
    val composerHeightDp = with(LocalDensity.current) { composerHeight.toDp() }

    // A subagent is a branch of this conversation rather than a chat of its
    // own, so it never appears in the session list and is read from here.
    val subagents = remember(allSubagents, sessionId) {
        allSubagents.filter { it.parentId == sessionId }
    }
    val activeSubagents = subagents.count { it.status == SessionStatus.Working }

    val session = remember(sessions, subagents, sessionId) {
        sessions.firstOrNull { it.id == sessionId } ?: subagents.firstOrNull { it.id == sessionId }
    }
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
    // The local flag covers the gap between sending and the first event; the
    // session status covers everything else, including a chat opened while it
    // is already running and a subagent opened read-only.
    val working = state.isWorking || session?.status == SessionStatus.Working
    // While a reply streams, its message knows the model that actually served
    // it. Before the first token there is no such message, so fall back to the
    // chat's own model — which sending has just committed.
    val workingModel = catalog.model(
        (messages.lastOrNull() as? ChatMessage.Assistant)?.takeIf { it.streaming }?.model,
    ) ?: catalog.model(selectedModel)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }

    // Whether the view should follow the newest content.
    //
    // This cannot be read from `atBottom`: appending a turn makes the list
    // scrollable, so `canScrollForward` turns true before this effect observes
    // it and an at-bottom test never passes after sending. What matters is the
    // reader's intent, which only a scroll gesture changes — and a programmatic
    // scroll to the end always lands at the end, so re-arming from it is a
    // no-op rather than a feedback loop.
    val followEnd = remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) followEnd.value = !listState.canScrollForward
            }
    }

    // How much text the newest reply currently holds. Used as a scroll trigger
    // so the view follows the reply as it streams, not only when a turn lands.
    val streamingGrowth = messages.lastOrNull()
        ?.let { last -> (last as? ChatMessage.Assistant)?.parts }
        ?.sumOf { (it as? Part.Text)?.text?.length ?: 0 }
        ?: 0

    LaunchedEffect(messages.size, streamingGrowth, working) {
        // Do not yank the list while a gesture or fling is in flight: a reader
        // scrolling away mid-stream must win over the next token. Following is
        // re-armed when that scroll settles at the end.
        if (messages.isNotEmpty() && followEnd.value && !listState.isScrollInProgress) {
            // The indicator is one past the last message, so the newest turn and
            // the fact that it is still being worked on arrive together.
            listState.scrollToEnd(if (working) messages.size else messages.lastIndex)
        }
    }

    // Opening a session should land on the newest turn, not the first.
    LaunchedEffect(sessionId, messages.isNotEmpty()) {
        if (messages.isNotEmpty()) listState.scrollToEnd(messages.lastIndex)
    }

    // The header floats, so the list has to reserve its full height — including
    // the status-bar inset it also absorbs. Reserving only the control row left
    // the first turn underneath the buttons at the top of the transcript.
    val headerInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

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

                // A command can start a turn before any message exists, so
                // "working" keeps the transcript — and the indicator — on screen.
                messages.isEmpty() && !working -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        title = "Nothing here yet",
                        message = if (readOnly) {
                            "This subagent has not produced any messages."
                        } else {
                            "Ask for a change, or open a command with /."
                        },
                        icon = if (readOnly) EdgeIcons.Branch else EdgeIcons.Terminal,
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // Clears the floating header so the first turn is never
                    // trapped underneath it.
                    contentPadding = PaddingValues(
                        top = headerInset + HeaderHeight,
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
                            // The wire carries the model as provider/id; the
                            // catalogue is the only place a display name lives.
                            modelLabel = (message as? ChatMessage.Assistant)
                                ?.model
                                ?.let { catalog.model(it)?.name },
                            onOpenSubagent = { childId -> viewModel.openSubagent(childId) },
                        )
                    }

                    // Part of the transcript, not an overlay: it sits past the
                    // last turn and scrolls away with it, so it never covers
                    // text and never competes with the composer.
                    if (working) {
                        item(key = "working") {
                            WorkingIndicator(
                                model = workingModel?.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Space.lg, vertical = Space.md)
                                    .widthIn(max = Space.measure),
                            )
                        }
                    }
                }
            }

            // Fade behind the header: opaque at the top edge, dissolving to
            // nothing where the transcript begins. Text passing under the
            // controls fades out rather than colliding with them.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(headerInset + HeaderHeight + Space.lg)
                    .background(
                        Brush.verticalGradient(
                            0.00f to scheme.background,
                            0.55f to scheme.background.copy(alpha = 0.88f),
                            0.80f to scheme.background.copy(alpha = 0.45f),
                            1.00f to Color.Transparent,
                        ),
                    ),
            )

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

                if (readOnly) {
                    Chip(text = "Subagent", icon = EdgeIcons.Branch)
                } else {
                    // Only while one is running: settled ones are reachable from
                    // the call that spawned them, and a permanent count would be
                    // noise in a header that is otherwise source-of-truth free.
                    if (activeSubagents > 0) {
                        SubagentChip(
                            count = activeSubagents,
                            onClick = { showSubagents = true },
                        )
                        Spacer(Modifier.width(Space.sm))
                    }

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
            }

            ScrollToLatest(
                visible = !atBottom && messages.isNotEmpty(),
                onClick = { scope.launch { listState.scrollToEnd(messages.lastIndex) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Space.lg, bottom = composerHeightDp + Space.md),
            )
        }

        // Fade behind the composer, mirrored: nothing where the transcript is,
        // solid canvas at the window edge. The pill itself keeps its own fill
        // and stays fully transparent around it.
        if (!readOnly) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(composerHeightDp + Space.lg)
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.22f to scheme.background.copy(alpha = 0.45f),
                            0.45f to scheme.background.copy(alpha = 0.88f),
                            1.00f to scheme.background,
                        ),
                    ),
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

        // A subagent is driven by the agent that spawned it; a prompt typed here
        // would sit outside that conversation, so it has no composer at all.
        if (!readOnly) {
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
                modelLabel = viewModel.currentModelLabel(sessionId),
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
    }

    if (showModels) {
        ModelPickerSheet(
            models = catalog.usableModels,
            recent = selection.recent,
            selected = selectedModel,
            onSelect = { model ->
                viewModel.setModel(sessionId, ModelRef(model.providerId, model.id))
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

    if (showSubagents) {
        SubagentSheet(
            subagents = subagents,
            onOpen = { id ->
                showSubagents = false
                viewModel.openSubagent(id)
            },
            onDismiss = { showSubagents = false },
        )
    }
}

/** The header affordance for work running beside this chat. */
@Composable
private fun SubagentChip(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(Size.control)
            .tappable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Chip(
            text = if (count == 1) "1 subagent" else "$count subagents",
            icon = EdgeIcons.Branch,
            accent = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubagentSheet(
    subagents: List<SessionSummary>,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surface,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.padding(bottom = Space.xl)) {
            SheetHeader(
                title = "Subagents",
                subtitle = "Branches of this conversation",
                onClose = onDismiss,
            )
            // Running first: those are the ones worth looking at now.
            subagents.sortedByDescending { it.status == SessionStatus.Working }
                .forEach { subagent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tappable { onOpen(subagent.id) }
                            .padding(horizontal = Space.gutter, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = subagent.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                text = subagentStatusLine(subagent),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (subagent.status == SessionStatus.Working) {
                                    scheme.primary
                                } else {
                                    edge.faint
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = EdgeIcons.ChevronRight,
                            contentDescription = null,
                            tint = edge.faint,
                            modifier = Modifier.size(Size.iconSm),
                        )
                    }
                }
        }
    }
}

private fun subagentStatusLine(subagent: SessionSummary): String {
    val status = when (subagent.status) {
        SessionStatus.Working -> "Working"
        SessionStatus.Error -> "Failed"
        SessionStatus.Idle -> "Finished"
    }
    return listOfNotNull(status, subagent.modelLabel).joinToString(" · ")
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
