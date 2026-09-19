package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

@Composable
fun PrivateSpaceUnlockScreen(
    onBack: () -> Unit,
    onVerifyPassword: suspend (String) -> Boolean,
    onResetWithBackupCode: suspend (code: String, newPassword: String) -> com.ashudialer.app.data.PrivateSpaceResetResult,
    onUnlocked: () -> Unit,
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

    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var failedAttempts by remember { mutableStateOf(0) }

    fun attemptUnlock() {
        if (password.isEmpty()) return
        isSubmitting = true
        scope.launch {
            val ok = onVerifyPassword(password)
            isSubmitting = false
            if (ok) {
                onUnlocked()
            } else {
                failedAttempts += 1
                errorText = "Incorrect password"
                password = ""
            }
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
            Text("Enter your password to continue", fontSize = 13.sp, color = palette.textSecondary)

            Spacer(Modifier.height(24.dp))

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
                onClick = { attemptUnlock() },
                enabled = !isSubmitting && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Text(if (isSubmitting) "Checking…" else "Unlock", fontWeight = FontWeight.SemiBold)
            }

            // Only surfaced after a failed attempt - keeping it hidden
            // otherwise avoids the recovery path being an easy first stop
            // for someone who isn't the actual owner just poking around.
            if (failedAttempts > 0) {
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
