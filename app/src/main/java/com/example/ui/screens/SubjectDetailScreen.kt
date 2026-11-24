package com.example.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.DateTimeUtils
import com.example.core.formatFileSize
import com.example.data.db.*
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StudoraTopBar
import com.example.ui.components.parseColorHex
import com.example.ui.viewmodel.MainViewModel
import java.io.File

@Composable
fun SubjectDetailScreen(
    subjectId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenTopic: (Long, Long) -> Unit,
    onOpenPdfViewer: (String, String, String) -> Unit,
    onOpenScanner: (Long, Long?) -> Unit,
    onOpenWrittenNoteEditor: (Long, Long?, Long, Long?) -> Unit,
    onOpenAiNoteGenerator: (Long?, Long?, Long?, Long) -> Unit,
    onOpenQuizRunner: (Long) -> Unit
) {
    val context = LocalContext.current
    val subject by viewModel.observeSubject(subjectId).collectAsStateWithLifecycle(initialValue = null)
    val topics by viewModel.observeTopics(subjectId).collectAsStateWithLifecycle(initialValue = emptyList())
    val docs by viewModel.observeDocumentsForSubject(subjectId).collectAsStateWithLifecycle(initialValue = emptyList())
    val writtenNotes by viewModel.observeWrittenNotesForSubject(subjectId).collectAsStateWithLifecycle(initialValue = emptyList())
    val allAiNotes by viewModel.allAiNotes.collectAsStateWithLifecycle()
    val allRecordings by viewModel.allRecordings.collectAsStateWithLifecycle()
    val allAiAudio by viewModel.allAiAudio.collectAsStateWithLifecycle()
    val allQuizzes by viewModel.allQuizzes.collectAsStateWithLifecycle()
    val allFlashcards by viewModel.allFlashcards.collectAsStateWithLifecycle()

    val aiNotes = remember(allAiNotes, subjectId) { allAiNotes.filter { it.subjectId == subjectId } }
    val recordings = remember(allRecordings, subjectId) { allRecordings.filter { it.subjectId == subjectId } }
    val aiAudio = remember(allAiAudio, subjectId) { allAiAudio.filter { it.subjectId == subjectId } }
    val quizzes = remember(allQuizzes, subjectId) { allQuizzes.filter { it.subjectId == subjectId } }
    val flashcards = remember(allFlashcards, subjectId) { allFlashcards.filter { it.subjectId == subjectId } }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Topics", "Docs & PDFs", "Notes", "AI Notes", "Audio", "Quiz & Cards")

    var showTopicDialog by remember { mutableStateOf(false) }
    var editingTopic by remember { mutableStateOf<TopicEntity?>(null) }
    var topicToDelete by remember { mutableStateOf<TopicEntity?>(null) }

    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var docToRename by remember { mutableStateOf<DocumentEntity?>(null) }
    var docToDelete by remember { mutableStateOf<DocumentEntity?>(null) }

    var showVoiceRecorderSheet by remember { mutableStateOf(false) }
    var showAiAudioDialog by remember { mutableStateOf(false) }
    var showQuizGenDialog by remember { mutableStateOf(false) }
    var showFlashcardDeckDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = subject?.name ?: "Subject",
                subtitle = subject?.let {
                    listOf(it.code, it.teacherName, it.academicLevel).filter { p -> p.isNotBlank() }.joinToString(" • ")
                },
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onOpenScanner(subjectId, null) }) {
                        Icon(Icons.Default.DocumentScanner, "Scan document")
                    }
                    IconButton(onClick = {
                        filePickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                    }) {
                        Icon(Icons.Default.UploadFile, "Import PDF or Image")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            subject?.let { subj ->
                val accent = parseColorHex(subj.colorHex)
                Surface(
                    color = accent.copy(alpha = 0.1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                subj.description.ifBlank { "Organize chapters, PDFs, voice notes, AI notes & quizzes." },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { onOpenAiNoteGenerator(subjectId, null, null, 0L) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("AI Notes", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title) }
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                when (selectedTab) {
                    0 -> { // Topics
                        item {
                            Button(
                                onClick = {
                                    editingTopic = null
                                    showTopicDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Add Topic / Chapter", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (topics.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.Topic,
                                    title = "No Topics Yet",
                                    subtitle = "Add chapters or lessons (e.g. ER Diagram, SQL, Normalization) to organize study files."
                                )
                            }
                        } else {
                            itemsIndexed(topics, key = { _, t -> t.id }) { idx, topic ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenTopic(subjectId, topic.id) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                        ) {
                                            Text("${idx + 1}", style = MaterialTheme.typography.labelLarge)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(topic.title, style = MaterialTheme.typography.titleMedium)
                                            if (topic.description.isNotBlank()) {
                                                Text(
                                                    topic.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        IconButton(
                                            enabled = idx > 0,
                                            onClick = { viewModel.reorderTopic(topics, idx, idx - 1) }
                                        ) { Icon(Icons.Default.ArrowUpward, "Move up") }
                                        IconButton(
                                            enabled = idx < topics.lastIndex,
                                            onClick = { viewModel.reorderTopic(topics, idx, idx + 1) }
                                        ) { Icon(Icons.Default.ArrowDownward, "Move down") }
                                        IconButton(onClick = { editingTopic = topic; showTopicDialog = true }) {
                                            Icon(Icons.Default.Edit, "Edit")
                                        }
                                        IconButton(onClick = { topicToDelete = topic }) {
                                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> { // Documents & PDFs
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { onOpenScanner(subjectId, null) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.DocumentScanner, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Scan PDF", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = { filePickerLauncher.launch(arrayOf("application/pdf", "image/*")) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Import File", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (docs.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.PictureAsPdf,
                                    title = "No Documents Saved",
                                    subtitle = "Scan multi-page documents with your camera or import PDFs & images for offline study."
                                )
                            }
                        } else {
                            items(docs, key = { it.id }) { doc ->
                                DocumentItemCard(
                                    doc = doc,
                                    onOpen = { onOpenPdfViewer(doc.filePath, doc.title, doc.fileType) },
                                    onAiNotes = { onOpenAiNoteGenerator(subjectId, doc.topicId, doc.id, 0L) },
                                    onShare = {
                                        viewModel.repo.storage.shareFile(File(doc.filePath), doc.mimeType, doc.title)
                                            .onFailure { viewModel.notifyUser(it.localizedMessage ?: "Share failed.") }
                                    },
                                    onRename = { docToRename = doc },
                                    onDelete = { docToDelete = doc }
                                )
                            }
                        }
                    }

                    2 -> { // Written Notes
                        item {
                            Button(
                                onClick = { onOpenWrittenNoteEditor(subjectId, null, 0L, null) },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.EditNote, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Create Written Note", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (writtenNotes.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.Description,
                                    title = "No Written Notes",
                                    subtitle = "Write and organize lecture notes offline."
                                )
                            }
                        } else {
                            items(writtenNotes, key = { it.id }) { note ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenWrittenNoteEditor(subjectId, note.topicId, note.id, note.documentId) }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(note.title, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                note.content.ifBlank { "No content" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "Updated ${DateTimeUtils.formatShortDate(note.updatedAt)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteWrittenNote(note) }) {
                                            Icon(Icons.Default.Delete, "Delete note", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> { // AI Notes
                        item {
                            Button(
                                onClick = { onOpenAiNoteGenerator(subjectId, null, null, 0L) },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AutoAwesome, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Generate AI Notes", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (aiNotes.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.AutoAwesome,
                                    title = "No Saved AI Notes",
                                    subtitle = "Select a PDF or written note and ask AI to explain in Bangla, summarize slides, or create exam notes."
                                )
                            }
                        } else {
                            items(aiNotes, key = { it.id }) { note ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenAiNoteGenerator(subjectId, note.topicId, note.documentId, note.id) }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(note.title, style = MaterialTheme.typography.titleMedium)
                                            Text("Prompt: ${note.prompt}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(note.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        IconButton(onClick = { viewModel.deleteAiNote(note) }) {
                                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    4 -> { // Audio: Personal Voice Notes + AI Audio
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { showVoiceRecorderSheet = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Mic, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Record Voice", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = { showAiAudioDialog = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("AI Audio", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (recordings.isEmpty() && aiAudio.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.Mic,
                                    title = "No Voice or AI Audio",
                                    subtitle = "Record lectures with your microphone or generate offline AI audio narrations from your notes."
                                )
                            }
                        } else {
                            items(recordings, key = { "rec_${it.id}" }) { rec ->
                                AudioItemCard(
                                    title = rec.title,
                                    subtitle = "Voice Recording • ${DateTimeUtils.formatDurationMs(rec.durationMs)}",
                                    isAi = false,
                                    onPlay = { viewModel.playAudio(rec.filePath, rec.title) },
                                    onDelete = { viewModel.deleteRecording(rec) }
                                )
                            }
                            items(aiAudio, key = { "ai_${it.id}" }) { aud ->
                                AudioItemCard(
                                    title = aud.title,
                                    subtitle = "AI Audio (${aud.languageCode}) • ${DateTimeUtils.formatDurationMs(aud.durationMs)}",
                                    isAi = true,
                                    onPlay = { viewModel.playAudio(aud.filePath, aud.title) },
                                    onDelete = { viewModel.deleteAiAudio(aud) }
                                )
                            }
                        }
                    }

                    5 -> { // Quizzes & Flashcards
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { showQuizGenDialog = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Quiz, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("AI Quiz", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = { showFlashcardDeckDialog = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Style, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Flashcards (${flashcards.size})", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (quizzes.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.Quiz,
                                    title = "No Quizzes Yet",
                                    subtitle = "Generate MCQ, True/False, Short Answer, or Viva quizzes from your PDFs and notes, or open Flashcards above."
                                )
                            }
                        } else {
                            items(quizzes, key = { it.id }) { quiz ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenQuizRunner(quiz.id) }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                                        Icon(Icons.Default.Quiz, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(quiz.title, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${quiz.questionCount} questions • ${quiz.questionType} • ${quiz.difficulty}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteQuiz(quiz) }) {
                                            Icon(Icons.Default.Delete, "Delete quiz", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTopicDialog) {
        TopicFormDialog(
            initial = editingTopic,
            subjectId = subjectId,
            nextOrder = topics.size,
            onDismiss = { showTopicDialog = false },
            onSave = { topic ->
                viewModel.saveTopic(topic) { showTopicDialog = false }
            }
        )
    }

    topicToDelete?.let { t ->
        ConfirmationDialog(
            title = "Delete Topic",
            message = "Delete \"${t.title}\"? Linked documents and notes will remain in the subject.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteTopic(t)
                topicToDelete = null
            },
            onDismiss = { topicToDelete = null }
        )
    }

    pendingImportUri?.let { uri ->
        ImportDocumentDialog(
            uri = uri,
            viewModel = viewModel,
            initialSubjectId = subjectId,
            initialTopicId = null,
            onDismiss = { pendingImportUri = null }
        )
    }

    docToRename?.let { doc ->
        var newName by remember(doc) { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { docToRename = null },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Document Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    onClick = {
                        viewModel.renameDocument(doc, newName)
                        docToRename = null
                    }
                ) { Text("Save", maxLines = 1, softWrap = false) }
            },
            dismissButton = {
                TextButton(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    onClick = { docToRename = null }
                ) { Text("Cancel", maxLines = 1, softWrap = false) }
            }
        )
    }

    docToDelete?.let { doc ->
        ConfirmationDialog(
            title = "Delete Document",
            message = "Permanently delete \"${doc.title}\" from device storage?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteDocument(doc)
                docToDelete = null
            },
            onDismiss = { docToDelete = null }
        )
    }

    if (showVoiceRecorderSheet) {
        VoiceRecorderDialog(
            viewModel = viewModel,
            subjectId = subjectId,
            topicId = null,
            docs = docs,
            onDismiss = { showVoiceRecorderSheet = false }
        )
    }

    if (showAiAudioDialog) {
        AiAudioGeneratorDialog(
            viewModel = viewModel,
            subjectId = subjectId,
            topicId = null,
            writtenNotes = writtenNotes,
            aiNotes = aiNotes,
            onDismiss = { showAiAudioDialog = false }
        )
    }

    if (showQuizGenDialog) {
        QuizGeneratorDialog(
            viewModel = viewModel,
            subjectId = subjectId,
            topicId = null,
            docs = docs,
            writtenNotes = writtenNotes,
            aiNotes = aiNotes,
            onDismiss = { showQuizGenDialog = false },
            onQuizCreated = { qId ->
                showQuizGenDialog = false
                onOpenQuizRunner(qId)
            }
        )
    }

    if (showFlashcardDeckDialog) {
        FlashcardsManagerDialog(
            viewModel = viewModel,
            subjectId = subjectId,
            topicId = null,
            flashcards = flashcards,
            docs = docs,
            writtenNotes = writtenNotes,
            onDismiss = { showFlashcardDeckDialog = false }
        )
    }
}

@Composable
fun DocumentItemCard(
    doc: DocumentEntity,
    onOpen: () -> Unit,
    onAiNotes: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Icon(
                if (doc.fileType == "PDF") Icons.Default.PictureAsPdf else Icons.Default.Image,
                contentDescription = doc.fileType,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${doc.fileType} • ${doc.pageCount} pg • ${formatFileSize(doc.fileSizeBytes)} • ${doc.sourceType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(onClick = onAiNotes) {
                Icon(Icons.Default.AutoAwesome, "AI Notes")
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "Document actions")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Open Viewer") }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text("AI Note Generator") }, onClick = { menu = false; onAiNotes() })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; onShare() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun AudioItemCard(
    title: String,
    subtitle: String,
    isAi: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Icon(
                if (isAi) Icons.Default.RecordVoiceOver else Icons.Default.Mic,
                null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPlay) { Icon(Icons.Default.PlayArrow, "Play") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun TopicFormDialog(
    initial: TopicEntity?,
    subjectId: Long,
    nextOrder: Int,
    onDismiss: () -> Unit,
    onSave: (TopicEntity) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Topic / Chapter" else "Edit Topic") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; err = null },
                    label = { Text("Topic Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                onClick = {
                    if (title.isBlank()) { err = "Topic title is required."; return@Button }
                    onSave(
                        (initial ?: TopicEntity(subjectId = subjectId, title = "", displayOrder = nextOrder)).copy(
                            title = title.trim(),
                            description = desc.trim()
                        )
                    )
                }
            ) { Text("Save", maxLines = 1, softWrap = false) }
        },
        dismissButton = {
            TextButton(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                onClick = onDismiss
            ) { Text("Cancel", maxLines = 1, softWrap = false) }
        }
    )
}

@Composable
fun ImportDocumentDialog(
    uri: Uri,
    viewModel: MainViewModel,
    initialSubjectId: Long,
    initialTopicId: Long?,
    onDismiss: () -> Unit
) {
    val fileName = remember(uri) { viewModel.repo.storage.getDisplayNameFromUri(uri) }
    val topics by viewModel.observeTopics(initialSubjectId).collectAsStateWithLifecycle(emptyList())
    var title by remember { mutableStateOf(fileName.substringBeforeLast('.')) }
    var selectedTopicId by remember { mutableStateOf(initialTopicId) }
    var newTopicTitle by remember { mutableStateOf("") }
    var createNewTopic by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        title = { Text("Import Document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("File: $fileName", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Assign Topic (optional)", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = !createNewTopic && selectedTopicId == null,
                            onClick = { createNewTopic = false; selectedTopicId = null },
                            label = { Text("None") }
                        )
                    }
                    items(topics) { t ->
                        FilterChip(
                            selected = !createNewTopic && selectedTopicId == t.id,
                            onClick = { createNewTopic = false; selectedTopicId = t.id },
                            label = { Text(t.title) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = createNewTopic,
                            onClick = { createNewTopic = true },
                            label = { Text("+ New Topic") }
                        )
                    }
                }
                if (createNewTopic) {
                    OutlinedTextField(
                        value = newTopicTitle,
                        onValueChange = { newTopicTitle = it },
                        label = { Text("New Topic Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (importing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = !importing,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                onClick = {
                    importing = true
                    if (createNewTopic && newTopicTitle.isNotBlank()) {
                        viewModel.saveTopic(
                            TopicEntity(subjectId = initialSubjectId, title = newTopicTitle.trim(), displayOrder = topics.size)
                        ) { createdTopicId ->
                            viewModel.importDocument(uri, title, initialSubjectId, createdTopicId) {
                                importing = false
                                onDismiss()
                            }
                        }
                    } else {
                        viewModel.importDocument(uri, title, initialSubjectId, selectedTopicId) {
                            importing = false
                            onDismiss()
                        }
                    }
                }
            ) { Text("Confirm Import", maxLines = 1, softWrap = false) }
        },
        dismissButton = {
            TextButton(
                enabled = !importing,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                onClick = onDismiss
            ) { Text("Cancel", maxLines = 1, softWrap = false) }
        }
    )
}
