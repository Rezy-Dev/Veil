package dev.rezy.veil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rezy.veil.ui.theme.Ash
import dev.rezy.veil.ui.theme.Ember
import dev.rezy.veil.ui.theme.Handjet
import dev.rezy.veil.ui.theme.Ink
import dev.rezy.veil.ui.theme.Paper
import dev.rezy.veil.ui.theme.Slate
import dev.rezy.veil.ui.theme.dotGrid
import dev.rezy.veil.ui.theme.dotStyle

data class RemoteState(
    val locked: Boolean? = null,
    val reachable: Boolean? = null,
    val busy: Boolean = false,
    val prompting: Boolean = false,
    val focus: String? = null,
    val message: String? = null
)

@Composable
fun RemoteScreen(
    state: RemoteState,
    endpoint: String,
    configured: Boolean,
    hasPassword: Boolean,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onRefresh: () -> Unit,
    onSetup: () -> Unit,
    onAbout: () -> Unit
) {
    val (word, tone) = statusLabel(state, configured)

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .dotGrid(step = 18.dp, alpha = 0.05f)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Spacer(Modifier.height(28.dp))

            Text("VEIL", style = dotStyle(46, Paper, spacing = 1))
            Spacer(Modifier.height(6.dp))
            Text(
                "REMOTE CONTROL",
                style = TextStyle(
                    fontFamily = Handjet,
                    fontSize = 15.sp,
                    letterSpacing = 4.sp,
                    color = Ash
                )
            )

            Spacer(Modifier.height(28.dp))
            Rule()
            Spacer(Modifier.height(26.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(tone, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Text(word, style = dotStyle(34, Paper, spacing = 2))
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = if (configured) endpoint else "not set up",
                style = TextStyle(
                    fontFamily = Handjet,
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                    color = Slate
                )
            )

            if (state.prompting && state.focus != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "→ ${state.focus}",
                    style = TextStyle(
                        fontFamily = Handjet,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                        color = Ash
                    )
                )
            }

            Spacer(Modifier.height(20.dp))
            Rule()
            Spacer(Modifier.height(34.dp))

            VeilButton(
                label = "LOCK",
                enabled = configured && !state.busy
            ) { onLock() }

            Spacer(Modifier.height(12.dp))

            VeilButton(
                label = unlockLabel(state, hasPassword),
                filled = true,
                enabled = configured && !state.busy
            ) { onUnlock() }

            Spacer(Modifier.height(22.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Link("SETUP", onSetup)
                Link("REFRESH", onRefresh)
                Link("ABOUT", onAbout)
            }

            if (state.message != null) {
                Spacer(Modifier.height(26.dp))
                Text(
                    text = state.message,
                    style = TextStyle(
                        fontFamily = Handjet,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        letterSpacing = 1.sp,
                        color = Ember
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Link(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        style = TextStyle(
            fontFamily = Handjet,
            fontSize = 15.sp,
            letterSpacing = 4.sp,
            color = Ash
        )
    )
}

private fun statusLabel(state: RemoteState, configured: Boolean): Pair<String, androidx.compose.ui.graphics.Color> {
    return when {
        !configured -> "NOT SET UP" to Ember
        state.busy -> "WORKING" to Ash
        state.reachable == false -> "OFFLINE" to Ember
        state.locked == true -> "LOCKED" to Ember
        state.prompting -> "PASSWORD PROMPT" to Paper
        state.locked == false -> "UNLOCKED" to Paper
        else -> "UNKNOWN" to Ash
    }
}

private fun unlockLabel(state: RemoteState, hasPassword: Boolean): String = when {
    !hasPassword -> "UNLOCK · SET PASSWORD"
    state.locked != true && state.prompting -> "SEND PASSWORD"
    else -> "UNLOCK"
}
