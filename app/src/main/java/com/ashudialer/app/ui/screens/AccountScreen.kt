package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ashudialer.app.data.SignedInUser
import com.ashudialer.app.ui.theme.LocalDialerPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ashudialer.app.ui.components.glassCard

enum class BackupState { IDLE, IN_PROGRESS, SUCCESS, FAILED }

@Composable
fun AccountScreen(
    user: SignedInUser?,
    cloudBackupEnabled: Boolean,
    lastBackedUpAtMillis: Long,
    backupState: BackupState,
    myPhoneNumber: String,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onRegisterLocal: suspend (email: String, password: String, name: String) -> com.ashudialer.app.data.LocalAuthResult,
    onSignInLocal: suspend (email: String, password: String) -> com.ashudialer.app.data.LocalAuthResult,
    onSignOut: () -> Unit,
    onToggleCloudBackup: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onSaveMyPhoneNumber: (String) -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Account", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            if (user == null) {
                SignedOutContent(onSignIn, onRegisterLocal, onSignInLocal)
            } else {
                SignedInContent(
                    user = user,
                    cloudBackupEnabled = cloudBackupEnabled,
                    lastBackedUpAtMillis = lastBackedUpAtMillis,
                    backupState = backupState,
                    myPhoneNumber = myPhoneNumber,
                    onSignOut = onSignOut,
                    onToggleCloudBackup = onToggleCloudBackup,
                    onBackupNow = onBackupNow,
                    onSaveMyPhoneNumber = onSaveMyPhoneNumber,
                    onDeleteAccount = onDeleteAccount
                )
            }
        }
    }
}

@Composable
private fun SignedOutContent(
    onSignIn: () -> Unit,
    onRegisterLocal: suspend (email: String, password: String, name: String) -> com.ashudialer.app.data.LocalAuthResult,
    onSignInLocal: suspend (email: String, password: String) -> com.ashudialer.app.data.LocalAuthResult
) {
    val palette = LocalDialerPalette.current
    var showEmailForm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(88.dp).clip(CircleShape).background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = palette.accent, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Sign in to back up your data", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your call log, blocked numbers, and settings sync to your account so you can restore them on a new device.",
            fontSize = 13.5.sp, color = palette.textSecondary, textAlign = TextAlign.Center, lineHeight = 19.sp
        )
        Spacer(Modifier.height(28.dp))

        if (!showEmailForm) {
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showEmailForm = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Sign in with email", fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Email accounts stay on this device only — they don't sync data across devices.",
                fontSize = 11.5.sp, color = palette.textSecondary, textAlign = TextAlign.Center, lineHeight = 15.sp
            )
        } else {
            EmailAuthForm(
                onBack = { showEmailForm = false },
                onRegister = onRegisterLocal,
                onSignIn = onSignInLocal,
                palette = palette
            )
        }
    }
}

/**
 * A single form that toggles between "sign in" and "create account" mode
 * (isRegisterMode) rather than two separate screens - a person's first
 * instinct on seeing an email field is usually to just try their email and
 * a password, and this handles either intent (existing account or new one)
 * from the one field set, only branching to a distinct explicit action
 * (register vs sign in) at submit time.
 */
