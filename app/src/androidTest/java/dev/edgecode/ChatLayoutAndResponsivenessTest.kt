package dev.edgecode

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.edgecode.ui.chat.ComposerInputTestTag
import dev.edgecode.ui.chat.ComposerTestTag
import dev.edgecode.ui.chat.QuestionCardTestTag
import dev.edgecode.ui.chat.QuestionSheetTestTag
import dev.edgecode.ui.screens.TranscriptTestTag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Layout and responsiveness regression tests.
 *
 * Two defects drove these:
 *
 *  1. Navigating to the session list waited on a chain of serialised fake
 *     network calls, so the tap produced no visible change for well over a
 *     second. [connectingReachesTheSessionListPromptly] guards that.
 *  2. The chat screen applied the navigation-bar inset twice — once on the root
 *     column and once on the composer — so the composer floated above the real
 *     bottom of the window. [composerOccupiesTheBottomOfTheWindow] guards that.
 *
 * Every wait is condition-based; there are no fixed sleeps.
 */
@RunWith(AndroidJUnit4::class)
class ChatLayoutAndResponsivenessTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val settingsPrefs
        get() = rule.activity.getSharedPreferences("edgecode.settings", Context.MODE_PRIVATE)

    /** Start from a clean, disconnected state so the Connect screen is shown. */
    @Before
    fun startDisconnected() {
        settingsPrefs.edit().clear().commit()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        // A retained ViewModel can keep the previous connection alive across
        // recreate(). Walk back to the list and disconnect through the UI so
        // every test starts from the same screen. This is cleanup for the test
        // harness, not an assertion about the app.
        repeat(3) {
            if (rule.onAllNodesWithContentDescription("Back to sessions")
                    .fetchSemanticsNodes().isNotEmpty()
            ) {
                rule.onNodeWithContentDescription("Back to sessions").performClick()
                rule.waitForIdle()
            }
        }
        if (rule.onAllNodesWithText("EdgeCode").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithContentDescription("More").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithText("Disconnect").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("Disconnect").performClick()
        }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Explore the demo").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun connectToDemo() {
        rule.onNodeWithText("Explore the demo").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("EdgeCode").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(
                "Fix export button contrast in Settings",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openFirstSession() {
        connectToDemo()
        rule.onNodeWithText(
            "Fix export button contrast in Settings",
            useUnmergedTree = true,
        ).performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(ComposerTestTag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun connectingReachesTheSessionListPromptly() {
        rule.onNodeWithText("Explore the demo").assertIsDisplayed()

        val start = SystemClock.uptimeMillis()
        rule.onNodeWithText("Explore the demo").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("EdgeCode").fetchSemanticsNodes().isNotEmpty()
        }
        val elapsed = SystemClock.uptimeMillis() - start

        println("PERF connect_to_session_list=${elapsed}ms")
        assertTrue(
            "Connecting took ${elapsed}ms; the button must produce a visible result quickly",
            elapsed < 2_000,
        )
    }

    @Test
    fun openingASessionShowsTheComposerPromptly() {
        connectToDemo()
        val row = rule.onNodeWithText("Fix export button contrast in Settings", useUnmergedTree = true)
        row.assertIsDisplayed()

        val start = SystemClock.uptimeMillis()
        row.performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(ComposerTestTag).fetchSemanticsNodes().isNotEmpty()
        }
        val elapsed = SystemClock.uptimeMillis() - start

        println("PERF open_session=${elapsed}ms")
        assertTrue("Opening a session took ${elapsed}ms", elapsed < 2_000)
    }

    /**
     * The composer must own the bottom inset. If the root column also pads for
     * the navigation bar the composer stops short of the window bottom, which is
     * what produced the floating-composer behaviour.
     */
    @Test
    fun aQuestionCanBeAnsweredWithoutLeavingTheChat() {
        openFirstSession()
        // The Crossfade from the session list settles after the composer tag
        // first appears; typing before then targets a node that is about to be
        // replaced by the incoming chat.
        rule.waitForIdle()

        rule.onNodeWithTag(ComposerInputTestTag).performTextInput("ask me a question")
        rule.onNodeWithContentDescription("Send").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag(QuestionCardTestTag).fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithText("Answer").performTouchInput { click() }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag(QuestionSheetTestTag).fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithText("Phone").performClick()
        rule.onNodeWithText("Unit tests").performClick()
        rule.onNodeWithText("Send answers").performClick()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag(QuestionCardTestTag).fetchSemanticsNodes().isEmpty()
        }

        // The answered exchange must now be the question tool's output in the
        // transcript, and it must be on screen without another tap.
        rule.onNodeWithText("Phone").assertIsDisplayed()
        rule.onNodeWithText("Unit tests").assertIsDisplayed()
    }

    @Test
    fun composerOccupiesTheBottomOfTheWindow() {
        openFirstSession()

        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val composer = rule.onNodeWithTag(ComposerTestTag).fetchSemanticsNode().boundsInRoot
        val transcript = rule.onNodeWithTag(TranscriptTestTag).fetchSemanticsNode().boundsInRoot

        println("PERF bounds root=$root transcript=$transcript composer=$composer")

        assertTrue(
            "Composer must stay inside the transcript (${composer.bottom} vs ${transcript.bottom})",
            composer.bottom <= transcript.bottom + 1f,
        )
        assertTrue(
            "Composer should reach the content bottom, inside the navigation inset (${composer.bottom} vs ${root.bottom})",
            root.bottom - composer.bottom <= 64f,
        )
        assertTrue(
            "Composer is only ${composer.height}px tall — its controls may be clipped",
            composer.height > 80f,
        )
    }

    /** The floating composer sits inside the edge-to-edge transcript. */
    @Test
    fun transcriptRunsBehindTheFloatingComposer() {
        openFirstSession()

        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val composer = rule.onNodeWithTag(ComposerTestTag).fetchSemanticsNode().boundsInRoot
        val transcript = rule.onNodeWithTag(TranscriptTestTag).fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Transcript should fill the content area (${transcript.bottom} vs ${root.bottom})",
            root.bottom - transcript.bottom <= 64f,
        )
        assertTrue(
            "Composer should sit inside the transcript",
            composer.top < transcript.bottom,
        )
    }

    /** Icon-only controls must stay reachable and described. */
    @Test
    fun composerControlsAreReachable() {
        openFirstSession()
        rule.onNodeWithContentDescription("Send").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add attachment").assertIsDisplayed()
    }

    /**
     * Focusing the composer must not move the screen. If the window pans instead
     * of resizing, or the composer is padded for the same inset twice, it drifts
     * up into the middle of the display and the transcript can end up underneath
     * it.
     *
     * Note: the soft keyboard only appears if the test device has an IME
     * installed and enabled. Where it does not, this still guards the layout
     * contract but does not exercise the real inset. The on-device
     * keyboard-open screenshot is the definitive check.
     */
    @Test
    fun composerStaysAnchoredWhenTheInputIsFocused() {
        openFirstSession()

        rule.onNodeWithTag(ComposerInputTestTag).performClick()
        rule.waitForIdle()

        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val composer = rule.onNodeWithTag(ComposerTestTag).fetchSemanticsNode().boundsInRoot
        val transcript = rule.onNodeWithTag(TranscriptTestTag).fetchSemanticsNode().boundsInRoot

        println("PERF focused bounds root=$root transcript=$transcript composer=$composer")

        rule.onNodeWithContentDescription("Send").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add attachment").assertIsDisplayed()

        assertTrue(
            "Transcript should still fill the content area",
            root.bottom - transcript.bottom <= 64f,
        )
        assertTrue(
            "Composer must not be pushed into the upper half of the window (top ${composer.top} of ${root.height})",
            composer.top > root.height / 3f,
        )
    }

    @Test
    fun lightThemeSelectsDarkSystemBarIcons() {
        connectToDemo()
        rule.onNodeWithContentDescription("More").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Settings").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("APPEARANCE").fetchSemanticsNodes().isNotEmpty()
        }

        val controller = WindowInsetsControllerCompat(
            rule.activity.window,
            rule.activity.window.decorView,
        )

        rule.onNodeWithText("Light").performClick()
        rule.waitForIdle()
        assertTrue(
            "Light theme must request dark status-bar icons",
            controller.isAppearanceLightStatusBars,
        )
        assertTrue(
            "Light theme must request dark navigation-bar icons",
            controller.isAppearanceLightNavigationBars,
        )

        rule.onNodeWithText("Dark").performClick()
        rule.waitForIdle()
        assertFalse(
            "Dark theme must request light status-bar icons",
            controller.isAppearanceLightStatusBars,
        )

        // Switching back must not require recreating the activity.
        assertFalse(rule.activity.isFinishing)
    }

    @Test
    fun sessionListRendersLongTitlesAndMetadata() {
        connectToDemo()

        // The long title must survive, and its metadata must remain readable
        // rather than being dropped to make the row fit.
        rule.onNodeWithText("Fix export button contrast in Settings").assertIsDisplayed()
        rule.onNodeWithText("dashboard").assertIsDisplayed()
        rule.onNodeWithText("ACTIVE").assertIsDisplayed()
        rule.onNodeWithText("RECENT").assertIsDisplayed()
    }
}
