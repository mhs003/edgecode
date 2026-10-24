package dev.edgecode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.Stroke
import dev.edgecode.core.design.components.ChoiceRow
import dev.edgecode.core.design.components.EdgeButton
import dev.edgecode.core.design.components.EdgeTextField
import dev.edgecode.core.design.components.SheetHeader
import dev.edgecode.core.design.components.TextAction
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.model.QuestionAnswerDraft
import dev.edgecode.core.model.QuestionDraft
import dev.edgecode.core.model.QuestionInfo
import dev.edgecode.core.model.QuestionRequest

const val QuestionCardTestTag = "question-card"
const val QuestionSheetTestTag = "question-sheet"

/**
 * The standing signal that the agent is blocked.
 *
 * Pinned above the composer like [PermissionCard]: a question stops the turn,
 * so it must not be possible to scroll past it. The card stays compact and the
 * full form opens on tap — a multi-question form is too tall to live inline.
 */
@Composable
fun QuestionCard(
    request: QuestionRequest,
    onAnswer: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val first = request.questions.firstOrNull() ?: return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.sm)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.card))
            .background(scheme.surfaceContainerLow)
            .border(
                Stroke.hairline,
                scheme.outlineVariant,
                androidx.compose.foundation.shape.RoundedCornerShape(Radius.card),
            )
            .testTag(QuestionCardTestTag)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Icon(
                imageVector = EdgeIcons.Info,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(Size.iconSm),
            )
            Text(
                text = if (request.questions.size == 1) {
                    "Question"
                } else {
                    "${request.questions.size} questions"
                },
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Waiting",
                style = MaterialTheme.typography.labelSmall,
                color = edge.faint,
            )
        }

        Text(
            text = first.header,
            style = MaterialTheme.typography.labelSmall,
            color = edge.faint,
        )
        Text(
            text = first.question,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            TextAction(
                text = "Skip",
                color = scheme.error,
                onClick = onSkip,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Answer",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onPrimary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(scheme.primary)
                    .tappable(onClick = onAnswer)
                    .padding(horizontal = Space.lg, vertical = Space.sm),
            )
        }
    }
}

/**
 * The full form for a question request.
 *
 * Every question is answered before the send action is enabled, because the
 * server expects answers in the same order as the questions. A multi-select
 * answer can combine option labels with the free-text field; a single-select
 * answer is either one option or the typed answer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionSheet(
    request: QuestionRequest,
    busy: Boolean,
    error: String?,
    onAnswer: (List<List<String>>) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(request.id) {
        mutableStateOf(QuestionDraft.forQuestions(request.questions))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding()
                .testTag(QuestionSheetTestTag),
        ) {
            SheetHeader(
                title = if (request.questions.size == 1) "Question" else "Questions",
                subtitle = "The agent is waiting for an answer",
                onClose = onDismiss,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.gutter),
                verticalArrangement = Arrangement.spacedBy(Space.xl),
            ) {
                request.questions.forEachIndexed { index, question ->
                    QuestionBlock(
                        question = question,
                        draft = draft.at(index),
                        onSelect = { label -> draft = draft.select(index, question, label) },
                        onCustom = { text -> draft = draft.type(index, question, text) },
                    )
                }
                Spacer(Modifier.heightIn(min = Space.xl))
            }

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                TextAction(
                    text = "Skip",
                    color = scheme.error,
                    onClick = onSkip,
                )
                Spacer(Modifier.weight(1f))
                EdgeButton(
                    text = if (busy) "Sending" else "Send answers",
                    onClick = { onAnswer(draft.toAnswers(request.questions)) },
                    enabled = !busy && draft.isComplete(request.questions),
                    loading = busy,
                )
            }
        }
    }
}

@Composable
private fun QuestionBlock(
    question: QuestionInfo,
    draft: QuestionAnswerDraft,
    onSelect: (String) -> Unit,
    onCustom: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current

    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(
            text = question.header.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = edge.faint,
        )
        Text(
            text = question.question,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
        )
        if (question.options.isNotEmpty()) {
            Text(
                text = if (question.multiple) "Select one or more" else "Select one",
                style = MaterialTheme.typography.bodySmall,
                color = edge.faint,
            )
            question.options.forEach { option ->
                ChoiceRow(
                    label = option.label,
                    description = option.description,
                    selected = option.label in draft.selected,
                    multi = question.multiple,
                    onClick = { onSelect(option.label) },
                )
            }
        }

        if (question.custom) {
            EdgeTextField(
                value = draft.custom,
                onValueChange = onCustom,
                label = if (question.options.isEmpty()) "Your answer" else "Something else",
                placeholder = "Type your answer",
                singleLine = false,
                minHeight = 48.dp,
                imeAction = ImeAction.Default,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }

        if (question.options.isEmpty() && !question.custom) {
            Text(
                text = "No choices were offered.",
                style = MaterialTheme.typography.bodySmall,
                color = edge.faint,
            )
        }
    }
}
