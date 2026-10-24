package dev.edgecode

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.toArgb
import dev.edgecode.core.design.EdgeTheme
import dev.edgecode.core.design.paletteFor
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
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private lateinit var notifications: Notifications

    /** A session the user asked for by tapping a notification. */
    private val deepLink = MutableStateFlow<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink.value = intent?.getStringExtra(NOTIFICATION_SESSION_ID)

        val settingsStore = SettingsStore(applicationContext)
        val connectionsStore = ConnectionsStore(applicationContext)
        notifications = Notifications(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Paint the right background before the first Compose frame. Without
        // this a light-theme start flashes the dark window background and vice
        // versa, because the window background comes from the XML theme.
        val startSettings = settingsStore.state.value
        val startDark = startSettings.themeMode.isDark(this)
        window.setBackgroundDrawable(
            ColorDrawable(
                paletteFor(startSettings.themeFamily, startDark).background.toArgb(),
            ),
        )

        setContent {
            val viewModel: AppViewModel = viewModel(
                factory = AppViewModelFactory(settingsStore, connectionsStore),
            )
            val settings by viewModel.settings_.collectAsState()
            val link by deepLink.collectAsState()

            LaunchedEffect(link) {
                link?.let {
                    viewModel.openFromNotification(it)
                    deepLink.value = null
                }
            }

            EdgeTheme(
                themeMode = settings.themeMode,
                themeFamily = settings.themeFamily,
            ) {
                EdgeApp(viewModel, notifications)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink.value = intent.getStringExtra(NOTIFICATION_SESSION_ID)
    }

    override fun onStart() {
        super.onStart()
        notifications.setForeground(true)
    }

    override fun onStop() {
        notifications.setForeground(false)
        super.onStop()
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
private fun EdgeApp(viewModel: AppViewModel, notifications: Notifications) {
    val stack by viewModel.stack.collectAsState()
    val current = stack.lastOrNull() ?: Screen.Connect

    NotificationWatcher(viewModel, notifications)

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

/**
 * Posts a notification for a blocking prompt only while the activity is
 * stopped. The flow collectors stay alive in the background, which is exactly
 * the window where an SSE-delivered question or permission would otherwise go
 * unseen.
 */
@Composable
private fun NotificationWatcher(viewModel: AppViewModel, notifications: Notifications) {
    val questions by viewModel.questions.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val subagents by viewModel.subagents.collectAsState()
    val foreground by notifications.foreground.collectAsState()
    val allSessions = remember(sessions, subagents) { sessions + subagents }

    LaunchedEffect(foreground, questions, allSessions) {
        notifications.syncQuestions(questions, allSessions)
    }
    LaunchedEffect(foreground, permissions, allSessions) {
        notifications.syncPermissions(permissions, allSessions)
    }
}
