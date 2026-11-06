package dev.rezy.veil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rezy.veil.ui.theme.Handjet
import dev.rezy.veil.ui.theme.Ink
import dev.rezy.veil.ui.theme.Line
import dev.rezy.veil.ui.theme.Paper
import dev.rezy.veil.ui.theme.Slate
import dev.rezy.veil.ui.theme.dotStyle

@Composable
fun Rule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Line)
    )
}

@Composable
fun VeilButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val foreground: Color = when {
        !enabled -> Slate
        filled -> Ink
        else -> Paper
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .then(
                if (filled) {
                    Modifier.background(if (enabled) Paper else Line)
                } else {
                    Modifier.border(1.dp, Line)
                }
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = Handjet,
                fontSize = 20.sp,
                letterSpacing = 5.sp,
                color = foreground
            )
        )
    }
}

@Composable
fun VeilField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    masked: Boolean = false,
    keyboardType: androidx.compose.ui.text.input.KeyboardType =
        androidx.compose.ui.text.input.KeyboardType.Text
) {
    Column(Modifier.padding(bottom = 18.dp)) {
        Text(
            text = label,
            style = dotStyle(13, Slate, spacing = 3)
        )
        Spacer(Modifier.height(6.dp))
        Box {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        color = Slate.copy(alpha = 0.7f)
                    )
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(Paper),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    color = Paper
                ),
                visualTransformation =
                    if (masked) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Rule()
    }
}
