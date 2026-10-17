package dev.edgecode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import dev.edgecode.core.util.formatTokens

/**
 * Model picker.
 *
 * Grouped by provider and showing context window and price side by side, because
 * choosing a model is a cost-and-capability decision, not a name lookup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    models: List<ModelInfo>,
    selected: ModelRef?,
    onSelect: (ModelInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val filtered = remember(models, query) {
        if (query.isBlank()) {
            models
        } else {
            models.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.providerName.contains(query, ignoreCase = true)
            }
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.providerName } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surface,
        dragHandle = null,
    ) {
        SheetHeader(
            title = "Model",
            subtitle = "${models.size} available on this server",
            onClose = onDismiss,
        )

        EdgeTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Filter models",
            leadingIcon = EdgeIcons.Search,
            modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.sm),
        )

        LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
            grouped.forEach { (provider, providerModels) ->
                item(key = "header-$provider") {
                    Text(
                        text = provider.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = edge.faint,
                        modifier = Modifier.padding(
                            start = Space.gutter,
                            top = Space.lg,
                            bottom = Space.sm,
                        ),
                    )
                }
                items(providerModels, key = { it.providerId + "/" + it.id }) { model ->
                    ModelRow(
                        model = model,
                        isSelected = selected?.let { it.providerId == model.providerId && it.modelId == model.id }
                            ?: model.isDefault,
                        onSelect = { onSelect(model) },
                    )
                }
            }
            item { Spacer(Modifier.height(Space.xxl)) }
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable(onClick = onSelect)
            .padding(horizontal = Space.gutter, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (model.isDefault) {
                    Chip(text = "Default", accent = scheme.primary)
                }
            }
            Spacer(Modifier.height(Space.xxs))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text(
                    text = "${formatTokens(model.contextLimit)} context",
                    style = MaterialTheme.typography.labelSmall,
                    color = edge.faint,
                )
                if (model.inputCostPerM > 0 || model.outputCostPerM > 0) {
                    Text(text = "·", style = MaterialTheme.typography.labelSmall, color = edge.faint)
                    Text(
                        text = "$${trim(model.inputCostPerM)} / $${trim(model.outputCostPerM)} per M",
                        style = MaterialTheme.typography.labelSmall,
                        color = edge.faint,
                    )
                }
            }
            Spacer(Modifier.height(Space.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                if (model.capabilities.reasoning) {
                    CapabilityTag("thinking")
                }
                if (model.capabilities.vision) {
                    CapabilityTag("vision")
                }
                if (model.capabilities.attachments) {
                    CapabilityTag("files")
                }
                if (model.variants.size > 1) {
                    CapabilityTag("${model.variants.size} variants")
                }
            }
        }
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

@Composable
private fun CapabilityTag(text: String) {
    val edge = LocalEdgeColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = edge.faint,
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.xs))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = Space.xs + 1.dp, vertical = 2.dp),
    )
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
        containerColor = scheme.surface,
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
                        .tappable { onSelect(agent) }
                        .padding(horizontal = Space.gutter, vertical = Space.md),
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
                if (index < agents.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = Space.gutter),
                        color = scheme.outlineVariant,
                    )
                }
            }
        }
    }
}

private fun trim(value: Double): String {
    val rounded = Math.round(value * 100) / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
