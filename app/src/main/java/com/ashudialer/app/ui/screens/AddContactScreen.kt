package com.ashudialer.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.data.ContactAccount
import com.ashudialer.app.ui.components.glassCard
import com.ashudialer.app.ui.components.glassCircle
import com.ashudialer.app.ui.theme.DialerPalette
import com.ashudialer.app.ui.theme.LocalDialerPalette

data class NewContactInput(
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val phoneLabel: String,
    val email: String,
    val homeAddress: String = "",
    val company: String = "",
    val notes: String = "",
    val account: ContactAccount? = null,
    // Cropped JPEG bytes from the avatar picker below, carried through to
    // ContactsRepository.insertContact so the photo is saved as part of the
    // same batch as the rest of the new contact - see MainViewModel.saveNewContact.
    val photoJpegBytes: ByteArray? = null
)

private val phoneLabels = listOf("Mobile", "Home", "Work", "Other")

/**
 * Full screen for creating a contact - a real destination in the
 * OverlayScreen navigation stack (top bar + back arrow, slides in like
 * About/Account/Settings) rather than a Dialog stacked on top of the
 * Contacts list. The old dialog capped itself at 92% height inside a
 * small rounded card, which is what made it feel cramped next to every
 * other screen in the app; this uses the exact same full-bleed layout
 * those screens use.
 *
 * onPickPhoto launches the photo picker + crop flow (same PhotoCropDialog
 * used elsewhere in the app - see MainActivity's pendingCropUri wiring)
 * and hands the cropped JPEG bytes back via the returned preview bitmap
 * so the avatar updates immediately without waiting for a save.
 */
