package dev.rezy.veil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
                label = "TAILSCALE HOST",
                value = host,
                onValueChange = onHost,
                placeholder = "100.x.y.z"
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

            VeilButton(
                label = "SAVE",
                filled = true,
                enabled = !busy
            ) { onSave() }

            Spacer(Modifier.height(26.dp))
            Rule()
            Spacer(Modifier.height(20.dp))

            Note(
                "The host and secret come from desktop/setup.sh. Run it on the " +
                    "Omarchy machine; it prints both and starts the daemon."
            )
            Spacer(Modifier.height(10.dp))
            Note(
                "Saving the password asks for your fingerprint. It is encrypted " +
                    "with a key inside the secure element that only a live biometric " +
                    "can use — the stored bytes are useless without you."
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
