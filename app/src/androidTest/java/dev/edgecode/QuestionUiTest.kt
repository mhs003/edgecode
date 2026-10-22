package dev.edgecode

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.edgecode.core.design.EdgeTheme
import dev.edgecode.core.model.QuestionInfo
import dev.edgecode.core.model.QuestionOption
import dev.edgecode.core.model.QuestionRequest
import dev.edgecode.ui.chat.QuestionCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuestionUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val request = QuestionRequest(
        id = "que_1",
        sessionId = "ses_1",
        questions = listOf(
            QuestionInfo(
                question = "Which target?",
                header = "Target",
                options = listOf(QuestionOption("Phone", "Fast and thumb-friendly")),
                custom = true,
            ),
        ),
    )

    @Test
    fun theCardOffersBothActions() {
        var answered = false
        var skipped = false

        rule.setContent {
            EdgeTheme {
                QuestionCard(
                    request = request,
                    onAnswer = { answered = true },
                    onSkip = { skipped = true },
                )
            }
        }

        rule.onNodeWithText("Answer").performClick()
        assertTrue("Answer must be tappable", answered)

        rule.onNodeWithText("Skip").performClick()
        assertTrue("Skip must be tappable", skipped)
    }
}
