package com.ashudialer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.launch

/**
 * The gate for calling a locked number - reuses Private Space's password
 * rather than a separate PIN, so protecting a number doesn't mean the
 * person now has two different secrets to remember. Success places the
 * call; dismissing (back press, tapping outside, or Cancel) leaves the call
 * un-placed entirely rather than falling back to placing it anyway.
 */
@Composable
fun LockedNumberPinDialog(
    onDismiss: () -> Unit,
    onVerify: suspend (String) -> Boolean,
    onVerified: () -> Unit
) {
    val palette = LocalDialerPalette.current
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    fun submit() {
        if (attempt.isEmpty() || isChecking) return
        isChecking = true
        scope.launch {
            val ok = onVerify(attempt)
            isChecking = false
            if (ok) {
                onVerified()
            } else {
                errorText = "Incorrect password"
                attempt = ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = palette.accent) },
        title = { Text("This number is locked") },
        text = {
            Column {
                Text(
                    "Enter your Private Space password to place this call.",
                    fontSize = 13.sp, color = palette.textSecondary, lineHeight = 18.sp
                )
                Spacer(Modifier.height(14.dp))
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.searchBackground)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (attempt.isEmpty()) {
                        Text("Password", color = palette.textSecondary, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = attempt,
                        onValueChange = { attempt = it; errorText = null },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(palette.accent),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorText!!, fontSize = 12.sp, color = palette.danger)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = !isChecking && attempt.isNotEmpty()) {
                Text(if (isChecking) "Checking…" else "Call", color = palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
