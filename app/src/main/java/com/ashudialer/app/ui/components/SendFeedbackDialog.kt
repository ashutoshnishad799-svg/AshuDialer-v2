package com.ashudialer.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.DiagnosticsShareHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * "Send feedback" as a single tap: type what happened, tap Send, and it goes straight to the
 * developer over Firestore - no share sheet, no picking an app.
 *
 * App version, device model, Android version and the last crash report (if any) ride along
 * automatically (see DiagnosticsShareHelper.sendToFirestore) exactly as the old share-sheet
 * report did; only the delivery method changed, not what's collected.
 *
 * A failed send (e.g. no network) is shown as an inline error with the typed message kept in
 * the field, rather than silently discarded - the person can just tap Send again once they're
 * back online.
 */
@Composable
fun SendFeedbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    var sendJob by remember { mutableStateOf<Job?>(null) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(if (sent) "Sent" else "Send feedback") },
        text = {
            if (sent) {
                Text("Thanks - your feedback went through.")
            } else {
                Column {
                    Text(
                        "What happened? Your app version, phone model and Android version are included automatically.",
                        fontSize = 12.5.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it; errorText = null },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("Describe the issue...") },
                        enabled = !sending
                    )
                    if (errorText != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(errorText!!, color = androidx.compose.ui.graphics.Color(0xFFE0442E), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (!sent) {
                TextButton(
                    enabled = !sending && message.isNotBlank(),
                    onClick = {
                        if (sending) return@TextButton
                        sending = true
                        errorText = null
                        sendJob = scope.launch {
                            val result = DiagnosticsShareHelper.sendToFirestore(context, message)
                            sending = false
                            sendJob = null
                            result.onSuccess {
                                sent = true
                            }.onFailure { error ->
                                errorText = if (error is kotlinx.coroutines.CancellationException) {
                                    null
                                } else {
                                    "Couldn't send - check your connection and try again."
                                }
                            }
                        }
                    }
                ) {
                    if (sending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Send")
                    }
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (!sent) {
                TextButton(
                    onClick = {
                        if (sending) {
                            sendJob?.cancel()
                            sendJob = null
                            sending = false
                        } else {
                            onDismiss()
                        }
                    }
                ) { Text(if (sending) "Cancel sending" else "Cancel") }
            }
        }
    )
}

@Composable
private fun Spacer(modifier: Modifier = Modifier) = androidx.compose.foundation.layout.Spacer(modifier)
