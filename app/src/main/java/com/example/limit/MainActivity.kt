package com.example.limit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var choice by remember { mutableStateOf(Prefs.colorChoice(this@MainActivity)) }
            LimitTheme(choice) {
                var showWelcome by remember { mutableStateOf(!Prefs.welcomeSeen(this@MainActivity)) }
                if (showWelcome) {
                    WelcomeScreen {
                        Prefs.setWelcomeSeen(this@MainActivity)
                        showWelcome = false
                    }
                } else {
                    AppRoot(
                        choice = choice,
                        onChoice = {
                            Prefs.setColorChoice(this@MainActivity, it)
                            choice = it
                        }
                    )
                }
            }
        }
    }
}

private fun notificationsGranted(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

private fun formatTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0) + 999) / 1000
    return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

/** Dolna nawigacja: Strona główna / Ustawienia. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(choice: String, onChoice: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(if (tab == 0) "Limit czasu" else "Ustawienia") })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Strona główna") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Ustawienia") }
                )
            }
        }
    ) { padding ->
        if (tab == 0) HomeScreen(padding) else SettingsScreen(padding, choice, onChoice)
    }
}

@Composable
fun HomeScreen(padding: PaddingValues) {
    val ctx = LocalContext.current

    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var hasNotif by remember { mutableStateOf(notificationsGranted(ctx)) }
    var hasUsage by remember { mutableStateOf(UsageAccess.granted(ctx)) }
    var endTime by remember { mutableLongStateOf(Prefs.endTime(ctx)) }
    var locked by remember { mutableStateOf(Prefs.locked(ctx)) }
    var pausedAt by remember { mutableLongStateOf(Prefs.pausedAt(ctx)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var hours by rememberSaveable { mutableStateOf(0f) }
    var minutes by rememberSaveable { mutableStateOf(30f) }
    var showCancel by remember { mutableStateOf(false) }
    var crash by remember { mutableStateOf(CrashLog.read(ctx)) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasNotif = it }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasOverlay = Settings.canDrawOverlays(ctx)
        hasNotif = notificationsGranted(ctx)
        hasUsage = UsageAccess.granted(ctx)
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            endTime = Prefs.endTime(ctx)
            locked = Prefs.locked(ctx)
            pausedAt = Prefs.pausedAt(ctx)
            delay(500)
        }
    }

    val paused = pausedAt > 0L
    val running = endTime > 0L && !locked && (paused || now < endTime)
    val totalMs = (hours.toInt() * 60L + minutes.toInt()) * 60_000L

    run {
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasOverlay) {
                PermissionCard(
                    text = "Potrzebne uprawnienie: wyświetlanie nad innymi aplikacjami. Bez niego ekran „Czas minął!” się nie pokaże.",
                    onClick = {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}")
                            )
                        )
                    }
                )
            }
            if (!hasUsage) {
                PermissionCard(
                    text = "Potrzebne uprawnienie: dostęp do danych o użyciu. Dzięki niemu podczas blokady da się wejść w Ustawienia z panelu szybkich ustawień (np. żeby odinstalować aplikację), a po wyjściu blokada wraca.",
                    onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
            }
            if (!hasNotif) {
                PermissionCard(
                    text = "Potrzebne uprawnienie: powiadomienia, żeby pokazywać ile czasu zostało.",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }

            if (locked) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Czas minął! Telefon jest zablokowany.",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else if (running) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (paused) "Pauza (telefon zablokowany), pozostało" else "Pozostało",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            formatTime(if (paused) endTime - pausedAt else endTime - now),
                            style = MaterialTheme.typography.displayLarge
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showCancel = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Anuluj limit (wymaga hasła)") }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Ustaw limit", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${hours.toInt()} h ${minutes.toInt()} min",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text("Godziny", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = hours,
                            onValueChange = { hours = it },
                            valueRange = 0f..12f,
                            steps = 11
                        )
                        Text("Minuty", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = minutes,
                            onValueChange = { minutes = it },
                            valueRange = 0f..59f,
                            steps = 58
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = { hours = 0f; minutes = 15f },
                                label = { Text("15 min") })
                            AssistChip(
                                onClick = { hours = 0f; minutes = 30f },
                                label = { Text("30 min") })
                            AssistChip(
                                onClick = { hours = 1f; minutes = 0f },
                                label = { Text("1 h") })
                            AssistChip(
                                onClick = { hours = 2f; minutes = 0f },
                                label = { Text("2 h") })
                        }
                    }
                }

                Button(
                    enabled = hasOverlay && hasUsage && totalMs > 0,
                    onClick = {
                        if (!hasNotif && Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            LimitService.start(ctx, totalMs)
                            endTime = Prefs.endTime(ctx)
                            locked = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Start limitu") }
            }
        }
    }

    crash?.let { text ->
        CrashDialog(text = text, onClose = {
            CrashLog.clear(ctx)
            crash = null
        })
    }

    if (showCancel) {
        CancelDialog(
            onDismiss = { showCancel = false },
            onCorrect = {
                showCancel = false
                LimitService.cancel(ctx)
                endTime = 0L
                locked = false
            }
        )
    }
}

@Composable
private fun PermissionCard(text: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick) { Text("Przyznaj") }
        }
    }
}

@Composable
private fun CancelDialog(onDismiss: () -> Unit, onCorrect: () -> Unit) {
    val ctx = LocalContext.current
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anulować limit?") },
        text = {
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it; error = false },
                label = { Text("Hasło") },
                singleLine = true,
                isError = error,
                supportingText = { if (error) Text("Złe hasło") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (pass == Prefs.password(ctx)) onCorrect() else error = true
            }) { Text("Anuluj limit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Wróć") }
        }
    )
}
