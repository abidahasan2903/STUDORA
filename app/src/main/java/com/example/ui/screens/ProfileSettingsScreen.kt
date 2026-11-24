package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.DateTimeUtils
import com.example.core.formatFileSize
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.StudoraTopBar
import com.example.ui.components.openAppSettings
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun ProfileSettingsScreen(
    viewModel: MainViewModel,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val backupBusy by viewModel.backupBusy.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshStorageStats()
    }

    var displayName by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var studentType by remember(profile.studentType) { mutableStateOf(profile.studentType) }
    var academicLevel by remember(profile.academicLevel) { mutableStateOf(profile.academicLevel) }
    var preferredLanguage by remember(profile.preferredLanguage) { mutableStateOf(profile.preferredLanguage) }
    var studyGoals by remember(profile.studyGoals) { mutableStateOf(profile.studyGoals) }

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestoreManifest by remember { mutableStateOf<JSONObject?>(null) }
    var backupStatusMsg by remember { mutableStateOf<String?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri) { res ->
                res.onSuccess { summary ->
                    backupStatusMsg = "Backup exported (${summary.filesCount} items, ${formatFileSize(summary.exportedBytes)})."
                }.onFailure { err ->
                    backupStatusMsg = "Export failed: ${err.localizedMessage}"
                }
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                viewModel.repo.validateBackup(uri).onSuccess { manifest ->
                    pendingRestoreUri = uri
                    pendingRestoreManifest = manifest
                }.onFailure { err ->
                    backupStatusMsg = err.localizedMessage ?: "Invalid backup archive."
                }
            }
        }
    }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = "Profile & Settings",
                subtitle = "Local-first configuration • No account required",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Local Profile Section
            item {
                SettingsCard(title = "Local Student Profile", icon = Icons.Default.Person) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = studentType,
                        onValueChange = { studentType = it },
                        label = { Text("Student Type (e.g. University, High School)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = academicLevel,
                        onValueChange = { academicLevel = it },
                        label = { Text("Academic Level / Semester") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = preferredLanguage,
                        onValueChange = { preferredLanguage = it },
                        label = { Text("Preferred Language (e.g. English, Bangla)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = studyGoals,
                        onValueChange = { studyGoals = it },
                        label = { Text("Study Goals") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        onClick = {
                            viewModel.updateProfile(
                                profile.copy(
                                    displayName = displayName.trim(),
                                    studentType = studentType.trim(),
                                    academicLevel = academicLevel.trim(),
                                    preferredLanguage = preferredLanguage.trim(),
                                    studyGoals = studyGoals.trim()
                                )
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("save_profile_button")
                    ) {
                        Text("Save Profile", maxLines = 1, softWrap = false)
                    }
                }
            }

            // Appearance & Theme
            item {
                SettingsCard(title = "Appearance & Theme", icon = Icons.Default.Palette) {
                    Text("App Theme", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (key, label) ->
                            FilterChip(
                                selected = settings.themeMode == key,
                                onClick = { viewModel.updateTheme(key) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // Audio & Notification Preferences
            item {
                SettingsCard(title = "Notifications & Audio", icon = Icons.Default.Tune) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Local Reminder Notifications", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Task and bill reminders use native Android alarms",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.notificationsEnabled,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(notificationsEnabled = it))
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { openAppSettings(context) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Android Notification & Battery Settings",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        "Default AI Audio Speed: ${settings.ttsSpeechRate}x",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = settings.ttsSpeechRate,
                        onValueChange = { viewModel.updateSettings(settings.copy(ttsSpeechRate = (it * 20).toInt() / 20f)) },
                        valueRange = 0.75f..1.75f
                    )
                }
            }

            // Storage & Local Data Management + Backup / Restore
            item {
                SettingsCard(title = "Local Storage & Backup", icon = Icons.Default.Storage) {
                    storageStats?.let { st ->
                        StorageRow("SQLite Database", formatFileSize(st.databaseBytes))
                        StorageRow("PDFs & Documents", formatFileSize(st.documentsBytes))
                        StorageRow("Images & Scans", formatFileSize(st.imagesBytes))
                        StorageRow("Voice & AI Audio", formatFileSize(st.audioBytes))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        StorageRow("Total App Data", formatFileSize(st.totalAppBytes), bold = true)
                        StorageRow("Available on Phone", formatFileSize(st.availableDeviceBytes))
                    } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "All STUDORA data is stored locally in app-private storage on your phone. Uninstalling STUDORA or clearing app data in Android Settings will remove local files unless you export a backup ZIP first.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    if (backupBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            enabled = !backupBusy,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            onClick = {
                                val fileName = "studora_backup_${System.currentTimeMillis()}.zip"
                                exportBackupLauncher.launch(fileName)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_backup_button")
                        ) {
                            Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export ZIP", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            enabled = !backupBusy,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            onClick = { importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("restore_backup_button")
                        ) {
                            Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restore ZIP", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    if (backupStatusMsg != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            backupStatusMsg!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // About & Privacy + Developer Credit
            item {
                SettingsCard(title = "About STUDORA & Privacy", icon = Icons.Default.Info) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.studora_logo),
                            contentDescription = "STUDORA",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(68.dp)
                                .aspectRatio(1067f / 1014f)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("STUDORA", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Your AI-Powered Student Life Companion",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Developed by Abid Ahasan",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Privacy Commitment: STUDORA works offline-first without accounts or cloud syncing. Your documents, notes, voice recordings, contacts, and wallet records stay on your smartphone and are only sent to the AI service when you explicitly run an AI feature.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (pendingRestoreUri != null && pendingRestoreManifest != null) {
        val created = pendingRestoreManifest!!.optLong("createdAt", 0L)
        val dateText = if (created > 0L) DateTimeUtils.formatDateTime(created) else "Unknown date"
        ConfirmationDialog(
            title = "Restore Backup?",
            message = "This valid STUDORA backup (created $dateText) will replace all current local records and files on this device. This cannot be undone.",
            confirmText = "Restore Now",
            isDestructive = true,
            onConfirm = {
                val uri = pendingRestoreUri!!
                pendingRestoreUri = null
                pendingRestoreManifest = null
                viewModel.restoreBackup(uri) { res ->
                    res.onSuccess { count ->
                        backupStatusMsg = "Restored $count items. Please close and reopen STUDORA if any screen shows cached state."
                        viewModel.refreshStorageStats()
                    }.onFailure { err ->
                        backupStatusMsg = "Restore failed: ${err.localizedMessage}"
                    }
                }
            },
            onDismiss = {
                pendingRestoreUri = null
                pendingRestoreManifest = null
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StorageRow(label: String, value: String, bold: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            label,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
        )
        Text(
            value,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
            color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
