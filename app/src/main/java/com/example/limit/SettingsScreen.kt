package com.example.limit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Zakładka Ustawienia: kolory aplikacji i zmiana hasła. */
@Composable
fun SettingsScreen(padding: PaddingValues, choice: String, onChoice: (String) -> Unit) {
    Column(
        modifier = Modifier
            .padding(padding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ColorsCard(choice, onChoice)
        PasswordCard()
    }
}

@Composable
private fun ColorsCard(choice: String, onChoice: (String) -> Unit) {
    // Jeśli zapisano "dynamic", a telefon go nie obsługuje (Android < 12), pokazujemy pierwszy kolor.
    val effective =
        if (choice == AppColors.DYNAMIC && !AppColors.dynamicAvailable) AppColors.seedChoice(0)
        else choice
    val dynamicAvailable = AppColors.dynamicAvailable

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Kolory aplikacji", style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = dynamicAvailable) { onChoice(AppColors.DYNAMIC) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = effective == AppColors.DYNAMIC,
                    onClick = null,
                    enabled = dynamicAvailable
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text("Dynamiczne", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (dynamicAvailable) "Kolory dobrane do tapety telefonu"
                        else "Dostępne od Androida 12",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("Własny kolor", style = MaterialTheme.typography.labelLarge)

            AppColors.SEEDS.chunked(3).forEachIndexed { row, seeds ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    seeds.forEachIndexed { col, seed ->
                        val index = row * 3 + col
                        val selected = effective == AppColors.seedChoice(index)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onChoice(AppColors.seedChoice(index)) }
                                .padding(4.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(AppColors.swatch(seed.hue)))
                                    .then(
                                        if (selected) Modifier.border(
                                            3.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                            CircleShape
                                        ) else Modifier
                                    )
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }
                            Text(
                                seed.name,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PasswordMessage(val text: String, val ok: Boolean)

private fun digitsOnly(s: String) = s.filter { it.isDigit() }.take(12)

@Composable
private fun PasswordCard() {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<PasswordMessage?>(null) }

    val keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Zmiana hasła", style = MaterialTheme.typography.titleLarge)
            Text(
                "Hasło odblokowuje ekran „Czas minął!” i pozwala anulować limit. Od 4 do 12 cyfr.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = current,
                onValueChange = { current = digitsOnly(it); message = null },
                label = { Text("Obecne hasło") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = keyboard,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = new,
                onValueChange = { new = digitsOnly(it); message = null },
                label = { Text("Nowe hasło") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = keyboard,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = repeat,
                onValueChange = { repeat = digitsOnly(it); message = null },
                label = { Text("Powtórz nowe hasło") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = keyboard,
                modifier = Modifier.fillMaxWidth()
            )

            message?.let {
                Text(
                    it.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.ok) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    message = when {
                        current != Prefs.password(ctx) ->
                            PasswordMessage("Złe obecne hasło", false)

                        new.length < 4 ->
                            PasswordMessage("Nowe hasło musi mieć co najmniej 4 cyfry", false)

                        new != repeat ->
                            PasswordMessage("Nowe hasła nie są takie same", false)

                        else -> {
                            Prefs.setPassword(ctx, new)
                            current = ""
                            new = ""
                            repeat = ""
                            PasswordMessage("Hasło zostało zmienione", true)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Zmień hasło") }
        }
    }
}
