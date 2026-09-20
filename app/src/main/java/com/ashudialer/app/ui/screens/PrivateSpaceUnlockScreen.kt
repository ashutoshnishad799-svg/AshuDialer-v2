package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.LocalDialerPalette
import kotlinx.coroutines.launch
import com.ashudialer.app.ui.components.glassCard

/**
 * Unlock screen.
 *
 * If a PIN has been set ([hasPin]) it opens with a numeric keypad (4-6 digits, unlocks the moment the
 * last digit is entered) and offers "Use password instead"; with no PIN it shows the password field as
 * before. Either way every attempt goes through [onUnlock], which applies the shared lockout: after 5
 * wrong tries the screen switches to a countdown and refuses input until it ends. The countdown reads
 * [lockedForMs] again every second, so it keeps running correctly if the person leaves and returns.
 */
@Composable
fun PrivateSpaceUnlockScreen(
    onBack: () -> Unit,
    onUnlock: suspend (String) -> com.ashudialer.app.data.PrivateSpaceRepository.UnlockResult,
    onResetWithBackupCode: suspend (code: String, newPassword: String) -> com.ashudialer.app.data.PrivateSpaceResetResult,
    onUnlocked: () -> Unit,
    hasPin: Boolean = false,
    lockedForMs: () -> Long = { 0L },
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val scope = rememberCoroutineScope()

    var showRecovery by remember { mutableStateOf(false) }

    if (showRecovery) {
        RecoveryStep(
            onBack = { showRecovery = false },
            onReset = onResetWithBackupCode,
            onSuccess = onUnlocked,
            palette = palette
        )
        return
    }

    var usePassword by remember { mutableStateOf(!hasPin) }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var failedAttempts by remember { mutableStateOf(0) }
    var waitMs by remember { mutableStateOf(lockedForMs()) }

    // Countdown: while locked, re-read the remaining time every second. Reading it from the guard (not
    // from a local timer started at the moment of the last wrong attempt) is what keeps the number
    // right after the person backs out and returns, or rotates the phone.
    androidx.compose.runtime.LaunchedEffect(waitMs > 0) {
        while (waitMs > 0) {
            kotlinx.coroutines.delay(1000)
            waitMs = lockedForMs()
        }
    }
    val locked = waitMs > 0

    fun submit(entry: String) {
        if (entry.isEmpty() || isSubmitting || locked) return
        isSubmitting = true
        scope.launch {
            when (val r = onUnlock(entry)) {
                is com.ashudialer.app.data.PrivateSpaceRepository.UnlockResult.Success -> onUnlocked()
                is com.ashudialer.app.data.PrivateSpaceRepository.UnlockResult.Locked -> {
                    waitMs = r.remainingMs
                    errorText = null
                }
                is com.ashudialer.app.data.PrivateSpaceRepository.UnlockResult.Wrong -> {
                    failedAttempts += 1
                    if (r.lockedForMs > 0) {
                        waitMs = r.lockedForMs
                        errorText = null
                    } else {
                        errorText = if (usePassword) "Incorrect password (${r.attemptsLeft} tries left before a wait)"
                        else "Incorrect PIN (${r.attemptsLeft} tries left before a wait)"
                    }
                }
            }
            pin = ""
            password = ""
            isSubmitting = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = palette.accent, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Private Space", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    locked -> "Too many wrong attempts"
                    usePassword -> "Enter your password to continue"
                    else -> "Enter your PIN"
                },
                fontSize = 13.sp, color = if (locked) palette.danger else palette.textSecondary
            )

            Spacer(Modifier.height(22.dp))

            if (locked) {
                // The whole entry area is replaced by the countdown, so there is nothing to type into.
                Text(
                    com.ashudialer.app.data.Lockout.format(waitMs),
                    fontSize = 34.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Try again when the timer ends",
                    fontSize = 12.5.sp, color = palette.textSecondary
                )
            } else if (usePassword) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(palette, 14.dp)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (password.isEmpty()) {
                            Text("Password", color = palette.textSecondary, fontSize = 15.sp)
                        }
                        BasicTextField(
                            value = password,
                            onValueChange = { password = it; errorText = null },
                            singleLine = true,
                            textStyle = TextStyle(color = palette.textPrimary, fontSize = 15.sp),
                            cursorBrush = SolidColor(palette.accent),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.size(22.dp)) {
                        Icon(
                            if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (errorText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorText!!, fontSize = 12.5.sp, color = palette.danger)
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { submit(password) },
                    enabled = !isSubmitting && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) {
                    Text(if (isSubmitting) "Checking…" else "Unlock", fontWeight = FontWeight.SemiBold)
                }
                if (hasPin) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Use PIN instead",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.accent,
                        modifier = Modifier.clickable { usePassword = false; errorText = null }
                    )
                }
            } else {
                // PIN entry: dots for what has been typed, then the keypad. The PIN can be 4 to 6 digits,
                // and the app does not tell a screen-watcher which length it is, so there is no automatic
                // submit at a fixed length: the check mark submits.
                PinDots(entered = pin.length, palette = palette)
                if (errorText != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(errorText!!, fontSize = 12.5.sp, color = palette.danger, textAlign = TextAlign.Center)
                } else {
                    Spacer(Modifier.height(10.dp))
                    Spacer(Modifier.height(16.dp))
                }
                Spacer(Modifier.height(10.dp))
                PinKeypad(
                    enabled = !isSubmitting,
                    palette = palette,
                    onDigit = { d -> if (pin.length < com.ashudialer.app.data.PrivateSpaceGuard.PIN_MAX) { pin += d; errorText = null } },
                    onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                    onSubmit = { if (pin.length >= com.ashudialer.app.data.PrivateSpaceGuard.PIN_MIN) submit(pin) },
                    canSubmit = pin.length >= com.ashudialer.app.data.PrivateSpaceGuard.PIN_MIN && !isSubmitting
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Use password instead",
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.accent,
                    modifier = Modifier.clickable { usePassword = true; errorText = null; pin = "" }
                )
            }

            // Only surfaced after a failed attempt (or while locked) - keeping it hidden otherwise avoids the
            // recovery path being an easy first stop for someone who is not the owner just poking around.
            if (failedAttempts > 0 || locked) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Forgot password? Use backup code",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.accent,
                    modifier = Modifier.clickable { showRecovery = true }
                )
            }
        }
    }
}

