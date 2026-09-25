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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
 * Two-step first-time setup: choose a password, then (only after that
 * succeeds) see a generated backup code exactly once. The backup code step
 * can't be skipped past or navigated back into once dismissed - it only
 * ever exists in server-side hash form after that, matching how the
 * repository's setup() is designed (see PrivateSpaceRepository.setup).
 */
@Composable
fun PrivateSpaceSetupScreen(
    onBack: () -> Unit,
    onSetup: suspend (String) -> com.ashudialer.app.data.PrivateSpaceSetupResult,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var step by remember { mutableStateOf(0) } // 0 = choose password, 1 = show backup code
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var backupCode by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step == 0) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
                }
                Spacer(Modifier.width(4.dp))
            } else {
                Spacer(Modifier.width(52.dp))
            }
            Text("Private Space", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        if (step == 0) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(palette.accentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = palette.accent, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Set a password", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    "This protects the locked numbers, call manager, and history inside Private Space. You'll need it every time you open it.",
                    fontSize = 13.sp, color = palette.textSecondary, textAlign = TextAlign.Center, lineHeight = 18.sp
                )

                Spacer(Modifier.height(24.dp))

                PasswordField(
                    value = password,
                    onValueChange = { password = it; errorText = null },
                    visible = passwordVisible,
                    onToggleVisible = { passwordVisible = !passwordVisible },
                    placeholder = "Password",
                    palette = palette
                )
                Spacer(Modifier.height(10.dp))
                PasswordField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorText = null },
                    visible = passwordVisible,
                    onToggleVisible = { passwordVisible = !passwordVisible },
                    placeholder = "Confirm password",
                    palette = palette
                )

                if (errorText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorText!!, fontSize = 12.5.sp, color = palette.danger, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        when {
                            password.length < 4 -> errorText = "Password must be at least 4 characters"
                            password != confirmPassword -> errorText = "Passwords don't match"
                            else -> {
                                isSubmitting = true
                                scope.launch {
                                    when (val result = onSetup(password)) {
                                        is com.ashudialer.app.data.PrivateSpaceSetupResult.Success -> {
                                            backupCode = result.backupCode
                                            step = 1
                                        }
                                        is com.ashudialer.app.data.PrivateSpaceSetupResult.Failure -> {
                                            errorText = result.message
                                        }
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
                    Text(if (isSubmitting) "Setting up…" else "Continue", fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            BackupCodeStep(backupCode = backupCode, palette = palette, onDone = onComplete)
        }
    }
}

@Composable
private fun BackupCodeStep(
    backupCode: String,
    palette: com.ashudialer.app.ui.theme.DialerPalette,
    onDone: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var confirmedSaved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = palette.accent, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(12.dp))
        Text("Save your backup code", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "If you forget your password, this code is the only way back in. It's shown only this once.",
            fontSize = 13.sp, color = palette.textSecondary, textAlign = TextAlign.Center, lineHeight = 18.sp
        )

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(palette, 16.dp)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                backupCode,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { clipboard.setText(AnnotatedString(backupCode)) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy backup code", tint = palette.accent)
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().clickable { confirmedSaved = !confirmedSaved },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (confirmedSaved) palette.accent else palette.cardBackground),
                contentAlignment = Alignment.Center
            ) {
                if (confirmedSaved) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = palette.solidBackground, modifier = Modifier.size(12.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text("I've saved this code somewhere safe", fontSize = 13.5.sp, color = palette.textPrimary)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Button(
            onClick = onDone,
            enabled = confirmedSaved,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
        ) {
            Text("Done", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    placeholder: String,
    palette: com.ashudialer.app.ui.theme.DialerPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(palette, 14.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, color = palette.textSecondary, fontSize = 15.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = palette.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(palette.accent),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
        }
        IconButton(onClick = onToggleVisible, modifier = Modifier.size(22.dp)) {
            Icon(
                if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (visible) "Hide password" else "Show password",
                tint = palette.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
