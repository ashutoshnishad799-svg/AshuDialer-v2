package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.launch
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.components.glassCircle

/**
 * Private Space's own settings - reached from the lock icon on its Home
 * screen. Two actions: change the password (requires the current one, see
 * PrivateSpaceRepository.changePassword) and a full reset (wipes the
 * password, every locked number, and every recording moved in - requires
 * typing a confirmation word first, deliberately more friction than the
 * password change since this one can't be undone).
 */
@Composable
fun PrivateSpaceSettingsScreen(
    onBack: () -> Unit,
    onChangePassword: suspend (current: String, new: String) -> com.ashudialer.app.data.PrivateSpaceResetResult,
    onWipeEverything: () -> Unit,
    hasPin: Boolean = false,
    onSetPin: suspend (currentPassword: String, pin: String) -> String? = { _, _ -> "PIN is not available" },
    onClearPin: () -> Unit = {},
    hideLockedCallHistoryFromRecents: Boolean = false,
    onSetHideLockedCallHistoryFromRecents: (Boolean) -> Unit = {},
    hideLockedContactsFromContactsList: Boolean = false,
    onSetHideLockedContactsFromContactsList: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    var showWipeDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.glassCircle(palette)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Private Space settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            ChangePasswordSection(onChangePassword = onChangePassword, palette = palette)

            Spacer(Modifier.height(32.dp))
            androidx.compose.material3.HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
            Spacer(Modifier.height(24.dp))

            PinSection(hasPin = hasPin, onSetPin = onSetPin, onClearPin = onClearPin, palette = palette)

            Spacer(Modifier.height(32.dp))
            androidx.compose.material3.HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
            Spacer(Modifier.height(24.dp))

            // Isolation section - both toggles default off (the values
            // passed in already reflect that; see MainViewModel's
            // hideLockedCallHistoryFromRecents/hideLockedContactsFromContactsList).
            // Deliberately live only here inside Private Space's own
            // settings, not the main app Settings screen, since neither
            // makes sense to a person who hasn't set Private Space up.
            Text("Isolation", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Keep locked numbers out of the main app entirely - their calls and contact card only show up in here.",
                fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 17.sp
            )
            Spacer(Modifier.height(14.dp))
            Column(modifier = Modifier.fillMaxWidth().glassCard(palette, 16.dp)) {
                IsolationToggleRow(
                    title = "Hide call history",
                    subtitle = "Locked numbers' calls only appear in Private Space, not in Recents",
                    checked = hideLockedCallHistoryFromRecents,
                    onCheckedChange = onSetHideLockedCallHistoryFromRecents,
                    palette = palette
                )
                androidx.compose.material3.HorizontalDivider(color = palette.cardBorder, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                IsolationToggleRow(
                    title = "Hide contacts",
                    subtitle = "Locked numbers' saved contacts only appear in Private Space, not in Contacts",
                    checked = hideLockedContactsFromContactsList,
                    onCheckedChange = onSetHideLockedContactsFromContactsList,
                    palette = palette
                )
            }

            Spacer(Modifier.height(28.dp))
            androidx.compose.material3.HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
            Spacer(Modifier.height(24.dp))

            Text("Danger zone", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.danger)
            Spacer(Modifier.height(8.dp))
            Text(
                "This permanently deletes your Private Space password, every locked number, and every recording moved in here. This can't be undone.",
                fontSize = 12.5.sp, color = palette.textSecondary, lineHeight = 17.sp
            )
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = { showWipeDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.danger)
            ) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset Private Space", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showWipeDialog) {
        WipeConfirmationDialog(
            palette = palette,
            onDismiss = { showWipeDialog = false },
            onConfirm = {
                showWipeDialog = false
                onWipeEverything()
            }
        )
    }
}

@Composable
private fun IsolationToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    palette: DialerPalette
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = palette.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.5.sp, color = palette.textSecondary, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = palette.accent)
        )
    }
}

