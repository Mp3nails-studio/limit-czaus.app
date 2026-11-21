package com.example.limit

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pokazuje zapisany błąd z poprzedniego uruchomienia, z przyciskiem kopiowania. */
@Composable
fun CrashDialog(text: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Poprzednio wystąpił błąd") },
        text = {
            Column {
                Text(
                    "Kliknij „Kopiuj” i wklej ten tekst w rozmowie, to da się naprawić.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ctx.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("blad", text))
            }) { Text("Kopiuj") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Zamknij") }
        }
    )
}
