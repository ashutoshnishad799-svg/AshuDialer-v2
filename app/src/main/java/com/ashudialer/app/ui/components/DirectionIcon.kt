package com.ashudialer.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashudialer.app.data.db.CallDirection
import com.ashudialer.app.ui.theme.LocalDialerPalette

@Composable
fun DirectionIcon(direction: CallDirection, modifier: Modifier = Modifier) {
    val palette = LocalDialerPalette.current
    val color = if (direction == CallDirection.MISSED) palette.danger else palette.textSecondary

    when (direction) {
        CallDirection.OUTGOING -> Icon(
            Icons.Filled.CallMade, contentDescription = "Outgoing",
            tint = color, modifier = modifier.size(14.dp)
        )
        CallDirection.INCOMING -> Icon(
            Icons.Filled.CallReceived, contentDescription = "Incoming",
            tint = color, modifier = modifier.size(14.dp)
        )
        CallDirection.MISSED -> Icon(
            Icons.Filled.CallMissed, contentDescription = "Missed",
            tint = color, modifier = modifier.size(14.dp)
        )
        CallDirection.REJECTED -> Icon(
            Icons.Filled.CallMissed, contentDescription = "Rejected",
            tint = color, modifier = modifier.size(14.dp)
        )
    }
}

@Composable
fun BothWayIcon(modifier: Modifier = Modifier) {
    val palette = LocalDialerPalette.current
    Row {
        Icon(
            Icons.Filled.CallReceived, contentDescription = null,
            tint = palette.textSecondary, modifier = modifier.size(13.dp)
        )
        Icon(
            Icons.Filled.CallMade, contentDescription = null,
            tint = palette.textSecondary, modifier = modifier.size(13.dp)
        )
    }
}