@Composable
private fun ChangePasswordSection(
    onChangePassword: suspend (current: String, new: String) -> com.ashudialer.app.data.PrivateSpaceResetResult,
    palette: DialerPalette
) {
    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    Text("Change password", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
    Spacer(Modifier.height(4.dp))
    Text(
        "Your backup code stays the same - only the password changes.",
        fontSize = 12.5.sp, color = palette.textSecondary
    )
    Spacer(Modifier.height(16.dp))

    SettingsField(value = currentPassword, onValueChange = { currentPassword = it; errorText = null; successMessage = null }, placeholder = "Current password", palette = palette)
    Spacer(Modifier.height(10.dp))
    SettingsField(value = newPassword, onValueChange = { newPassword = it; errorText = null; successMessage = null }, placeholder = "New password", palette = palette)
    Spacer(Modifier.height(10.dp))
    SettingsField(value = confirmPassword, onValueChange = { confirmPassword = it; errorText = null; successMessage = null }, placeholder = "Confirm new password", palette = palette)

    if (errorText != null) {
        Spacer(Modifier.height(8.dp))
        Text(errorText!!, fontSize = 12.5.sp, color = palette.danger)
    }
    if (successMessage != null) {
        Spacer(Modifier.height(8.dp))
        Text(successMessage!!, fontSize = 12.5.sp, color = palette.accent, fontWeight = FontWeight.Medium)
    }

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = {
            when {
                currentPassword.isEmpty() -> errorText = "Enter your current password"
                newPassword.length < 4 -> errorText = "New password must be at least 4 characters"
                newPassword != confirmPassword -> errorText = "New passwords don't match"
                else -> {
                    isSubmitting = true
                    scope.launch {
                        when (val result = onChangePassword(currentPassword, newPassword)) {
                            is com.ashudialer.app.data.PrivateSpaceResetResult.Success -> {
                                successMessage = "Password changed"
                                currentPassword = ""
                                newPassword = ""
                                confirmPassword = ""
                            }
                            is com.ashudialer.app.data.PrivateSpaceResetResult.Failure -> errorText = result.message
                        }
                        isSubmitting = false
                    }
                }
            }
        },
        enabled = !isSubmitting,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
    ) {
        Icon(Icons.Filled.Password, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (isSubmitting) "Updating…" else "Update password", fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Optional PIN. The password always keeps working; a PIN is a faster way in, entered on a keypad.
 * Setting one needs the current password (so someone holding an already-unlocked phone cannot add a
 * PIN of their own), and it counts toward the same lockout as the password.
 */
@Composable
private fun PinSection(
    hasPin: Boolean,
    onSetPin: suspend (currentPassword: String, pin: String) -> String?,
    onClearPin: () -> Unit,
    palette: DialerPalette
) {
    val scope = rememberCoroutineScope()
    var pinIsSet by remember(hasPin) { mutableStateOf(hasPin) }
    var currentPassword by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    Text("PIN unlock", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
    Spacer(Modifier.height(4.dp))
    Text(
        if (pinIsSet) "A PIN is set. You can still unlock with your password."
        else "Add a 4 to 6 digit PIN to open Private Space from a keypad. Your password keeps working.",
        fontSize = 12.5.sp, color = palette.textSecondary
    )
    Spacer(Modifier.height(12.dp))

    if (!editing) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { editing = true; message = null; errorText = null },
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) { Text(if (pinIsSet) "Change PIN" else "Set a PIN", fontWeight = FontWeight.SemiBold) }
            if (pinIsSet) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { onClearPin(); pinIsSet = false; message = "PIN removed" },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Remove PIN") }
            }
        }
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message!!, fontSize = 12.5.sp, color = palette.accent, fontWeight = FontWeight.Medium)
        }
        return
    }

    SettingsField(value = currentPassword, onValueChange = { currentPassword = it; errorText = null }, placeholder = "Current password", palette = palette)
    Spacer(Modifier.height(10.dp))
    NumericField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6); errorText = null }, placeholder = "New PIN (4 to 6 digits)", palette = palette)
    Spacer(Modifier.height(10.dp))
    NumericField(value = confirmPin, onValueChange = { confirmPin = it.filter(Char::isDigit).take(6); errorText = null }, placeholder = "Confirm PIN", palette = palette)
    if (errorText != null) {
        Spacer(Modifier.height(8.dp))
        Text(errorText!!, fontSize = 12.5.sp, color = palette.danger)
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.material3.OutlinedButton(
            onClick = { editing = false; currentPassword = ""; pin = ""; confirmPin = ""; errorText = null },
            modifier = Modifier.weight(1f).height(46.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Cancel") }
        Button(
            onClick = {
                when {
                    currentPassword.isEmpty() -> errorText = "Enter your current password"
                    pin != confirmPin -> errorText = "The two PINs don't match"
                    else -> {
                        isSubmitting = true
                        scope.launch {
                            val error = onSetPin(currentPassword, pin)
                            isSubmitting = false
                            if (error != null) errorText = error
                            else {
                                pinIsSet = true; editing = false; message = "PIN saved"
                                currentPassword = ""; pin = ""; confirmPin = ""
                            }
                        }
                    }
                }
            },
            enabled = !isSubmitting,
            modifier = Modifier.weight(1f).height(46.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
        ) { Text(if (isSubmitting) "Saving…" else "Save PIN", fontWeight = FontWeight.SemiBold) }
    }
}

/** Numeric, masked field for PIN entry (the general SettingsField always asks for a text keyboard). */
@Composable
private fun NumericField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    palette: DialerPalette
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(palette, 14.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = palette.textSecondary, fontSize = 14.5.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.5.sp),
            cursorBrush = SolidColor(palette.accent),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * A typed confirmation ("DELETE") rather than a plain Yes/No dialog -
 * matches the friction level of other irreversible-wipe patterns (GitHub
 * repo deletion, etc). Reset Private Space is one accidental tap on a
 * plain confirm button away from losing every locked number and recording
 * with no recovery path, so the extra typing step is intentional, not an
 * oversight.
 */
@Composable
private fun WipeConfirmationDialog(
    palette: DialerPalette,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var confirmationText by remember { mutableStateOf("") }
    val canConfirm = confirmationText.trim().equals("DELETE", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Private Space?") },
        text = {
            Column {
                Text(
                    "Your password, locked numbers, and every recording moved here will be permanently deleted. This can't be undone.",
                    fontSize = 13.sp, color = palette.textSecondary, lineHeight = 18.sp
                )
                Spacer(Modifier.height(14.dp))
                Text("Type DELETE to confirm", fontSize = 12.5.sp, color = palette.textSecondary)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.searchBackground)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = confirmationText,
                        onValueChange = { confirmationText = it },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.textPrimary, fontSize = 13.5.sp),
                        cursorBrush = SolidColor(palette.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) {
                Text("Delete everything", color = if (canConfirm) palette.danger else palette.textSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    palette: DialerPalette
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(palette, 14.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = palette.textSecondary, fontSize = 14.5.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.5.sp),
            cursorBrush = SolidColor(palette.accent),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
