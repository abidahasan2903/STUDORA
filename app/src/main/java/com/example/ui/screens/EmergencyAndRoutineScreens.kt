package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.DateTimeUtils
import com.example.data.db.BusScheduleEntity
import com.example.data.db.ClassRoutineEntity
import com.example.data.db.EmergencyContactEntity
import com.example.data.db.TimetableEntryEntity
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StudoraTopBar
import com.example.ui.components.parseColorHex
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import java.io.File

private data class EmergencyCategoryMeta(
    val key: String,
    val label: String,
    val color: Color
)

private val EMERGENCY_CATEGORIES = listOf(
    EmergencyCategoryMeta("ALL", "All", StudoraNavy),
    EmergencyCategoryMeta("FAMILY", "Family", EmeraldSuccess),
    EmergencyCategoryMeta("TRUSTED_FRIENDS", "Trusted Friends", SkyInfo),
    EmergencyCategoryMeta("MEDICAL", "Medical", CrimsonError),
    EmergencyCategoryMeta("POLICE", "Police", IndigoAccent),
    EmergencyCategoryMeta("FIRE_SERVICE", "Fire Service", AmberWarning),
    EmergencyCategoryMeta("CAMPUS_SECURITY", "Campus Security", TealAccent),
    EmergencyCategoryMeta("OTHER", "Other", StudoraGold)
)

