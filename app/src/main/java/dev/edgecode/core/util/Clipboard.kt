package dev.edgecode.core.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Copies text and confirms it. Uses the platform clipboard rather than the
 * Compose wrapper so it behaves identically on every API level we support.
 */
@Composable
fun rememberCopyAction(): (String, String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { label, text ->
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText(label, text))
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        }
    }
}