/** The row of dots showing how many digits of the PIN have been typed (no digit is ever drawn). */
@Composable
private fun PinDots(entered: Int, palette: com.ashudialer.app.ui.theme.DialerPalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        // Always 6 slots so the row does not change width as the PIN is typed; the ones beyond what has been
        // typed are hollow.
        repeat(com.ashudialer.app.data.PrivateSpaceGuard.PIN_MAX) { i ->
            val filled = i < entered
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(if (filled) palette.accent else androidx.compose.ui.graphics.Color.Transparent)
                    .border(1.5.dp, if (filled) palette.accent else palette.textSecondary.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}

/** 3 x 4 numeric keypad: 1-9, then backspace, 0, and a submit tick. */
@Composable
private fun PinKeypad(
    enabled: Boolean,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    canSubmit: Boolean
) {
    val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { d -> PinKey(label = d.toString(), enabled = enabled, palette = palette) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PinKey(label = null, icon = Icons.Filled.Backspace, description = "Delete", enabled = enabled, palette = palette, onClick = onBackspace)
            PinKey(label = "0", enabled = enabled, palette = palette) { onDigit('0') }
            PinKey(label = null, icon = Icons.Filled.Check, description = "Unlock", enabled = canSubmit, palette = palette, emphasized = true, onClick = onSubmit)
        }
    }
}

@Composable
private fun PinKey(
    label: String?,
    enabled: Boolean,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    emphasized: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    description: String? = null,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> palette.textSecondary.copy(alpha = 0.4f)
        emphasized -> androidx.compose.ui.graphics.Color.White
        else -> palette.textPrimary
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(if (emphasized && enabled) palette.accent else palette.accentSoft)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            // A real icon rather than a Unicode glyph: glyphs like the backspace symbol depend on the
            // phone's font and show as an empty box on some ROMs.
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(24.dp))
        } else if (label != null) {
            Text(label, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = tint)
        }
    }
}

@Composable
private fun RecoveryStep(
    onBack: () -> Unit,
    onReset: suspend (code: String, newPassword: String) -> com.ashudialer.app.data.PrivateSpaceResetResult,
    onSuccess: () -> Unit,
    palette: com.ashudialer.app.ui.theme.DialerPalette
) {
    val scope = rememberCoroutineScope()
    var backupCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Reset password", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(
                "Enter your backup code and choose a new password.",
                fontSize = 13.sp, color = palette.textSecondary, lineHeight = 18.sp
            )

            Spacer(Modifier.height(20.dp))
            LabeledField(value = backupCode, onValueChange = { backupCode = it; errorText = null }, placeholder = "Backup code (e.g. 1234-5678-9012)", palette = palette)
            Spacer(Modifier.height(10.dp))
            LabeledField(value = newPassword, onValueChange = { newPassword = it; errorText = null }, placeholder = "New password", isPassword = true, palette = palette)
            Spacer(Modifier.height(10.dp))
            LabeledField(value = confirmPassword, onValueChange = { confirmPassword = it; errorText = null }, placeholder = "Confirm new password", isPassword = true, palette = palette)

            if (errorText != null) {
                Spacer(Modifier.height(8.dp))
                Text(errorText!!, fontSize = 12.5.sp, color = palette.danger)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    when {
                        backupCode.isBlank() -> errorText = "Enter your backup code"
                        newPassword.length < 4 -> errorText = "Password must be at least 4 characters"
                        newPassword != confirmPassword -> errorText = "Passwords don't match"
                        else -> {
                            isSubmitting = true
                            scope.launch {
                                when (val result = onReset(backupCode, newPassword)) {
                                    is com.ashudialer.app.data.PrivateSpaceResetResult.Success -> onSuccess()
                                    is com.ashudialer.app.data.PrivateSpaceResetResult.Failure -> errorText = result.message
                                }
                                isSubmitting = false
                            }
                        }
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Text(if (isSubmitting) "Resetting…" else "Reset password", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LabeledField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    palette: com.ashudialer.app.ui.theme.DialerPalette
) {
    Box(
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
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