@Composable
fun EmergencyContactsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val contacts by viewModel.emergencyContacts.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var showFormDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<EmergencyContactEntity?>(null) }
    var contactToDelete by remember { mutableStateOf<EmergencyContactEntity?>(null) }

    val filtered = remember(contacts, searchQuery, selectedCategory, favoritesOnly) {
        val q = searchQuery.trim().lowercase()
        contacts.filter { c ->
            val catMatch = selectedCategory == "ALL" || c.category == selectedCategory
            val favMatch = !favoritesOnly || c.isFavorite
            val textMatch = q.isBlank() ||
                c.name.lowercase().contains(q) ||
                c.phoneNumber.lowercase().contains(q) ||
                c.description.lowercase().contains(q)
            catMatch && favMatch && textMatch
        }
    }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = "Emergency Contact",
                subtitle = "${contacts.size} saved • Offline quick dial",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { favoritesOnly = !favoritesOnly }) {
                        Icon(
                            imageVector = if (favoritesOnly) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Toggle favorites filter",
                            tint = if (favoritesOnly) StudoraGold else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingContact = null
                    showFormDialog = true
                },
                icon = { Icon(Icons.Default.AddIcCall, null) },
                text = { Text("Add Contact", maxLines = 1, softWrap = false) },
                modifier = Modifier.testTag("add_emergency_contact_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Add your family, campus security, and local verified emergency numbers. Stored offline on your device for instant dialer access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search emergency contacts...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EMERGENCY_CATEGORIES) { meta ->
                        FilterChip(
                            selected = selectedCategory == meta.key,
                            onClick = { selectedCategory = meta.key },
                            label = { Text(meta.label) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.ContactPhone,
                        title = if (contacts.isEmpty()) "No emergency contacts saved" else "No matching contacts",
                        subtitle = "Save family, trusted friends, medical, campus security, or verified local emergency numbers for one-tap phone dialer access.",
                        actionLabel = if (contacts.isEmpty()) "Add Emergency Contact" else null,
                        onAction = if (contacts.isEmpty()) {
                            { editingContact = null; showFormDialog = true }
                        } else null
                    )
                }
            } else {
                items(filtered, key = { it.id }) { contact ->
                    val meta = EMERGENCY_CATEGORIES.firstOrNull { it.key == contact.category }
                        ?: EMERGENCY_CATEGORIES.last()
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(meta.color.copy(alpha = 0.14f))
                                ) {
                                    Icon(
                                        Icons.Default.PhoneInTalk,
                                        contentDescription = null,
                                        tint = meta.color
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            contact.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (contact.isFavorite) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = "Favorite",
                                                tint = StudoraGold,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        contact.phoneNumber,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Button(
                                    onClick = {
                                        val dialIntent = Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.parse("tel:${contact.phoneNumber.trim()}")
                                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                        try {
                                            context.startActivity(dialIntent)
                                        } catch (_: Exception) {
                                            viewModel.notifyUser("No phone dialer available on this device.")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("dial_contact_${contact.id}")
                                ) {
                                    Icon(Icons.Default.Call, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Call", maxLines = 1, softWrap = false)
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(meta.label, style = MaterialTheme.typography.labelSmall) }
                                )
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            if (contact.isVerifiedOfficial) "Official Verified" else "User Added",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        viewModel.saveEmergencyContact(contact.copy(isFavorite = !contact.isFavorite))
                                    }
                                ) {
                                    Icon(
                                        if (contact.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Toggle favorite",
                                        tint = if (contact.isFavorite) StudoraGold else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    editingContact = contact
                                    showFormDialog = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit contact")
                                }
                                IconButton(onClick = { contactToDelete = contact }) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete contact",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            if (contact.description.isNotBlank() || contact.notes.isNotBlank()) {
                                Text(
                                    listOf(contact.description, contact.notes).filter { it.isNotBlank() }.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFormDialog) {
        EmergencyContactDialog(
            initial = editingContact,
            onDismiss = { showFormDialog = false },
            onSave = { entity ->
                viewModel.saveEmergencyContact(entity)
                showFormDialog = false
            }
        )
    }

    contactToDelete?.let { contact ->
        ConfirmationDialog(
            title = "Delete Emergency Contact?",
            message = "Remove \"${contact.name}\" (${contact.phoneNumber}) from your offline emergency directory?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteEmergencyContact(contact)
                contactToDelete = null
            },
            onDismiss = { contactToDelete = null }
        )
    }
}

@Composable
private fun EmergencyContactDialog(
    initial: EmergencyContactEntity?,
    onDismiss: () -> Unit,
    onSave: (EmergencyContactEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phoneNumber ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "FAMILY") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var isFavorite by remember { mutableStateOf(initial?.isFavorite ?: false) }
    var isVerifiedOfficial by remember { mutableStateOf(initial?.isVerifiedOfficial ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Emergency Contact" else "Edit Emergency Contact") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(EMERGENCY_CATEGORIES.drop(1)) { meta ->
                        FilterChip(
                            selected = category == meta.key,
                            onClick = { category = meta.key },
                            label = { Text(meta.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Role / Relation") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (address, extension, hours)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = isFavorite, onCheckedChange = { isFavorite = it })
                    Text("Pin as Favorite", style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = isVerifiedOfficial, onCheckedChange = { isVerifiedOfficial = it })
                    Text("Verified Official Number", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && phone.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                onClick = {
                    onSave(
                        (initial ?: EmergencyContactEntity(
                            name = "",
                            category = category,
                            phoneNumber = ""
                        )).copy(
                            name = name.trim(),
                            category = category,
                            phoneNumber = phone.trim(),
                            description = description.trim(),
                            notes = notes.trim(),
                            isFavorite = isFavorite,
                            isVerifiedOfficial = isVerifiedOfficial
                        )
                    )
                }
            ) {
                Text("Save", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("Cancel", maxLines = 1, softWrap = false) }
        }
    )
}

// =========================================================================
// CLASS ROUTINE SCREEN
// =========================================================================

private val WEEK_DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val TIMETABLE_COLORS = listOf("#0F2547", "#0D9488", "#4F46E5", "#D97706", "#E11D48", "#0284C7")

@Composable
fun ClassRoutineScreen(
    viewModel: MainViewModel,
    onOpenScanner: () -> Unit,
    onOpenViewer: (String, String, String) -> Unit,
    onBack: () -> Unit
) {
    val routines by viewModel.classRoutines.collectAsStateWithLifecycle()
    val entries by viewModel.timetableEntries.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Documents, 1 = Manual Timetable
    var renamingRoutine by remember { mutableStateOf<ClassRoutineEntity?>(null) }
    var replacingRoutine by remember { mutableStateOf<ClassRoutineEntity?>(null) }
    var routineToDelete by remember { mutableStateOf<ClassRoutineEntity?>(null) }
    var showEntryDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<TimetableEntryEntity?>(null) }
    var selectedDayFilter by remember { mutableStateOf("All") }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val title = viewModel.repo.storage.getDisplayNameFromUri(uri).substringBeforeLast('.')
            viewModel.importRoutineUri(uri, title)
        }
    }

    val replacePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val old = replacingRoutine
        replacingRoutine = null
        if (uri != null && old != null) {
            viewModel.importRoutineUri(uri, old.title)
            viewModel.deleteRoutine(old)
        }
    }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = "Class Routine",
                subtitle = "${routines.size} docs • ${entries.size} manual classes",
                onBack = onBack
            )
        },
        floatingActionButton = {
            if (selectedTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingEntry = null
                        showEntryDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add Class", maxLines = 1, softWrap = false) },
                    modifier = Modifier.testTag("add_timetable_entry_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Routine Documents", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.Description, null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Manual Timetable", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.Schedule, null) }
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    importPicker.launch(arrayOf("application/pdf", "image/*"))
                                },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import File", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = onOpenScanner,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DocumentScanner, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Scan Routine", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    if (routines.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.CalendarViewWeek,
                                title = "No routine files saved",
                                subtitle = "Import your university timetable PDF or image, or scan a printed routine with the camera for offline viewing."
                            )
                        }
                    } else {
                        items(routines, key = { it.id }) { item ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenViewer(item.filePath, item.title, item.fileType) }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (item.fileType == "PDF") Icons.Default.PictureAsPdf else Icons.Default.Image,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${item.fileType} • ${item.pageCount} page(s) • ${DateTimeUtils.formatShortDate(item.updatedAt)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { renamingRoutine = item }) {
                                            Icon(Icons.Default.DriveFileRenameOutline, "Rename routine")
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = { onOpenViewer(item.filePath, item.title, item.fileType) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("View", maxLines = 1, softWrap = false)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                replacingRoutine = item
                                                replacePicker.launch(arrayOf("application/pdf", "image/*"))
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("Replace", maxLines = 1, softWrap = false)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val mime = if (item.fileType == "PDF") "application/pdf" else "image/*"
                                                viewModel.repo.storage.shareFile(File(item.filePath), mime, item.title)
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("Share", maxLines = 1, softWrap = false)
                                        }
                                        Spacer(Modifier.weight(1f))
                                        IconButton(onClick = { routineToDelete = item }) {
                                            Icon(Icons.Default.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val visibleEntries = remember(entries, selectedDayFilter) {
                    if (selectedDayFilter == "All") entries else entries.filter { it.dayOfWeek == selectedDayFilter }
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf("All") + WEEK_DAYS) { day ->
                                FilterChip(
                                    selected = selectedDayFilter == day,
                                    onClick = { selectedDayFilter = day },
                                    label = { Text(day) }
                                )
                            }
                        }
                    }

                    if (visibleEntries.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Schedule,
                                title = "No manual timetable classes",
                                subtitle = "Add your structured weekly classes with room numbers and instructors here. These remain completely separate from uploaded PDFs.",
                                actionLabel = "Add Class Entry",
                                onAction = { editingEntry = null; showEntryDialog = true }
                            )
                        }
                    } else {
                        items(visibleEntries, key = { it.id }) { entry ->
                            val accent = parseColorHex(entry.colorHex)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(5.dp)
                                            .height(52.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(accent)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.subjectName, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${entry.dayOfWeek} • ${entry.startTime} - ${entry.endTime}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        val details = listOfNotNull(
                                            entry.room.takeIf { it.isNotBlank() }?.let { "Room $it" },
                                            entry.teacher.takeIf { it.isNotBlank() }
                                        ).joinToString(" • ")
                                        if (details.isNotBlank()) {
                                            Text(
                                                details,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(onClick = {
                                        editingEntry = entry
                                        showEntryDialog = true
                                    }) {
                                        Icon(Icons.Default.Edit, "Edit class")
                                    }
                                    IconButton(onClick = { viewModel.deleteTimetableEntry(entry) }) {
                                        Icon(Icons.Default.DeleteOutline, "Delete class", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renamingRoutine?.let { item ->
        var newTitle by remember(item.id) { mutableStateOf(item.title) }
        AlertDialog(
            onDismissRequest = { renamingRoutine = null },
            title = { Text("Rename Routine") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Document Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = newTitle.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    onClick = {
                        viewModel.renameRoutine(item, newTitle)
                        renamingRoutine = null
                    }
                ) { Text("Save", maxLines = 1, softWrap = false) }
            },
            dismissButton = {
                TextButton(
                    onClick = { renamingRoutine = null },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("Cancel", maxLines = 1, softWrap = false) }
            }
        )
    }

    routineToDelete?.let { item ->
        ConfirmationDialog(
            title = "Delete Routine File?",
            message = "Delete \"${item.title}\" permanently from local device storage?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteRoutine(item)
                routineToDelete = null
            },
            onDismiss = { routineToDelete = null }
        )
    }

    if (showEntryDialog) {
        TimetableEntryDialog(
            initial = editingEntry,
            onDismiss = { showEntryDialog = false },
            onSave = { entry ->
                viewModel.saveTimetableEntry(entry)
                showEntryDialog = false
            }
        )
    }
}

@Composable
private fun TimetableEntryDialog(
    initial: TimetableEntryEntity?,
    onDismiss: () -> Unit,
    onSave: (TimetableEntryEntity) -> Unit
) {
    var subjectName by remember { mutableStateOf(initial?.subjectName ?: "") }
    var dayOfWeek by remember { mutableStateOf(initial?.dayOfWeek ?: "Monday") }
    var startTime by remember { mutableStateOf(initial?.startTime ?: "09:00 AM") }
    var endTime by remember { mutableStateOf(initial?.endTime ?: "10:30 AM") }
    var room by remember { mutableStateOf(initial?.room ?: "") }
    var teacher by remember { mutableStateOf(initial?.teacher ?: "") }
    var colorHex by remember { mutableStateOf(initial?.colorHex ?: TIMETABLE_COLORS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Class Entry" else "Edit Class Entry") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = subjectName,
                    onValueChange = { subjectName = it },
                    label = { Text("Subject Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Day of Week", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(WEEK_DAYS) { day ->
                        FilterChip(
                            selected = dayOfWeek == day,
                            onClick = { dayOfWeek = day },
                            label = { Text(day.take(3)) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("End") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Room / Lab") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("Instructor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(TIMETABLE_COLORS) { hex ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(parseColorHex(hex))
                                .clickable { colorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (colorHex == hex) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = subjectName.isNotBlank() && startTime.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                onClick = {
                    onSave(
                        (initial ?: TimetableEntryEntity(
                            subjectName = "",
                            dayOfWeek = dayOfWeek,
                            startTime = "",
                            endTime = ""
                        )).copy(
                            subjectName = subjectName.trim(),
                            dayOfWeek = dayOfWeek,
                            startTime = startTime.trim(),
                            endTime = endTime.trim(),
                            room = room.trim(),
                            teacher = teacher.trim(),
                            colorHex = colorHex
                        )
                    )
                }
            ) { Text("Save", maxLines = 1, softWrap = false) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("Cancel", maxLines = 1, softWrap = false) }
        }
    )
}

// =========================================================================
// BUS SCHEDULE SCREEN
// =========================================================================

@Composable
fun BusScheduleScreen(
    viewModel: MainViewModel,
    onOpenScanner: () -> Unit,
    onOpenViewer: (String, String, String) -> Unit,
    onBack: () -> Unit
) {
    val allSchedules by viewModel.busSchedules.collectAsStateWithLifecycle()
    val docSchedules = remember(allSchedules) { allSchedules.filter { it.isDocumentRecord } }
    val manualRoutes = remember(allSchedules) { allSchedules.filter { !it.isDocumentRecord } }

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var renamingItem by remember { mutableStateOf<BusScheduleEntity?>(null) }
    var replacingItem by remember { mutableStateOf<BusScheduleEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<BusScheduleEntity?>(null) }
    var showRouteDialog by remember { mutableStateOf(false) }
    var editingRoute by remember { mutableStateOf<BusScheduleEntity?>(null) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val title = viewModel.repo.storage.getDisplayNameFromUri(uri).substringBeforeLast('.')
            viewModel.importBusScheduleUri(uri, title)
        }
    }

    val replacePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val old = replacingItem
        replacingItem = null
        if (uri != null && old != null) {
            viewModel.importBusScheduleUri(uri, old.title)
            viewModel.deleteBusSchedule(old)
        }
    }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = "Bus Schedule",
                subtitle = "${docSchedules.size} docs • ${manualRoutes.size} routes (saved locally)",
                onBack = onBack
            )
        },
        floatingActionButton = {
            if (selectedTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingRoute = null
                        showRouteDialog = true
                    },
                    icon = { Icon(Icons.Default.DirectionsBus, null) },
                    text = { Text("Add Route", maxLines = 1, softWrap = false) },
                    modifier = Modifier.testTag("add_bus_route_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Schedule Files", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.Description, null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Manual Routes", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.Route, null) }
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    importPicker.launch(arrayOf("application/pdf", "image/*"))
                                },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import File", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = onOpenScanner,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DocumentScanner, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Scan Sheet", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    if (docSchedules.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.DirectionsBus,
                                title = "No bus schedule files saved",
                                subtitle = "Import or scan your campus shuttle PDF or noticeboard photo for instant offline access."
                            )
                        }
                    } else {
                        items(docSchedules, key = { it.id }) { doc ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenViewer(doc.filePath, doc.title, doc.fileType) }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (doc.fileType == "PDF") Icons.Default.PictureAsPdf else Icons.Default.Image,
                                            contentDescription = null,
                                            tint = SkyInfo
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(doc.title, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${doc.fileType} • ${doc.pageCount} page(s) • ${DateTimeUtils.formatShortDate(doc.updatedAt)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { renamingItem = doc }) {
                                            Icon(Icons.Default.DriveFileRenameOutline, "Rename")
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = { onOpenViewer(doc.filePath, doc.title, doc.fileType) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) { Text("View", maxLines = 1, softWrap = false) }
                                        OutlinedButton(
                                            onClick = {
                                                replacingItem = doc
                                                replacePicker.launch(arrayOf("application/pdf", "image/*"))
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) { Text("Replace", maxLines = 1, softWrap = false) }
                                        OutlinedButton(
                                            onClick = {
                                                val mime = if (doc.fileType == "PDF") "application/pdf" else "image/*"
                                                viewModel.repo.storage.shareFile(File(doc.filePath), mime, doc.title)
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) { Text("Share", maxLines = 1, softWrap = false) }
                                        Spacer(Modifier.weight(1f))
                                        IconButton(onClick = { itemToDelete = doc }) {
                                            Icon(Icons.Default.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val filteredRoutes = remember(manualRoutes, searchQuery) {
                    val q = searchQuery.trim().lowercase()
                    if (q.isBlank()) manualRoutes else manualRoutes.filter {
                        it.routeName.lowercase().contains(q) ||
                            it.busName.lowercase().contains(q) ||
                            it.startingPoint.lowercase().contains(q) ||
                            it.destination.lowercase().contains(q)
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search routes, stops, bus names...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (filteredRoutes.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Route,
                                title = "No manual bus routes",
                                subtitle = "Add your campus bus departures, stops, and arrival times to keep them organized offline.",
                                actionLabel = "Add Bus Route",
                                onAction = { editingRoute = null; showRouteDialog = true }
                            )
                        }
                    } else {
                        items(filteredRoutes, key = { it.id }) { route ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.DirectionsBus, null, tint = SkyInfo)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                route.routeName.ifBlank { route.title },
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            if (route.busName.isNotBlank()) {
                                                Text(
                                                    route.busName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        IconButton(onClick = {
                                            editingRoute = route
                                            showRouteDialog = true
                                        }) {
                                            Icon(Icons.Default.Edit, "Edit route")
                                        }
                                        IconButton(onClick = { itemToDelete = route }) {
                                            Icon(Icons.Default.DeleteOutline, "Delete route", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "${route.startingPoint.ifBlank { "Start" }} → ${route.destination.ifBlank { "Destination" }}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Departs: ${route.departureTime.ifBlank { "-" }}  •  Arrives: ${route.arrivalTime.ifBlank { "-" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (route.notes.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            route.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renamingItem?.let { item ->
        var newTitle by remember(item.id) { mutableStateOf(item.title) }
        AlertDialog(
            onDismissRequest = { renamingItem = null },
            title = { Text("Rename Bus Schedule") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = newTitle.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    onClick = {
                        viewModel.saveBusSchedule(item.copy(title = newTitle.trim()))
                        renamingItem = null
                    }
                ) { Text("Save", maxLines = 1, softWrap = false) }
            },
            dismissButton = {
                TextButton(
                    onClick = { renamingItem = null },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("Cancel", maxLines = 1, softWrap = false) }
            }
        )
    }

    itemToDelete?.let { item ->
        ConfirmationDialog(
            title = "Delete Bus Schedule?",
            message = "Remove \"${item.title.ifBlank { item.routeName }}\" permanently?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteBusSchedule(item)
                itemToDelete = null
            },
            onDismiss = { itemToDelete = null }
        )
    }

    if (showRouteDialog) {
        BusRouteDialog(
            initial = editingRoute,
            onDismiss = { showRouteDialog = false },
            onSave = { route ->
                viewModel.saveBusSchedule(route)
                showRouteDialog = false
            }
        )
    }
}

@Composable
private fun BusRouteDialog(
    initial: BusScheduleEntity?,
    onDismiss: () -> Unit,
    onSave: (BusScheduleEntity) -> Unit
) {
    var routeName by remember { mutableStateOf(initial?.routeName ?: "") }
    var busName by remember { mutableStateOf(initial?.busName ?: "") }
    var departure by remember { mutableStateOf(initial?.departureTime ?: "") }
    var arrival by remember { mutableStateOf(initial?.arrivalTime ?: "") }
    var startPoint by remember { mutableStateOf(initial?.startingPoint ?: "") }
    var destination by remember { mutableStateOf(initial?.destination ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Bus Route" else "Edit Bus Route") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = routeName,
                    onValueChange = { routeName = it },
                    label = { Text("Route Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = busName,
                    onValueChange = { busName = it },
                    label = { Text("Bus Name / Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = departure,
                        onValueChange = { departure = it },
                        label = { Text("Departure") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = arrival,
                        onValueChange = { arrival = it },
                        label = { Text("Arrival") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = startPoint,
                    onValueChange = { startPoint = it },
                    label = { Text("Starting Point") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Stoppages / Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = routeName.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                onClick = {
                    onSave(
                        (initial ?: BusScheduleEntity(
                            isDocumentRecord = false,
                            title = routeName.trim()
                        )).copy(
                            isDocumentRecord = false,
                            title = routeName.trim(),
                            routeName = routeName.trim(),
                            busName = busName.trim(),
                            departureTime = departure.trim(),
                            arrivalTime = arrival.trim(),
                            startingPoint = startPoint.trim(),
                            destination = destination.trim(),
                            notes = notes.trim()
                        )
                    )
                }
            ) { Text("Save", maxLines = 1, softWrap = false) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("Cancel", maxLines = 1, softWrap = false) }
        }
    )
}
