package dev.edgecode.core.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Standard tap target behaviour. Named so intent is obvious at the call site
 * and so press feedback can be changed in one place.
 */
fun Modifier.tappable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.then(
    if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
)

/**
 * The screen's top inset. Belongs on the app bar, so list content can scroll
 * beneath the status bar rather than being boxed in by it.
 */
fun Modifier.topSystemInset(): Modifier = statusBarsPadding()

/**
 * The screen's bottom inset — the one place it is applied.
 *
 * Union rather than sum: while the keyboard is open the IME inset already
 * extends past the navigation bar, so padding for both pushed the composer up
 * off the bottom of the window and left it floating mid-screen. Anything that
 * needs to sit above the keyboard uses this and nothing else.
 */
@Composable
fun Modifier.bottomSystemInset(): Modifier =
    windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
