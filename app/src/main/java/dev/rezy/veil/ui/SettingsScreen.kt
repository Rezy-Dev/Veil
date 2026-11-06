package dev.rezy.veil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rezy.veil.ui.theme.Ash
import dev.rezy.veil.ui.theme.Handjet
import dev.rezy.veil.ui.theme.Ink
import dev.rezy.veil.ui.theme.Paper
import dev.rezy.veil.ui.theme.Slate
import dev.rezy.veil.ui.theme.dotGrid
import dev.rezy.veil.ui.theme.dotStyle

@Composable
fun SettingsScreen(
    host: String,
    onHost: (String) -> Unit,
    port: String,
    onPort: (String) -> Unit,
    secret: String,
    onSecret: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    secure: Boolean,
    onSecure: (Boolean) -> Unit,
    hasPassword: Boolean,
    busy: Boolean,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
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
            Spacer(Modifier.height(20.dp))

            Text(
                text = "BACK",
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                style = TextStyle(
                    fontFamily = Handjet,
                    fontSize = 16.sp,
                    letterSpacing = 4.sp,
                    color = Ash
                )
            )

            Spacer(Modifier.height(18.dp))
            Text("SETUP", style = dotStyle(40, Paper, spacing = 1))
            Spacer(Modifier.height(26.dp))
            Rule()
            Spacer(Modifier.height(26.dp))

            VeilField(
                label = "SERVER HOST",
                value = host,
                onValueChange = onHost,
                placeholder = "hostname, IP or tunnel"
            )
            VeilField(
                label = "PORT",
                value = port,
                onValueChange = onPort,
                placeholder = "8765",
                keyboardType = KeyboardType.Number
            )
            VeilField(
                label = "SHARED SECRET",
                value = secret,
                onValueChange = onSecret,
                placeholder = "hex from setup.sh",
                masked = true
            )
            VeilField(
                label = if (hasPassword) "DESKTOP PASSWORD · SAVED" else "DESKTOP PASSWORD",
                value = password,
                onValueChange = onPassword,
                placeholder = if (hasPassword) "leave blank to keep" else "login password",
                masked = true,
                keyboardType = KeyboardType.Password
            )

            Spacer(Modifier.height(10.dp))

            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "TLS",
                        style = dotStyle(13, Slate, spacing = 3)
                    )
                    Text(
                        text = if (secure) "https via Tailscale Serve" else "http, tailnet only",
                        style = TextStyle(
                            fontFamily = Handjet,
                            fontSize = 12.sp,
                            color = Ash
                        )
                    )
                }
                VeilToggle(checked = secure, onChange = onSecure)
            }

            Spacer(Modifier.height(22.dp))

            VeilButton(
                label = "SAVE",
                filled = true,
                enabled = !busy
            ) { onSave() }

            Spacer(Modifier.height(26.dp))
            Rule()
            Spacer(Modifier.height(20.dp))

            Note(
                "The host and secret come from desktop/setup.sh on the machine " +
                    "running the daemon."
            )
            Spacer(Modifier.height(10.dp))
            Note(
                "Any transport works: a Tailscale address, a Cloudflare Tunnel " +
                    "hostname, a reverse proxy, or a plain LAN IP. Turn TLS on " +
                    "(port 443) when something terminates HTTPS for you."
            )
            Spacer(Modifier.height(10.dp))
            Note(
                "Saving the password asks you to prove it's you — a biometric if " +
                    "the phone has one, otherwise your PIN, pattern or password. " +
                    "The stored bytes are useless without that check."
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = Slate
        )
    )
}
