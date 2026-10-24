package dev.edgecode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.components.Chip
import dev.edgecode.core.design.components.EdgeTextField
import dev.edgecode.core.design.components.SheetHeader
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.model.AgentInfo
import dev.edgecode.core.model.ModelInfo
import dev.edgecode.core.model.ModelRef
import dev.edgecode.core.util.Fuzzy
import dev.edgecode.core.util.formatTokens

/**
 * Model picker.
 *
 * Grouped by provider and showing context window and price side by side, because
 * choosing a model is a cost-and-capability decision, not a name lookup.
 *
 * The list is what the server says is usable, not its whole registry: that
 * registry carries every provider it knows about — thousands of models — and
 * only a few are connected. Offering the rest made the sheet slow to open and
 * offered choices that could only fail on send.
 *
 * Typing switches to a single ranked list rather than filtering a grouped one,
 * because relevance and grouping want different orders. Matching is fuzzy and
 * spans the model name, its provider and its id, so "glm commandcode" and
 * "commandcode glm" both find the same row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    models: List<ModelInfo>,
    recent: List<ModelRef>,
    selected: ModelRef?,
    onSelect: (ModelInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val byKey = remember(models) { models.associateBy { it.key } }
    val recentModels = remember(recent, byKey) { recent.mapNotNull { byKey[it.key] } }
    val grouped = remember(models) { models.groupBy { it.providerName } }
    val matches = remember(models, query) {
        if (query.isBlank()) {
            emptyList()
        } else {
            Fuzzy.rank(query, models) { listOf(it.name, it.providerName) }
        }
    }
    val selectedKey = selected?.key

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.background,
        dragHandle = null,
    ) {
        SheetHeader(
            title = "Model",
            subtitle = "${models.size} models on connected providers",
            onClose = onDismiss,
        )

        EdgeTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search models and providers",
            leadingIcon = EdgeIcons.Search,
            modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.sm),
        )

        LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
            if (query.isNotBlank()) {
                if (matches.isEmpty()) {
                    item(key = "no-match") {
                        Text(
                            text = "No model matches \"$query\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = edge.faint,
                            modifier = Modifier.padding(
                                start = Space.gutter,
                                end = Space.gutter,
                                top = Space.lg,
                            ),
                        )
                    }
                }
                items(matches, key = { it.key }) { model ->
                    ModelRow(
                        model = model,
                        isSelected = model.key == selectedKey,
                        showProvider = true,
                        onSelect = { onSelect(model) },
                    )
                }
            } else {
                if (recentModels.isNotEmpty()) {
                    item(key = "recent-header") { PickerLabel("Recently used") }
                    items(recentModels, key = { "recent-" + it.key }) { model ->
                        ModelRow(
                            model = model,
                            isSelected = model.key == selectedKey,
                            showProvider = true,
                            onSelect = { onSelect(model) },
                        )
                    }
                }
                grouped.forEach { (provider, providerModels) ->
                    item(key = "header-$provider") { PickerLabel(provider) }
                    items(providerModels, key = { it.key }) { model ->
                        ModelRow(
                            model = model,
                            isSelected = model.key == selectedKey,
                            showProvider = false,
                            onSelect = { onSelect(model) },
                        )
                    }
                }
            }
            item(key = "tail") { Spacer(Modifier.height(Space.xxl)) }
        }
    }
}

private val ModelInfo.key: String get() = "$providerId/$id"

@Composable
private fun PickerLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalEdgeColors.current.faint,
        modifier = Modifier.padding(
            start = Space.gutter,
            top = Space.lg,
            bottom = Space.sm,
        ),
    )
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    isSelected: Boolean,
    showProvider: Boolean,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.sm)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.md))
            .background(if (isSelected) scheme.surfaceContainerHigh else Color.Transparent)
            .tappable(onClick = onSelect)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xxs))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                if (showProvider) {
                    Text(
                        text = model.providerName,
                        style = MaterialTheme.typography.labelSmall,
                        color = edge.faint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(text = "·", style = MaterialTheme.typography.labelSmall, color = edge.faint)
                }
                Text(
                    text = "${formatTokens(model.contextLimit)} context",
                    style = MaterialTheme.typography.labelSmall,
                    color = edge.faint,
                )
                if (model.inputCostPerM > 0 || model.outputCostPerM > 0) {
                    Text(text = "·", style = MaterialTheme.typography.labelSmall, color = edge.faint)
                    Text(
                        text = "$${trim(model.inputCostPerM)} / $${trim(model.outputCostPerM)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = edge.faint,
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (model.capabilities.reasoning) {
                Icon(
                    imageVector = EdgeIcons.Sparkle,
                    contentDescription = "Thinking",
                    tint = edge.faint,
                    modifier = Modifier.size(Size.iconSm),
                )
            }
            if (model.capabilities.vision) {
                Icon(
                    imageVector = EdgeIcons.Image,
                    contentDescription = "Image input",
                    tint = edge.faint,
                    modifier = Modifier.size(Size.iconSm),
                )
            }
            if (model.capabilities.attachments) {
                Icon(
                    imageVector = EdgeIcons.Attach,
                    contentDescription = "File input",
                    tint = edge.faint,
                    modifier = Modifier.size(Size.iconSm),
                )
            }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentPickerSheet(
    agents: List<AgentInfo>,
    selected: String,
    onSelect: (AgentInfo) -> Unit,
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
        SheetHeader(
            title = "Agent",
            subtitle = "How the model is allowed to work",
            onClose = onDismiss,
        )

        Column(modifier = Modifier.padding(bottom = Space.xxl)) {
            agents.forEachIndexed { index, agent ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.sm)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.md))
                        .background(
                            if (agent.id == selected) scheme.surfaceContainerHigh else Color.Transparent,
                        )
                        .tappable { onSelect(agent) }
                        .padding(horizontal = Space.md, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (agent.isPrimary) EdgeIcons.Bolt else EdgeIcons.Sparkle,
                        contentDescription = null,
                        tint = if (agent.id == selected) scheme.primary else edge.faint,
                        modifier = Modifier.size(Size.icon),
                    )
                    Spacer(Modifier.width(Space.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            Text(
                                text = agent.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurface,
                            )
                            if (!agent.isPrimary) Chip(text = "Subagent")
                        }
                        agent.description?.let { description ->
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = edge.faint,
                            )
                        }
                    }
                    if (agent.id == selected) {
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
    }
}

private fun trim(value: Double): String {
    val rounded = Math.round(value * 100) / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
