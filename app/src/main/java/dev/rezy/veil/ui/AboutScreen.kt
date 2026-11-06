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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rezy.veil.ui.theme.Ash
import dev.rezy.veil.ui.theme.Handjet
import dev.rezy.veil.ui.theme.Ink
import dev.rezy.veil.ui.theme.Line
import dev.rezy.veil.ui.theme.Paper
import dev.rezy.veil.ui.theme.dotGrid
import dev.rezy.veil.ui.theme.dotStyle

@Composable
fun AboutScreen(onBack: () -> Unit) {
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

            Spacer(Modifier.height(20.dp))

            Text("VEIL", style = dotStyle(46, Paper, spacing = 1))
            Spacer(Modifier.height(6.dp))
            Text(
                "ABOUT",
                style = TextStyle(
                    fontFamily = Handjet,
                    fontSize = 15.sp,
                    letterSpacing = 4.sp,
                    color = Ash
                )
            )

            Spacer(Modifier.height(26.dp))
            Rule()
            Spacer(Modifier.height(26.dp))

            Section(
                "WHAT IT IS",
                "Veil locks and unlocks your desktop from this phone. It talks to " +
                    "a small daemon running on that machine."
            )

            Section(
                "BIOMETRICS",
                "Unlocking makes you prove it's you — a fingerprint or face if the " +
                    "phone has one, otherwise your PIN, pattern or password. The " +
                    "desktop password is sealed by a key in the secure element that " +
                    "only that check can release."
            )

            Section(
                "HOW UNLOCK WORKS",
                "The daemon types the password into the focused lock screen and " +
                    "presses Return. The locker's own PAM stack validates it exactly " +
                    "as if you had typed it by hand — nothing bypasses authentication."
            )

            Section(
                "TRANSPORT",
                "Any path works: a private network, a tunnel, or a reverse proxy. " +
                    "Requests are signed with HMAC-SHA256 over a timestamp, a nonce, " +
                    "the method, the path and the body, so forged or replayed " +
                    "requests are rejected. Use TLS whenever the daemon is not " +
                    "reachable only from a network you trust."
            )

            Spacer(Modifier.height(2.dp))
            Rule()
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Text(
        text = title,
        style = TextStyle(
            fontFamily = Handjet,
            fontSize = 16.sp,
            letterSpacing = 3.sp,
            color = Paper
        )
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = body,
        style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = Ash
        )
    )
    Spacer(Modifier.height(26.dp))
}

@Composable
private fun Rule() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Line)
    )
}
