/*
 * Ashu Phone
 * Copyright (C) 2026 Ashutosh Nishad
 *
 * This file is part of Ashu Phone, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version.
 * See the LICENSE and NOTICE files in the project root.
 * This program comes with ABSOLUTELY NO WARRANTY.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.ui.theme.LocalDialerPalette
import com.ashudialer.app.ui.components.glassCard

private data class PolicySection(val heading: String, val body: String)

private val sections = listOf(
    PolicySection(
        "What this app accesses",
        "Ashu Dialer reads your call log, contacts, and phone state so it can " +
            "function as your dialer — showing recent calls, matching incoming numbers " +
            "to saved contacts, and letting you place calls. It requests microphone access " +
            "only if you turn on call recording."
    ),
    PolicySection(
        "Where your data lives",
        "Your call history, contacts cache, blocked numbers, notes, recordings and theme " +
            "preference stay on your device in a local, private database. They are only " +
            "uploaded if you sign in and turn on Cloud Backup. The only data that leaves the " +
            "device without that opt-in is described in \"Video calling\" and \"Anonymous " +
            "usage statistics\" below."
    ),
    PolicySection(
        "Video calling and your phone number",
        "To let another Ashu Dialer user reach you by video, the app creates an anonymous " +
            "Firebase account for your install and, once you have saved your own phone number, " +
            "publishes a mapping of the last 10 digits of that number to that anonymous account " +
            "ID in a shared directory. Another signed-in user of the app can look up a single " +
            "number in that directory to find out whether it is reachable; the directory cannot " +
            "be listed or downloaded in bulk. While a video call is being set up, temporary " +
            "connection details (call signalling) are stored in Firebase and deleted when the " +
            "call ends. You can stop this by removing your saved number in Settings or by deleting " +
            "your account."
    ),
    PolicySection(
        "Anonymous usage statistics",
        "The app sends anonymous, aggregate events through Firebase Analytics (for example " +
            "that the app was opened, and which features are switched on). It never sends phone " +
            "numbers, contact names, call history or recordings."
    ),
    PolicySection(
        "Cloud Backup (optional)",
        "If you sign in with your Google account and turn on Cloud Backup, your call log, " +
            "contacts cache, blocked-number list, and settings are synced to your private " +
            "Firebase account storage so you can restore them on a new device. This is off by " +
            "default and only activates after you sign in and opt in."
    ),
    PolicySection(
        "Call recording (optional)",
        "If you enable call recording in Settings, recordings are saved locally to your " +
            "device only. They are never uploaded automatically. Call recording laws vary by " +
            "region — you're responsible for complying with local regulations, including " +
            "informing the other party where required."
    ),
    PolicySection(
        "Spam protection",
        "The Protect tab checks incoming numbers against a local block-list you control. " +
            "No call data is sent to a third-party spam-detection service."
    ),
    PolicySection(
        "What we don't do",
        "We don't sell your data. We don't share your call log or contacts with advertisers. " +
            "We don't run ads in this app. The app's source code is public, so you can verify " +
            "every statement in this policy yourself."
    ),
    PolicySection(
        "Your control",
        "You can clear call history, remove blocked numbers, revoke permissions, or delete " +
            "your account and all associated cloud data at any time from Settings."
    ),
    PolicySection(
        "Contact",
        "Questions about this policy? Reach out via Instagram @ashtosh_07x."
    )
)

@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
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
            Text("Privacy Policy", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                "Last updated: August 2026",
                fontSize = 12.5.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 18.dp)
            )

            sections.forEach { section ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(palette, 16.dp)
                        .padding(16.dp)
                ) {
                    Text(section.heading, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(section.body, fontSize = 13.5.sp, color = palette.textSecondary, lineHeight = 19.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
