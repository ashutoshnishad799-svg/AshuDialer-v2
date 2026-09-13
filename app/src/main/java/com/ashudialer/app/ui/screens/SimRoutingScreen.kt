package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ashudialer.app.data.db.SimRoutingEntity
import com.ashudialer.app.telecom.SimAccount
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard


@Composable
fun SimRoutingScreen(
    availableSims: List<SimAccount>,
    rules: List<SimRoutingEntity>,
    onBack: () -> Unit,
    onAddRule: (phoneNumber: String, simAccountId: String) -> Unit,
    onRemoveRule: (phoneNumber: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current
    var showAddSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = palette.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("SIM Routing", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        Text(
            "Always call these numbers from a specific SIM.",
            fontSize = 13.sp, color = palette.textSecondary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)
        )

        if (rules.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.SimCard, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(10.dp))
                Text("No routing rules yet", fontSize = 14.sp, color = palette.textSecondary)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().glassCard(palette, 18.dp)
                    ) {
                        rules.forEachIndexed { index, rule ->
                            val simLabel = availableSims.firstOrNull { it.handle.id == rule.preferredSimAccountId }?.label ?: "Unknown SIM"
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.phoneNumber, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                                    Text(simLabel, fontSize = 12.5.sp, color = palette.accent)
                                }
                                IconButton(onClick = { onRemoveRule(rule.phoneNumber) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = palette.danger, modifier = Modifier.size(18.dp))
                                }
                            }
                            if (index != rules.lastIndex) {
                                HorizontalDivider(color = palette.cardBorder, thickness = 1.dp)
                            }
                        }
                    }
                    Spacer(Modifier.height(80.dp))
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.BottomEnd) {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = palette.accent,
                shape = CircleShape
            ) {
                Icon(Icons.Filled.SimCard, contentDescription = "Add rule", tint = Color.White)
            }
        }
    }

    if (showAddSheet) {
        AddRoutingRuleDialog(
            availableSims = availableSims,
            onDismiss = { showAddSheet = false },
            onConfirm = { number, simId ->
                onAddRule(number, simId)
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun AddRoutingRuleDialog(
    availableSims: List<SimAccount>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val palette = LocalDialerPalette.current
    var number by remember { mutableStateOf("") }
    var selectedSimId by remember { mutableStateOf(availableSims.firstOrNull()?.handle?.id ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(palette, 24.dp)
                .padding(20.dp)
        ) {
            Text("Route a number", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.searchBackground)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (number.isEmpty()) Text("Phone number", color = palette.textSecondary, fontSize = 14.sp)
                BasicTextField(
                    value = number,
                    onValueChange = { number = it.filter { c -> c.isDigit() || c == '+' } },
                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("Use SIM", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = palette.textSecondary)
            Spacer(Modifier.height(8.dp))
            availableSims.forEach { sim ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedSimId = sim.handle.id }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedSimId == sim.handle.id,
                        onClick = { selectedSimId = sim.handle.id },
                        colors = RadioButtonDefaults.colors(selectedColor = palette.accent)
                    )
                    Text(sim.label, fontSize = 14.sp, color = palette.textPrimary)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = palette.textSecondary) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { if (number.isNotBlank() && selectedSimId.isNotBlank()) onConfirm(number, selectedSimId) },
                    enabled = number.isNotBlank() && selectedSimId.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Save") }
            }
        }
    }
}