@Composable
fun AddContactScreen(
    prefillNumber: String = "",
    accounts: List<ContactAccount> = emptyList(),
    onBack: () -> Unit,
    onSave: (NewContactInput) -> Unit,
    onPickPhoto: () -> Unit = {},
    // Set by the caller once PhotoCropDialog finishes - null clears any
    // previously picked photo (e.g. if the person cancels a re-crop).
    croppedPhotoBytes: ByteArray? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf(prefillNumber) }
    var phoneLabel by remember { mutableStateOf("Mobile") }
    var email by remember { mutableStateOf("") }
    var homeAddress by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showMore by remember { mutableStateOf(false) }
    var selectedAccount by remember(accounts) { mutableStateOf(accounts.firstOrNull()) }

    // Mirrors the caller's croppedPhotoBytes into local state so a fresh
    // crop always replaces whatever was picked before, but the screen
    // still owns its own render state rather than re-deriving a bitmap
    // from raw bytes on every recomposition.
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(croppedPhotoBytes) {
        if (croppedPhotoBytes != null) photoBytes = croppedPhotoBytes
    }
    val photoBitmap = remember(photoBytes) {
        photoBytes?.let { bytes ->
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    val canSave = firstName.isNotBlank() && phoneNumber.isNotBlank()
    val fullName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.glassCircle(palette)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("New contact", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            // Identity header - the same idea as InCallActivity's scalloped
            // avatar (a large circular focal point up top), scaled down for
            // a form context. Initials update live as the person types so
            // the header always reflects who's actually being saved. Tapping
            // it launches the photo picker + crop flow; once a photo is set,
            // it renders in place of the initials, with a small camera badge
            // that stays tappable to change the photo again.
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .glassCircle(palette, tintAlpha = if (photoBitmap != null) 0f else 0.55f)
                            .then(if (photoBitmap == null) Modifier.background(palette.accentSoft, CircleShape) else Modifier)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onPickPhoto
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            photoBitmap != null -> Image(
                                bitmap = photoBitmap,
                                contentDescription = "Contact photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                            fullName.isNotBlank() -> Text(
                                text = fullName.trim().take(1).uppercase(),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.accent
                            )
                            else -> Icon(
                                Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }
                    // Camera badge - always visible so it's obvious the
                    // avatar is tappable even before any photo is picked.
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .glassCircle(palette, tintAlpha = 0.85f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onPickPhoto
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "Change photo",
                            tint = palette.accent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = fullName.ifBlank { "New contact" },
                    color = palette.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = phoneNumber.ifBlank { "Add a phone number" },
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
            }

            FormSection(title = "Save to", palette = palette, topPadding = 20.dp) {
                if (accounts.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(accounts) { account ->
                            val selected = selectedAccount == account
                            FilterChip(
                                selected = selected,
                                onClick = { selectedAccount = account },
                                label = { Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = {
                                    Icon(
                                        if (account.isDevice) Icons.Filled.PhoneAndroid else Icons.Filled.AccountCircle,
                                        null,
                                        Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            FormSection(title = "Name", palette = palette) {
                FieldGroup(palette) {
                    LabeledField("First name", firstName, { firstName = it }, palette)
                    FieldDivider(palette)
                    LabeledField("Last name", lastName, { lastName = it }, palette)
                }
            }

            FormSection(title = "Phone", palette = palette) {
                FieldGroup(palette) {
                    LabeledField("Phone number", phoneNumber, { phoneNumber = it }, palette, KeyboardType.Phone)
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(phoneLabels) { label ->
                        FilterChip(selected = phoneLabel == label, onClick = { phoneLabel = label }, label = { Text(label) })
                    }
                }
            }

            FormSection(title = "Email", palette = palette) {
                FieldGroup(palette) {
                    LabeledField("Email address (optional)", email, { email = it }, palette, KeyboardType.Email)
                }
            }

            Column(modifier = Modifier.animateContentSize()) {
                TextButton(
                    onClick = { showMore = !showMore },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.accent)
                ) {
                    Icon(if (showMore) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (showMore) "Hide more details" else "Add more details")
                }

                if (showMore) {
                    FormSection(title = "More details", palette = palette) {
                        FieldGroup(palette) {
                            LabeledField("Home address (optional)", homeAddress, { homeAddress = it }, palette, KeyboardType.Text, minLines = 2)
                            FieldDivider(palette)
                            LabeledField("Company (optional)", company, { company = it }, palette)
                            FieldDivider(palette)
                            LabeledField("Notes (optional)", notes, { notes = it }, palette, KeyboardType.Text, minLines = 3)
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }

    // Bottom-anchored save action, always reachable regardless of scroll
    // position or keyboard state - the previous dialog buried Save at the
    // very end of a scrolling column, so saving a short contact and saving
    // one with "more details" expanded required different amounts of
    // scrolling to reach the same button.
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .glassCard(palette, corner = 0.dp, tintAlpha = 0.85f)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            Button(
                onClick = {
                    onSave(
                        NewContactInput(
                            firstName.trim(), lastName.trim(), phoneNumber.trim(), phoneLabel,
                            email.trim(), homeAddress.trim(), company.trim(), notes.trim(), selectedAccount,
                            photoJpegBytes = photoBytes
                        )
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Icon(Icons.Filled.Check, null, Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save contact", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun FormSection(
    title: String,
    palette: DialerPalette,
    topPadding: androidx.compose.ui.unit.Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = topPadding)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

/** Groups related fields into one continuous glass card instead of separate boxes per field, so a Name or Phone section reads as one unit rather than a stack of disconnected pills. */
@Composable
private fun FieldGroup(palette: DialerPalette, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(palette, corner = 18.dp)
    ) {
        content()
    }
}

@Composable
private fun FieldDivider(palette: DialerPalette) {
    HorizontalDivider(color = palette.cardBorder, thickness = 1.dp, modifier = Modifier.padding(horizontal = 15.dp))
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    palette: DialerPalette,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 11.dp)) {
        Text(label, fontSize = 11.sp, color = palette.textSecondary, fontWeight = FontWeight.Medium)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = minLines == 1,
            minLines = minLines,
            textStyle = TextStyle(color = palette.textPrimary, fontSize = 15.sp, lineHeight = 21.sp),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
        )
    }
}
