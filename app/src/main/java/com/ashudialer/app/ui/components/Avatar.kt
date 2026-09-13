package com.ashudialer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ashudialer.app.ui.theme.LocalDialerPalette

@Composable
fun Avatar(
    name: String,
    photoUri: String?,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val palette = LocalDialerPalette.current

    if (!photoUri.isNullOrBlank()) {
        AsyncImage(
            model = photoUri,
            contentDescription = name,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
        return
    }

    val hue = ((name.firstOrNull()?.code ?: 63) * 37) % 360
    val initials = name.trim().split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

    val backgroundModifier = if (initials.isNotBlank()) {
        Modifier.background(color = palette.avatarBackground)
    } else {
        Modifier.background(brush = Brush.linearGradient(listOf(Color.Gray, Color.DarkGray)))
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(backgroundModifier),
        contentAlignment = Alignment.Center
    ) {
        if (initials.isNotBlank()) {
            Text(
                text = initials,
                color = palette.accent,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.36).sp
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}
