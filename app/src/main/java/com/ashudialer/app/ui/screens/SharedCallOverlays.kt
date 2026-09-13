package com.ashudialer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashudialer.app.telecom.CaptionLine
import com.ashudialer.app.telecom.TypedMessage

/**
 * Shared BoxScope overlay: the live-caption subtitle strip and the
 * collapsible type-to-talk composer button, placed above whatever bottom
 * control bar the calling screen (VideoCallScreen or CallScreen) already
 * has. Must be called as the LAST child inside a Box(fillMaxSize()) so
 * Alignment.BottomCenter/BottomEnd resolve against the whole screen rather
 * than some narrower parent.
 *
 * bottomInsetForCaptions / bottomInsetForComposer let each host screen tell
 * this overlay how tall ITS OWN control bar is, so the strip/composer sit
 * just above it rather than this file guessing a single fixed number that
 * would only be correct for one of the two very differently laid-out call
 * screens.
 */
@Composable
fun BoxScope.CaptionsAndTypeToTalkOverlay(
    captionsAvailable: Boolean,
    captionLines: List<CaptionLine>,
    typeToTalkAvailable: Boolean,
    typedMessages: List<TypedMessage>,
    onSendTypedMessage: (String) -> Unit,
    bottomInsetForCaptions: Dp,
    bottomInsetForComposer: Dp
) {
    // Subtitle-style strip just above the host screen's own control bar,
    // matching how captions render in every mainstream call app (WhatsApp,
    // Meet, stock Phone) rather than inventing a different placement. Only
    // the most recent couple of lines are shown at once - a full scrolling
    // transcript here would compete with the call itself for the person's
    // attention.
    if (captionsAvailable && captionLines.isNotEmpty()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = bottomInsetForCaptions, start = 24.dp, end = 24.dp)
        ) {
            captionLines.takeLast(2).forEach { line ->
                Text(
                    line.text,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = if (line.isFinal) FontWeight.Normal else FontWeight.Light,
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }

    if (typeToTalkAvailable) {
        var isComposerOpen by remember { mutableStateOf(false) }
        var draftText by remember { mutableStateOf("") }

        if (isComposerOpen) {
            TypeToTalkComposer(
                draftText = draftText,
                onDraftChange = { draftText = it },
                onSend = {
                    if (draftText.isNotBlank()) {
                        onSendTypedMessage(draftText)
                        draftText = ""
                    }
                },
                onClose = { isComposerOpen = false },
                recentMessages = typedMessages,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = bottomInsetForComposer, start = 16.dp, end = 16.dp)
            )
        } else {
            // Collapsed state: a single small button, not a persistent text
            // field - someone who can hear and speak normally should see
            // nothing different about this call screen at all beyond one
            // extra icon they can ignore.
            IconButton(
                onClick = { isComposerOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = bottomInsetForComposer.coerceAtMost(20.dp), end = 8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Icon(
                    Icons.Filled.Chat,
                    contentDescription = "Type a message to speak into the call",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TypeToTalkComposer(
    draftText: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
    recentMessages: List<TypedMessage>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C22).copy(alpha = 0.92f))
            .padding(12.dp)
    ) {
        val speaking = recentMessages.lastOrNull { it.isSpeaking }
        if (speaking != null) {
            Text(
                "Speaking: \"${speaking.text}\"",
                color = Color(0xFF7FE0D6),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = draftText,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type to speak into the call…", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) },
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF7FE0D6),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    cursorColor = Color.White
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onSend() })
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = draftText.isNotBlank(),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (draftText.isNotBlank()) Color(0xFF7FE0D6) else Color.White.copy(alpha = 0.16f))
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Speak this into the call",
                    tint = if (draftText.isNotBlank()) Color(0xFF1C1C22) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }
        }
    }
}
