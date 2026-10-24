package dev.edgecode.core.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.edgecode.core.design.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies text and exposes a short-lived confirmation for the control that
 * triggered it. Uses the platform clipboard rather than the Compose wrapper so
 * it behaves identically on every API level we support.
 */
@Stable
class CopyAction internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var copied by mutableStateOf(false)
        private set

    private var reset: Job? = null

    operator fun invoke(label: String, text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText(label, text))
        copied = true
        reset?.cancel()
        reset = scope.launch {
            delay(Motion.confirmation)
            copied = false
        }
    }
}

@Composable
fun rememberCopyAction(): CopyAction {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember(context, scope) { CopyAction(context, scope) }
}