@Composable
private fun EmailAuthForm(
    onBack: () -> Unit,
    onRegister: suspend (email: String, password: String, name: String) -> com.ashudialer.app.data.LocalAuthResult,
    onSignIn: suspend (email: String, password: String) -> com.ashudialer.app.data.LocalAuthResult,
    palette: com.ashudialer.app.ui.theme.DialerPalette
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    fun submit() {
        errorText = null
        isSubmitting = true
        scope.launch {
            val result = if (isRegisterMode) {
                onRegister(email, password, name)
            } else {
                onSignIn(email, password)
            }
            isSubmitting = false
            if (result is com.ashudialer.app.data.LocalAuthResult.Failure) {
                errorText = result.message
            }
            // On Success, currentUser (observed by the parent AccountScreen)
            // updates on its own via the repository's Flow - no explicit
            // navigation call needed here.
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isRegisterMode) {
            EmailFormField(value = name, onValueChange = { name = it; errorText = null }, placeholder = "Name", keyboardType = androidx.compose.ui.text.input.KeyboardType.Text, palette = palette)
            Spacer(Modifier.height(10.dp))
        }
        EmailFormField(value = email, onValueChange = { email = it; errorText = null }, placeholder = "Email", keyboardType = androidx.compose.ui.text.input.KeyboardType.Email, palette = palette)
        Spacer(Modifier.height(10.dp))
        EmailFormField(value = password, onValueChange = { password = it; errorText = null }, placeholder = "Password", isPassword = true, keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, palette = palette)

        if (errorText != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorText!!, fontSize = 12.5.sp, color = palette.danger, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { submit() },
            enabled = !isSubmitting && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
        ) {
            Text(
                if (isSubmitting) "Please wait…" else if (isRegisterMode) "Create account" else "Sign in",
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            if (isRegisterMode) "Already have an account? Sign in" else "New here? Create an account",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = palette.accent,
            modifier = Modifier.clickable { isRegisterMode = !isRegisterMode; errorText = null }
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "Back",
            fontSize = 13.sp,
            color = palette.textSecondary,
            modifier = Modifier.clickable(onClick = onBack)
        )
    }
}

@Composable
private fun EmailFormField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    keyboardType: androidx.compose.ui.text.input.KeyboardType,
    palette: com.ashudialer.app.ui.theme.DialerPalette
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
            visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SignedInContent(
    user: SignedInUser,
    cloudBackupEnabled: Boolean,
    lastBackedUpAtMillis: Long,
    backupState: BackupState,
    myPhoneNumber: String,
    onSignOut: () -> Unit,
    onToggleCloudBackup: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onSaveMyPhoneNumber: (String) -> Unit,
    onDeleteAccount: () -> Unit
) {
    val palette = LocalDialerPalette.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(palette, 18.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!user.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = user.photoUrl, contentDescription = null,
                modifier = Modifier.size(52.dp).clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = palette.accent)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName ?: "Signed in", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            if (!user.email.isNullOrBlank()) {
                Text(user.email, fontSize = 13.sp, color = palette.textSecondary)
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(palette, 18.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (cloudBackupEnabled) Icons.Filled.CloudDone else Icons.Filled.CloudUpload,
            contentDescription = null, tint = palette.accent
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Cloud Backup", fontWeight = FontWeight.SemiBold, color = palette.textPrimary, fontSize = 15.sp)
            Text(
                text = if (lastBackedUpAtMillis > 0) "Last backed up: ${formatBackupTime(lastBackedUpAtMillis)}" else "Not backed up yet",
                fontSize = 12.sp, color = palette.textSecondary
            )
        }
        Switch(checked = cloudBackupEnabled, onCheckedChange = onToggleCloudBackup)
    }

    if (cloudBackupEnabled) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onBackupNow,
            enabled = backupState != BackupState.IN_PROGRESS,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                when (backupState) {
                    BackupState.IN_PROGRESS -> "Backing up…"
                    BackupState.SUCCESS -> "Backed up ✓"
                    BackupState.FAILED -> "Failed — tap to retry"
                    BackupState.IDLE -> "Back up now"
                }
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    VideoCallingSection(myPhoneNumber = myPhoneNumber, onSave = onSaveMyPhoneNumber)

    Spacer(Modifier.height(24.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(palette, 18.dp)
    ) {
        Text(
            "Sign out",
            color = palette.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().clickable { onSignOut() }.padding(16.dp)
        )
        HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
        Text(
            "Delete account & cloud data",
            color = palette.danger,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().clickable { showDeleteConfirm = true }.padding(16.dp)
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete account?") },
            text = { Text("This permanently deletes your cloud backup and signs you out. Your data stays on this device.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteAccount()
                }) { Text("Delete", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatBackupTime(millis: Long): String {
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}

@Composable
private fun VideoCallingSection(myPhoneNumber: String, onSave: (String) -> Unit) {
    val palette = LocalDialerPalette.current
    var editing by remember(myPhoneNumber) { mutableStateOf(myPhoneNumber.isBlank()) }
    var draft by remember(myPhoneNumber) { mutableStateOf(myPhoneNumber) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(palette, 18.dp)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Videocam, contentDescription = null, tint = palette.accent)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Video Calling", fontWeight = FontWeight.SemiBold, color = palette.textPrimary, fontSize = 15.sp)
                Text(
                    "Other AshuPhone users can reach you by video call once you confirm your number below.",
                    fontSize = 12.sp, color = palette.textSecondary, lineHeight = 16.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (editing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.searchBackground)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (draft.isEmpty()) {
                    Text("Your phone number", color = palette.textSecondary, fontSize = 14.sp)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { input -> draft = input.filter { it.isDigit() || it == '+' }.take(15) },
                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { if (draft.isNotBlank()) { onSave(draft); editing = false } },
                enabled = draft.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { editing = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(myPhoneNumber, color = palette.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("Edit", color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "When you call someone on the same carrier as you, AshuPhone suggests a regular VoLTE call if your plan includes free on-network calling. Otherwise, or across different carriers, video calls connect over the internet.",
            fontSize = 11.5.sp, color = palette.textSecondary.copy(alpha = 0.85f), lineHeight = 16.sp
        )
    }
}
