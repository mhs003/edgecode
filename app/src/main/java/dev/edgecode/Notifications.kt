package dev.edgecode

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.edgecode.core.model.PermissionRequest
import dev.edgecode.core.model.QuestionRequest
import dev.edgecode.core.model.SessionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Background alerts for blocking prompts.
 *
 * A question or a permission request stops the agent, so it has to be visible
 * even when the app is not. Notifications are posted only while the activity is
 * stopped; in the foreground the pinned card is the signal. The app cannot
 * receive events after the process is killed — this covers the backgrounded
 * case, which is the one the stream can actually serve.
 */
class Notifications(context: Context) {

    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    private val _foreground = MutableStateFlow(true)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

    private val notifiedQuestions = mutableSetOf<String>()
    private val notifiedPermissions = mutableSetOf<String>()

    init {
        createChannel()
    }

    /**
     * Called from the activity's start/stop. Coming back to the foreground
     * clears everything this app posted: the card in the chat is now the
     * affordance.
     */
    fun setForeground(value: Boolean) {
        _foreground.value = value
        if (value) {
            (notifiedQuestions + notifiedPermissions).forEach { cancel(it) }
            notifiedQuestions.clear()
            notifiedPermissions.clear()
        }
    }

    fun syncQuestions(items: List<QuestionRequest>, sessions: List<SessionSummary>) {
        val ids = items.map { it.id }.toSet()
        (notifiedQuestions - ids).forEach { cancel(it) }
        notifiedQuestions.retainAll(ids)
        if (_foreground.value) return

        items.filter { it.id !in notifiedQuestions }.forEach { request ->
            val first = request.questions.firstOrNull() ?: return@forEach
            val title = sessionTitle(request.sessionId, sessions)
            val body = if (request.questions.size == 1) {
                first.question
            } else {
                "${request.questions.size} questions · ${first.header}"
            }
            post(request.id, "Question from $title", body, request.sessionId)
            notifiedQuestions += request.id
        }
    }

    fun syncPermissions(items: List<PermissionRequest>, sessions: List<SessionSummary>) {
        val ids = items.map { it.id }.toSet()
        (notifiedPermissions - ids).forEach { cancel(it) }
        notifiedPermissions.retainAll(ids)
        if (_foreground.value) return

        items.filter { it.id !in notifiedPermissions }.forEach { request ->
            val title = sessionTitle(request.sessionId, sessions)
            post(request.id, "Approval needed", "$title · ${request.title}", request.sessionId)
            notifiedPermissions += request.id
        }
    }

    private fun sessionTitle(sessionId: String, sessions: List<SessionSummary>): String =
        sessions.firstOrNull { it.id == sessionId }?.title?.takeIf { it.isNotBlank() }
            ?: "OpenCode session"

    private fun post(id: String, title: String, text: String, sessionId: String) {
        if (!canNotify()) return

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NOTIFICATION_SESSION_ID, sessionId)
        }
        val pending = PendingIntent.getActivity(
            context,
            id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setGroup(GROUP)
            .build()

        runCatching { manager.notify(id.hashCode(), notification) }
    }

    private fun cancel(id: String) {
        runCatching { manager.cancel(id.hashCode()) }
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Agent prompts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Questions and permission requests while EdgeCode is in the background"
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "edgecode.prompts"
        const val GROUP = "edgecode.prompts"
    }
}

/** Carried by the notification so a tap lands on the session that is blocked. */
const val NOTIFICATION_SESSION_ID = "dev.edgecode.notification.session"
