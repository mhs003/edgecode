package dev.edgecode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.edgecode.core.design.EdgeIcons
import dev.edgecode.core.design.LocalEdgeColors
import dev.edgecode.core.design.Radius
import dev.edgecode.core.design.Size
import dev.edgecode.core.design.Space
import dev.edgecode.core.design.Stroke
import dev.edgecode.core.design.components.EdgeButton
import dev.edgecode.core.design.components.EdgeSecondaryButton
import dev.edgecode.core.design.components.EdgeTextField
import dev.edgecode.core.design.components.ErrorNotice
import dev.edgecode.core.design.components.TextAction
import dev.edgecode.core.design.components.bottomSystemInset
import dev.edgecode.core.design.components.tappable
import dev.edgecode.core.design.components.topSystemInset
import dev.edgecode.core.model.ConnectionState
import dev.edgecode.ui.AppViewModel

/**
 * First screen: point the app at a server.
 *
 * Validation is real — the screen only advances once the server has answered a
 * health check — but the demo entry beside it is equally prominent, because the
 * app is fully usable without a server.
 */
@Composable
fun ConnectScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val edge = LocalEdgeColors.current
    val connection by viewModel.connection.collectAsState()
    val saved = viewModel.settings_.collectAsState().value

    var url by remember { mutableStateOf(saved.serverUrl) }
    var username by remember { mutableStateOf(saved.serverUsername.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var rememberServer by remember { mutableStateOf(true) }

    val connecting = connection is ConnectionState.Connecting
    val failure = (connection as? ConnectionState.Failed)?.message

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .topSystemInset()
            .bottomSystemInset()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Space.xxxl))

        EdgeMark()

        Spacer(Modifier.height(Space.lg))

        Text(
            text = "EdgeCode",
            style = MaterialTheme.typography.displaySmall,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = "Connect to an OpenCode server to work with your sessions, " +
                "tools and models from this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = edge.faint,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )

        Spacer(Modifier.height(Space.xxl))

        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            EdgeTextField(
                value = url,
                onValueChange = { url = it },
                label = "Server address",
                placeholder = "192.168.1.10:4096",
                leadingIcon = EdgeIcons.Server,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
                enabled = !connecting,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (showAdvanced) "Hide credentials" else "Add credentials",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .tappable { showAdvanced = !showAdvanced }
                        .padding(vertical = Space.xs, horizontal = Space.xxs),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Optional",
                    style = MaterialTheme.typography.labelSmall,
                    color = edge.faint,
                )
            }

            if (showAdvanced) {
                EdgeTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Username",
                    placeholder = "opencode",
                    leadingIcon = EdgeIcons.Person,
                    enabled = !connecting,
                )
                EdgeTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    placeholder = "Server password",
                    leadingIcon = EdgeIcons.Lock,
                    singleLine = true,
                    enabled = !connecting,
                    imeAction = ImeAction.Done,
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailing = {
                        TextAction(
                            text = if (showPassword) "Hide" else "Show",
                            onClick = { showPassword = !showPassword },
                        )
                    },
                )
            }

            RememberRow(
                checked = rememberServer,
                onToggle = { rememberServer = !rememberServer },
                enabled = !connecting,
            )

            if (failure != null) {
                ErrorNotice(message = failure)
            }

            EdgeButton(
                text = if (connecting) "Connecting" else "Connect",
                onClick = { viewModel.connect(url, username, password, rememberServer) },
                enabled = url.isNotBlank(),
                loading = connecting,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(Stroke.hairline)
                        .background(scheme.outlineVariant),
                )
                Text(
                    text = "or",
                    style = MaterialTheme.typography.labelSmall,
                    color = edge.faint,
                    modifier = Modifier.padding(horizontal = Space.md),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(Stroke.hairline)
                        .background(scheme.outlineVariant),
                )
            }

            // Deliberately the secondary action: with no server yet it is the
            // path most people take, but Connect stays the screen's one primary.
            EdgeSecondaryButton(
                text = "Explore the demo",
                icon = EdgeIcons.Terminal,
                onClick = { viewModel.connectDemo(rememberServer) },
                enabled = !connecting,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "No server yet? The demo runs the whole interface against " +
                    "sample data, including streaming replies, tool calls and " +
                    "permission prompts.",
                style = MaterialTheme.typography.bodySmall,
                color = edge.faint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Space.xxxl))
    }
}

@Composable
private fun RememberRow(checked: Boolean, onToggle: () -> Unit, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .tappable(enabled = enabled, onClick = onToggle)
            .padding(vertical = Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(if (checked) scheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                .then(
                    Modifier.borderBox(
                        if (checked) scheme.primary else scheme.outline,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                androidx.compose.material3.Icon(
                    imageVector = EdgeIcons.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Text(
            text = "Remember this server",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

private fun Modifier.borderBox(color: Color): Modifier =
    border(Stroke.thin, color, RoundedCornerShape(Radius.xs))

/** Product mark: a terminal prompt in a rounded tile. */
@Composable
private fun EdgeMark() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.lg))
            .background(scheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = EdgeIcons.Terminal,
            contentDescription = null,
            tint = scheme.onPrimary,
            modifier = Modifier.size(Size.iconXl),
        )
    }
}
