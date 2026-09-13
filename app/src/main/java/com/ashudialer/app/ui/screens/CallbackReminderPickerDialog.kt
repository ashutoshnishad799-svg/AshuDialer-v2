package com.ashudialer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun CallbackReminderPickerDialog(
    callerName: String,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Callback reminder")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (callerName.isBlank()) {
                        "When should you be reminded to call back?"
                    } else {
                        "When should you be reminded to call $callerName?"
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReminderButton(
                        text = "1 hour",
                        onClick = {
                            onConfirm(
                                System.currentTimeMillis() + 60L * 60L * 1000L
                            )
                        }
                    )

                    ReminderButton(
                        text = "2 hours",
                        onClick = {
                            onConfirm(
                                System.currentTimeMillis() + 2L * 60L * 60L * 1000L
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReminderButton(
                        text = "Tomorrow",
                        onClick = {
                            val calendar = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, 1)
                                set(Calendar.HOUR_OF_DAY, 9)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            onConfirm(calendar.timeInMillis)
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ReminderButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier.weight(1f),
        onClick = onClick
    ) {
        Text(text)
    }
}
