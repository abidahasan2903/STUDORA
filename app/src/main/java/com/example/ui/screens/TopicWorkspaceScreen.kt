package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.DateTimeUtils
import com.example.data.db.DocumentEntity
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StudoraTopBar
import com.example.ui.viewmodel.MainViewModel
import java.io.File

@Composable
fun TopicWorkspaceScreen(
    subjectId: Long,
    topicId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenPdfViewer: (String, String, String) -> Unit,
    onOpenScanner: (Long, Long) -> Unit,
    onOpenWrittenNoteEditor: (Long, Long, Long, Long?) -> Unit,
    onOpenAiNoteGenerator: (Long, Long, Long?, Long) -> Unit,
    onOpenQuizRunner: (Long) -> Unit
) {
    val subject by viewModel.observeSubject(subjectId).collectAsStateWithLifecycle(null)
    val topics by viewModel.observeTopics(subjectId).collectAsStateWithLifecycle(emptyList())
    val topic = topics.firstOrNull { it.id == topicId }

    val docs by viewModel.observeDocumentsForTopic(topicId).collectAsStateWithLifecycle(emptyList())
    val writtenNotes by viewModel.observeWrittenNotesForTopic(topicId).collectAsStateWithLifecycle(emptyList())
    val allAiNotes by viewModel.allAiNotes.collectAsStateWithLifecycle()
    val allRecordings by viewModel.allRecordings.collectAsStateWithLifecycle()
    val allAiAudio by viewModel.allAiAudio.collectAsStateWithLifecycle()
    val allQuizzes by viewModel.allQuizzes.collectAsStateWithLifecycle()
    val allFlashcards by viewModel.allFlashcards.collectAsStateWithLifecycle()

    val aiNotes = remember(allAiNotes, topicId) { allAiNotes.filter { it.topicId == topicId } }
    val recordings = remember(allRecordings, topicId) { allRecordings.filter { it.topicId == topicId } }
    val aiAudio = remember(allAiAudio, topicId) { allAiAudio.filter { it.topicId == topicId } }
    val quizzes = remember(allQuizzes, topicId) { allQuizzes.filter { it.topicId == topicId } }
    val flashcards = remember(allFlashcards, topicId) { allFlashcards.filter { it.topicId == topicId } }

    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showVoiceRecorder by remember { mutableStateOf(false) }
    var showAiAudio by remember { mutableStateOf(false) }
    var showQuizGen by remember { mutableStateOf(false) }
    var showFlashcards by remember { mutableStateOf(false) }
    var docToRename by remember { mutableStateOf<DocumentEntity?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = topic?.title ?: "Topic Workspace",
                subtitle = subject?.name,
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text("Topic Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onOpenScanner(subjectId, topicId) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DocumentScanner, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Scan PDF", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Import", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onOpenWrittenNoteEditor(subjectId, topicId, 0L, null) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.EditNote, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Write Note", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { showVoiceRecorder = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Mic, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Voice Note", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(
                                onClick = { onOpenAiNoteGenerator(subjectId, topicId, docs.firstOrNull()?.id, 0L) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("AI Notes", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                            FilledTonalButton(
                                onClick = { showQuizGen = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Quiz, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("AI Quiz", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showAiAudio = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.RecordVoiceOver, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("AI Audio", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { showFlashcards = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Style, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Flashcards (${flashcards.size})", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            if (docs.isEmpty() && writtenNotes.isEmpty() && aiNotes.isEmpty() && recordings.isEmpty() && aiAudio.isEmpty() && quizzes.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.FolderOpen,
                        title = "Workspace Ready",
                        subtitle = "Scan or import PDFs, write notes, record voice memos, or generate AI notes, quizzes, and flashcards for this topic."
                    )
                }
            }

            if (docs.isNotEmpty()) {
                item { Text("Documents (${docs.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(docs, key = { "d_${it.id}" }) { doc ->
                    DocumentItemCard(
                        doc = doc,
                        onOpen = { onOpenPdfViewer(doc.filePath, doc.title, doc.fileType) },
                        onAiNotes = { onOpenAiNoteGenerator(subjectId, topicId, doc.id, 0L) },
                        onShare = { viewModel.repo.storage.shareFile(File(doc.filePath), doc.mimeType, doc.title) },
                        onRename = { docToRename = doc },
                        onDelete = { viewModel.deleteDocument(doc) }
                    )
                }
            }

            if (writtenNotes.isNotEmpty() || aiNotes.isNotEmpty()) {
                item { Text("Notes (${writtenNotes.size + aiNotes.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(writtenNotes, key = { "wn_${it.id}" }) { note ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenWrittenNoteEditor(subjectId, topicId, note.id, note.documentId) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(note.title, style = MaterialTheme.typography.titleSmall)
                            Text(note.content, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                    }
                }
                items(aiNotes, key = { "an_${it.id}" }) { note ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAiNoteGenerator(subjectId, topicId, note.documentId, note.id) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("AI: ${note.title}", style = MaterialTheme.typography.titleSmall)
                            Text(note.content, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                    }
                }
            }

            if (recordings.isNotEmpty() || aiAudio.isNotEmpty()) {
                item { Text("Audio (${recordings.size + aiAudio.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(recordings, key = { "r_${it.id}" }) { rec ->
                    AudioItemCard(
                        title = rec.title,
                        subtitle = "Voice • ${DateTimeUtils.formatDurationMs(rec.durationMs)}",
                        isAi = false,
                        onPlay = { viewModel.playAudio(rec.filePath, rec.title) },
                        onDelete = { viewModel.deleteRecording(rec) }
                    )
                }
                items(aiAudio, key = { "aa_${it.id}" }) { aud ->
                    AudioItemCard(
                        title = aud.title,
                        subtitle = "AI Audio • ${DateTimeUtils.formatDurationMs(aud.durationMs)}",
                        isAi = true,
                        onPlay = { viewModel.playAudio(aud.filePath, aud.title) },
                        onDelete = { viewModel.deleteAiAudio(aud) }
                    )
                }
            }

            if (quizzes.isNotEmpty()) {
                item { Text("Quizzes (${quizzes.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(quizzes, key = { "q_${it.id}" }) { quiz ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenQuizRunner(quiz.id) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                            Icon(Icons.Default.Quiz, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(quiz.title, modifier = Modifier.weight(1f))
                            Text("${quiz.questionCount} Qs", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    pendingImportUri?.let { uri ->
        ImportDocumentDialog(
            uri = uri,
            viewModel = viewModel,
            initialSubjectId = subjectId,
            initialTopicId = topicId,
            onDismiss = { pendingImportUri = null }
        )
    }

    docToRename?.let { doc ->
        var newName by remember(doc) { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { docToRename = null },
            title = { Text("Rename Document") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = {
                Button(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    onClick = { viewModel.renameDocument(doc, newName); docToRename = null }
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

    if (showVoiceRecorder) {
        VoiceRecorderDialog(viewModel, subjectId, topicId, docs) { showVoiceRecorder = false }
    }
    if (showAiAudio) {
        AiAudioGeneratorDialog(viewModel, subjectId, topicId, writtenNotes, aiNotes) { showAiAudio = false }
    }
    if (showQuizGen) {
        QuizGeneratorDialog(
            viewModel, subjectId, topicId, docs, writtenNotes, aiNotes,
            onDismiss = { showQuizGen = false },
            onQuizCreated = { id -> showQuizGen = false; onOpenQuizRunner(id) }
        )
    }
    if (showFlashcards) {
        FlashcardsManagerDialog(viewModel, subjectId, topicId, flashcards, docs, writtenNotes) { showFlashcards = false }
    }
}
