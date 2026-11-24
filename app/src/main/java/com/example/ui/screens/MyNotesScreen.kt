package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.SubjectEntity
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StudoraTopBar
import com.example.ui.components.parseColorHex
import com.example.ui.theme.SubjectPalette
import com.example.ui.viewmodel.MainViewModel

private val SUBJECT_ICONS: Map<String, ImageVector> = mapOf(
    "Book" to Icons.AutoMirrored.Filled.MenuBook,
    "Code" to Icons.Default.Code,
    "Science" to Icons.Default.Science,
    "Math" to Icons.Default.Calculate,
    "Data" to Icons.Default.Storage,
    "Design" to Icons.Default.Architecture,
    "Language" to Icons.Default.Translate,
    "Business" to Icons.Default.BusinessCenter
)

fun subjectIconFor(name: String): ImageVector = SUBJECT_ICONS[name] ?: Icons.AutoMirrored.Filled.MenuBook

@Composable
fun MyNotesScreen(
    viewModel: MainViewModel,
    onBack: (() -> Unit)? = null,
    onOpenSubject: (Long) -> Unit,
    onOpenAiNote: (Long) -> Unit,
    onOpenQuiz: (Long) -> Unit,
    onOpenScanner: () -> Unit
) {
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val topics by viewModel.allTopics.collectAsStateWithLifecycle()
    val documents by viewModel.allDocuments.collectAsStateWithLifecycle()
    val writtenNotes by viewModel.allWrittenNotes.collectAsStateWithLifecycle()
    val aiNotes by viewModel.allAiNotes.collectAsStateWithLifecycle()
    val recordings by viewModel.allRecordings.collectAsStateWithLifecycle()
    val aiAudio by viewModel.allAiAudio.collectAsStateWithLifecycle()
    val quizzes by viewModel.allQuizzes.collectAsStateWithLifecycle()
    val flashcards by viewModel.allFlashcards.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showSubjectDialog by remember { mutableStateOf(false) }
    var editingSubject by remember { mutableStateOf<SubjectEntity?>(null) }
    var subjectToDelete by remember { mutableStateOf<SubjectEntity?>(null) }
    var selectedSection by remember { mutableIntStateOf(0) } // 0=Subjects, 1=Study Tools Library

    val filteredSubjects = remember(subjects, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) subjects
        else subjects.filter {
            it.name.lowercase().contains(q) ||
                it.code.lowercase().contains(q) ||
                it.teacherName.lowercase().contains(q)
        }
    }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = "My Notes",
                subtitle = "${subjects.size} subjects • ${documents.size} docs • ${writtenNotes.size + aiNotes.size} notes",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenScanner) {
                        Icon(Icons.Default.DocumentScanner, "Scan Document")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingSubject = null
                    showSubjectDialog = true
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add Subject", maxLines = 1, softWrap = false) },
                modifier = Modifier.testTag("add_subject_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedSection == 0,
                        onClick = { selectedSection = 0 },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Subjects (${subjects.size})", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                    SegmentedButton(
                        selected = selectedSection == 1,
                        onClick = { selectedSection = 1 },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Offline Library", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                }
            }

            if (selectedSection == 0) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search subjects by name, code, or teacher...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (subjects.isNotEmpty() && searchQuery.isBlank()) {
                    item {
                        Text(
                            "Recently Accessed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(subjects.take(5)) { subj ->
                                val color = parseColorHex(subj.colorHex)
                                AssistChip(
                                    onClick = {
                                        viewModel.touchSubject(subj.id)
                                        onOpenSubject(subj.id)
                                    },
                                    label = { Text(subj.name) },
                                    leadingIcon = {
                                        Icon(
                                            subjectIconFor(subj.iconName),
                                            null,
                                            tint = color,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                if (filteredSubjects.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            title = if (subjects.isEmpty()) "No Subjects Created Yet" else "No Matching Subjects",
                            subtitle = "Create unlimited custom subjects (e.g. Database Management System, Statistics, Java) to organize chapters, PDFs, voice notes, AI notes, quizzes, and flashcards.",
                            actionLabel = "+ Add Subject",
                            onAction = {
                                editingSubject = null
                                showSubjectDialog = true
                            }
                        )
                    }
                } else {
                    items(filteredSubjects, key = { it.id }) { subj ->
                        val topicCount = topics.count { it.subjectId == subj.id }
                        val docCount = documents.count { it.subjectId == subj.id }
                        val noteCount = writtenNotes.count { it.subjectId == subj.id }
                        SubjectCard(
                            subject = subj,
                            topicCount = topicCount,
                            docCount = docCount,
                            noteCount = noteCount,
                            onClick = {
                                viewModel.touchSubject(subj.id)
                                onOpenSubject(subj.id)
                            },
                            onEdit = {
                                editingSubject = subj
                                showSubjectDialog = true
                            },
                            onDelete = { subjectToDelete = subj }
                        )
                    }
                }
            } else {
                // Offline Library overview across all subjects
                item {
                    LibrarySummaryBlock(
                        title = "Saved AI Notes (${aiNotes.size})",
                        emptyHint = "Open any subject or PDF to generate and save AI notes."
                    ) {
                        aiNotes.forEach { note ->
                            ListItem(
                                headlineContent = { Text(note.title) },
                                supportingContent = { Text(note.prompt, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Icon(Icons.Default.AutoAwesome, null) },
                                modifier = Modifier.clickable { onOpenAiNote(note.id) }
                            )
                        }
                    }
                }
                item {
                    LibrarySummaryBlock(
                        title = "Voice Recordings (${recordings.size}) & AI Audio (${aiAudio.size})",
                        emptyHint = "Record voice notes or synthesize AI audio inside any subject."
                    ) {
                        recordings.forEach { rec ->
                            ListItem(
                                headlineContent = { Text(rec.title) },
                                supportingContent = { Text("Mic recording • ${rec.durationMs / 1000}s") },
                                leadingContent = { Icon(Icons.Default.Mic, null) },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.playAudio(rec.filePath, rec.title) }) {
                                        Icon(Icons.Default.PlayArrow, "Play")
                                    }
                                }
                            )
                        }
                        aiAudio.forEach { aud ->
                            ListItem(
                                headlineContent = { Text(aud.title) },
                                supportingContent = { Text("AI Audio (${aud.languageCode}) • ${aud.durationMs / 1000}s") },
                                leadingContent = { Icon(Icons.Default.RecordVoiceOver, null) },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.playAudio(aud.filePath, aud.title) }) {
                                        Icon(Icons.Default.PlayArrow, "Play")
                                    }
                                }
                            )
                        }
                    }
                }
                item {
                    LibrarySummaryBlock(
                        title = "Saved Quizzes (${quizzes.size}) & Flashcards (${flashcards.size})",
                        emptyHint = "Generate AI quizzes and flashcards inside any subject or topic."
                    ) {
                        quizzes.forEach { quiz ->
                            ListItem(
                                headlineContent = { Text(quiz.title) },
                                supportingContent = { Text("${quiz.questionCount} ${quiz.questionType} • ${quiz.difficulty}") },
                                leadingContent = { Icon(Icons.Default.Quiz, null) },
                                modifier = Modifier.clickable { onOpenQuiz(quiz.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSubjectDialog) {
        SubjectFormDialog(
            initial = editingSubject,
            onDismiss = { showSubjectDialog = false },
            onSave = { entity ->
                viewModel.saveSubject(entity) { showSubjectDialog = false }
            }
        )
    }

    subjectToDelete?.let { subj ->
        ConfirmationDialog(
            title = "Delete Subject?",
            message = "Delete \"${subj.name}\" along with its topics, written notes, and attached document files? This cannot be undone.",
            confirmText = "Delete Subject",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteSubject(subj)
                subjectToDelete = null
            },
            onDismiss = { subjectToDelete = null }
        )
    }
}

@Composable
private fun SubjectCard(
    subject: SubjectEntity,
    topicCount: Int,
    docCount: Int,
    noteCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = parseColorHex(subject.colorHex)
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.16f))
            ) {
                Icon(
                    imageVector = subjectIconFor(subject.iconName),
                    contentDescription = subject.name,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        subject.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (subject.code.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = accent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                subject.code,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (subject.teacherName.isNotBlank() || subject.academicLevel.isNotBlank()) {
                    Text(
                        listOf(subject.teacherName, subject.academicLevel).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "$topicCount topics • $docCount docs • $noteCount notes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, "Subject options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit Subject") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuOpen = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Subject") },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySummaryBlock(
    title: String,
    emptyHint: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            content()
            Text(
                emptyHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun SubjectFormDialog(
    initial: SubjectEntity?,
    onDismiss: () -> Unit,
    onSave: (SubjectEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var code by remember { mutableStateOf(initial?.code ?: "") }
    var teacher by remember { mutableStateOf(initial?.teacherName ?: "") }
    var level by remember { mutableStateOf(initial?.academicLevel ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var selectedIcon by remember { mutableStateOf(initial?.iconName ?: "Book") }
    var selectedColor by remember { mutableStateOf(initial?.colorHex ?: SubjectPalette.first()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Subject" else "Edit Subject") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        label = { Text("Subject Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Subject Code (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text("Teacher Name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = level,
                        onValueChange = { level = it },
                        label = { Text("Class / Semester (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("Icon", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SUBJECT_ICONS.keys.toList()) { iconKey ->
                            FilterChip(
                                selected = selectedIcon == iconKey,
                                onClick = { selectedIcon = iconKey },
                                label = { Text(iconKey) },
                                leadingIcon = { Icon(subjectIconFor(iconKey), null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
                item {
                    Text("Color", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SubjectPalette) { hex ->
                            val c = parseColorHex(hex)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .clickable { selectedColor = hex }
                            ) {
                                if (selectedColor == hex) {
                                    Icon(Icons.Default.Check, "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                if (error != null) {
                    item { Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            Button(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                onClick = {
                    if (name.isBlank()) {
                        error = "Subject Name is required."
                        return@Button
                    }
                    val entity = (initial ?: SubjectEntity(name = "")).copy(
                        name = name.trim(),
                        code = code.trim(),
                        teacherName = teacher.trim(),
                        academicLevel = level.trim(),
                        description = description.trim(),
                        iconName = selectedIcon,
                        colorHex = selectedColor
                    )
                    onSave(entity)
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
