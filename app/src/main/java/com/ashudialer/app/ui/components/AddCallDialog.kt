package com.ashudialer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ashudialer.app.data.Contact
import com.ashudialer.app.ui.theme.LocalDialerPalette

/** Full contact picker for Add call. Selecting a contact immediately starts the second call. */
@Composable
fun AddCallDialog(
    contacts: List<Contact>,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit
) {
    val palette = LocalDialerPalette.current
    var query by remember { mutableStateOf("") }
    var manualNumber by remember { mutableStateOf("") }

    val filtered = remember(contacts, query) {
        val unique = contacts.distinctBy { it.id }
        if (query.isBlank()) unique
        else {
            val digits = query.filter(Char::isDigit)
            unique.filter { contact ->
                contact.displayName.contains(query, ignoreCase = true) ||
                    (digits.isNotEmpty() && contact.phoneNumber.filter(Char::isDigit).contains(digits))
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .imePadding()
                .clip(RoundedCornerShape(24.dp))
                .background(palette.background)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Add call", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Text("Choose a contact for the second call", fontSize = 12.5.sp, color = palette.textSecondary)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = palette.textSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.searchBackground)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isBlank()) Text("Search contacts", color = palette.textSecondary, fontSize = 15.sp)
                        inner()
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.id }) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onCall(contact.phoneNumber) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(
                            name = contact.displayName,
                            photoUri = contact.photoUri,
                            size = 46.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(contact.displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                            Text("${contact.numberLabel} • ${contact.phoneNumber}", fontSize = 12.sp, color = palette.textSecondary)
                        }
                        Icon(Icons.Filled.Phone, contentDescription = "Call", tint = palette.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Dial a number", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = manualNumber,
                    onValueChange = { input -> manualNumber = input.filter { it.isDigit() || it == '+' || it == '*' || it == '#' } },
                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(palette.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.searchBackground)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    decorationBox = { inner ->
                        if (manualNumber.isBlank()) Text("Enter number", color = palette.textSecondary, fontSize = 15.sp)
                        inner()
                    }
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { if (manualNumber.isNotBlank()) onCall(manualNumber) },
                    enabled = manualNumber.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) { Text("Call", fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(2.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Cancel", color = palette.textSecondary)
            }
        }
    }
}
