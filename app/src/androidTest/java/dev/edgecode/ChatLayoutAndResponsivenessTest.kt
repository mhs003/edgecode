package dev.edgecode

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.edgecode.ui.chat.ComposerTestTag
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
    }

    private fun connectToDemo() {
        rule.onNodeWithText("Explore the demo").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Sessions").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openFirstSession() {
        connectToDemo()
        rule.onNodeWithText("Fix export button contrast in Settings").performClick()
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
            rule.onAllNodesWithText("Sessions").fetchSemanticsNodes().isNotEmpty()
        }
        val elapsed = SystemClock.uptimeMillis() - start

        println("PERF connect_to_session_list=${elapsed}ms")
        assertTrue(
            "Connecting took ${elapsed}ms; the button must produce a visible result quickly",
            elapsed < 1_200,
        )
    }

    @Test
    fun openingASessionShowsTheComposerPromptly() {
        connectToDemo()
        val row = rule.onNodeWithText("Fix export button contrast in Settings")
        row.assertIsDisplayed()

        val start = SystemClock.uptimeMillis()
        row.performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(ComposerTestTag).fetchSemanticsNodes().isNotEmpty()
        }
        val elapsed = SystemClock.uptimeMillis() - start

        println("PERF open_session=${elapsed}ms")
        assertTrue("Opening a session took ${elapsed}ms", elapsed < 800)
    }

    /**
     * The composer must own the bottom inset. If the root column also pads for
     * the navigation bar the composer stops short of the window bottom, which is
     * what produced the floating-composer behaviour.
     */
    @Test
    fun composerOccupiesTheBottomOfTheWindow() {
        openFirstSession()

        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val composer = rule.onNodeWithTag(ComposerTestTag).fetchSemanticsNode().boundsInRoot
        val transcript = rule.onNodeWithTag(TranscriptTestTag).fetchSemanticsNode().boundsInRoot

        println("PERF bounds root=$root transcript=$transcript composer=$composer")

        assertTrue(
            "Transcript should sit directly above the composer (gap ${transcript.bottom - composer.top})",
            abs(transcript.bottom - composer.top) < 2f,
        )
        assertTrue(
            "Composer bottom ${composer.bottom} should reach the window bottom ${root.bottom}",
            abs(composer.bottom - root.bottom) < 2f,
        )
        assertTrue(
            "Composer is only ${composer.height}px tall — its controls may be clipped",
            composer.height > 80f,
        )
    }

    /** The composer must never overlap the transcript viewport. */
    @Test
    fun transcriptDoesNotOverlapTheComposer() {
        openFirstSession()

        val composer = rule.onNodeWithTag(ComposerTestTag).fetchSemanticsNode().boundsInRoot
        val transcript = rule.onNodeWithTag(TranscriptTestTag).fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Transcript bottom (${transcript.bottom}) overlaps composer top (${composer.top})",
            transcript.bottom <= composer.top + 1f,
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

        rule.onNodeWithText("Ask for changes, or type / for commands").performClick()
        rule.waitForIdle()

        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val composer = rule.onNodeWithTag(ComposerTestTag).fetchSemanticsNode().boundsInRoot
        val transcript = rule.onNodeWithTag(TranscriptTestTag).fetchSemanticsNode().boundsInRoot

        println("PERF focused bounds root=$root transcript=$transcript composer=$composer")

        rule.onNodeWithContentDescription("Send").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add attachment").assertIsDisplayed()

        assertTrue(
            "Transcript (bottom ${transcript.bottom}) must not be covered by the composer (top ${composer.top})",
            transcript.bottom <= composer.top + 1f,
        )
        assertTrue(
            "Composer must not be pushed into the upper half of the window (top ${composer.top} of ${root.height})",
            composer.top > root.height / 3f,
        )
    }

    @Test
    fun lightThemeSelectsDarkSystemBarIcons() {
        connectToDemo()
        rule.onNodeWithContentDescription("Settings").performClick()
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
        rule.onNodeWithText("/home/dev/acme/dashboard").assertIsDisplayed()
        rule.onNodeWithText("ACTIVE").assertIsDisplayed()
        rule.onNodeWithText("RECENT").assertIsDisplayed()
    }
}
