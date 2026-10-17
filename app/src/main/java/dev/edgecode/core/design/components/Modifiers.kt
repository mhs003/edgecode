package dev.edgecode.core.design.components

import androidx.compose.foundation.clickable
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
