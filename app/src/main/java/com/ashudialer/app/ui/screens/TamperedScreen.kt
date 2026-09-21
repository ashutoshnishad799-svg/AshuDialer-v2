package com.ashudialer.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown instead of the app when IntegrityGuard finds that this copy is not the official build (modified and
 * re-signed). Deliberately self-contained: fixed colours and no app theme or data, because nothing from a
 * copy that cannot be trusted should be loaded.
 */
@Composable
fun TamperedScreen(context: Context, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF10141A))
            .systemBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Not the official app", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "This copy of Ashu Dialer has been changed or was not built by its developer, so it will not open. " +
                "Your calls still work normally. Install the official version from the Releases page.",
            fontSize = 15.sp, color = Color(0xFFB8C0CC), textAlign = TextAlign.Center, lineHeight = 22.sp
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ashutoshnishad799-svg/AshuDialer-v2/releases"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D7DF6))
        ) { Text("Open official downloads", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Close", color = Color.White)
        }
    }
}
