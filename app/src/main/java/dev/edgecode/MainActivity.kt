package dev.edgecode

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.edgecode.core.design.EdgeTheme
import dev.edgecode.core.design.Motion
import dev.edgecode.core.design.isDark
import dev.edgecode.data.ConnectionsStore
import dev.edgecode.data.SettingsStore
import dev.edgecode.ui.AppViewModel
import dev.edgecode.ui.AppViewModelFactory
import dev.edgecode.ui.Screen
import dev.edgecode.ui.screens.ChatScreen
import dev.edgecode.ui.screens.ConnectScreen
import dev.edgecode.ui.screens.SessionsScreen
import dev.edgecode.ui.screens.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsStore = SettingsStore(applicationContext)
        val connectionsStore = ConnectionsStore(applicationContext)

        // Paint the right background before the first Compose frame. Without
        // this a light-theme start flashes the dark window background and vice
        // versa, because the window background comes from the XML theme.
        val startDark = settingsStore.state.value.themeMode.isDark(this)
        window.setBackgroundDrawable(
            ColorDrawable(
                ContextCompat.getColor(
                    this,
                    if (startDark) R.color.window_background_dark
                    else R.color.window_background_light,
                ),
            ),
        )

        setContent {
            val viewModel: AppViewModel = viewModel(
                factory = AppViewModelFactory(settingsStore, connectionsStore),
            )
            val settings by viewModel.settings_.collectAsState()

            EdgeTheme(themeMode = settings.themeMode) {
                EdgeApp(viewModel)
            }
        }
    }
}

/**
 * Navigation host.
 *
 * A hand-rolled stack rather than a navigation graph: there are four
 * destinations, back behaviour is one line, and this keeps both the transitions
 * and the dependency surface under our control.
 */
@Composable
private fun EdgeApp(viewModel: AppViewModel) {
    val stack by viewModel.stack.collectAsState()
    val current = stack.lastOrNull() ?: Screen.Connect

    // Only intercept back when there is somewhere to return to.
    BackHandler(enabled = stack.size > 1) { viewModel.back() }

    Crossfade(
        targetState = current,
        animationSpec = tween(Motion.base, easing = Motion.standard),
        label = "screen",
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) { screen ->
        when (screen) {
            Screen.Connect -> ConnectScreen(viewModel)
            Screen.Sessions -> SessionsScreen(viewModel)
            Screen.Settings -> SettingsScreen(viewModel)
            is Screen.Chat -> ChatScreen(
                sessionId = screen.sessionId,
                readOnly = screen.readOnly,
                viewModel = viewModel,
            )
        }
    }
}
