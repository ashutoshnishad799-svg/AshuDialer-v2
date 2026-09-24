package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard

enum class LocalBackupMode { EXPORT, IMPORT }


@Composable
fun LocalBackupScreen(
    onBack: () -> Unit,
    onExport: (pin: String) -> Unit,
    onPickImportFile: () -> Unit,
    pendingImportFileName: String?,
    onConfirmImport: (pin: String) -> Unit,
    statusMessage: String?,
    isBusy: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    var mode by remember { mutableStateOf(LocalBackupMode.EXPORT) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var acknowledgedNoRecovery by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Local Backup", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModeTab("Export", mode == LocalBackupMode.EXPORT, palette) { mode = LocalBackupMode.EXPORT; pin = ""; confirmPin = "" }
            ModeTab("Import", mode == LocalBackupMode.IMPORT, palette) { mode = LocalBackupMode.IMPORT; pin = "" }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            when (mode) {
                LocalBackupMode.EXPORT -> {
                    InfoCard(
                        icon = Icons.Filled.SdStorage,
                        title = "Encrypted, offline, yours",
                        body = "Your call log, notes, blocked numbers, reported spam, and SIM/vibration rules are encrypted on this device and saved wherever you choose — no server, no account.",
                        palette = palette
                    )
                    Spacer(Modifier.height(16.dp))

                    Text("Choose a PIN", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    PinField(value = pin, onChange = { pin = it }, palette = palette)
                    Spacer(Modifier.height(10.dp))
                    Text("Confirm PIN", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    PinField(value = confirmPin, onChange = { confirmPin = it }, palette = palette)

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(palette.danger.copy(alpha = 0.12f)).padding(12.dp)
                    ) {
                        Checkbox(
                            checked = acknowledgedNoRecovery, onCheckedChange = { acknowledgedNoRecovery = it },
                            colors = CheckboxDefaults.colors(checkedColor = palette.danger)
                        )
                        Text(
                            "If you forget this PIN, this backup file can never be recovered — not by us, not by anyone. There is no reset.",
                            fontSize = 12.sp, color = palette.textPrimary, lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 12.dp, end = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    val canExport = pin.length >= 4 && pin == confirmPin && acknowledgedNoRecovery && !isBusy
                    Button(
                        onClick = { onExport(pin) },
                        enabled = canExport,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                    ) {
                        if (isBusy) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Export Backup", fontWeight = FontWeight.SemiBold)
                    }
                    if (pin.isNotEmpty() && confirmPin.isNotEmpty() && pin != confirmPin) {
                        Spacer(Modifier.height(6.dp))
                        Text("PINs don't match", fontSize = 12.sp, color = palette.danger)
                    }
                }

                LocalBackupMode.IMPORT -> {
                    InfoCard(
                        icon = Icons.Filled.CloudDownload,
                        title = "Restore from a backup file",
                        body = "Pick a .adlb file you exported earlier, then enter the PIN you set at the time.",
                        palette = palette
                    )
                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onPickImportFile,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.cardBackground, contentColor = palette.textPrimary)
                    ) {
                        Text(pendingImportFileName ?: "Choose backup file", fontWeight = FontWeight.Medium)
                    }

                    if (pendingImportFileName != null) {
                        Spacer(Modifier.height(16.dp))
                        Text("Enter PIN", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.textSecondary)
                        Spacer(Modifier.height(6.dp))
                        PinField(value = pin, onChange = { pin = it }, palette = palette)

                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = { onConfirmImport(pin) },
                            enabled = pin.length >= 4 && !isBusy,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                        ) {
                            if (isBusy) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("Restore", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (statusMessage != null) {
                Spacer(Modifier.height(16.dp))
                Text(statusMessage, fontSize = 13.sp, color = palette.textPrimary, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, palette: DialerPalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.accent else palette.cardBackground)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            label, color = if (selected) Color.White else palette.textSecondary,
            fontWeight = FontWeight.SemiBold, fontSize = 14.sp
        )
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String, palette: DialerPalette) {
    Row(
        modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp).padding(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            Spacer(Modifier.height(3.dp))
            Text(body, fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, palette: DialerPalette) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.searchBackground)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) Text("Enter PIN", color = palette.textSecondary, fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = { input -> onChange(input.filter { it.isDigit() }.take(12)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = TextStyle(color = palette.textPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
