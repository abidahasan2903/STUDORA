package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.DateTimeUtils
import com.example.data.db.*
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.PermissionRationaleCard
import com.example.ui.components.StudoraTopBar
import com.example.ui.components.openAppSettings
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.StudoraGold
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.util.Locale

@Composable
fun WrittenNoteEditorScreen(
    subjectId: Long,
    topicId: Long?,
    noteId: Long,
    docId: Long?,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val docs by viewModel.observeDocumentsForSubject(subjectId).collectAsStateWithLifecycle(emptyList())
    val topics by viewModel.observeTopics(subjectId).collectAsStateWithLifecycle(emptyList())

    var loadedId by remember { mutableLongStateOf(noteId) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedTopicId by remember { mutableStateOf(topicId) }
    var selectedDocId by remember { mutableStateOf(docId) }
    var createdAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var updatedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var initialHash by remember { mutableIntStateOf(0) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(noteId) {
        if (noteId > 0L) {
            viewModel.repo.getWrittenNote(noteId)?.let { existing ->
                title = existing.title
                content = existing.content
                selectedTopicId = existing.topicId
                selectedDocId = existing.documentId
                createdAt = existing.createdAt
                updatedAt = existing.updatedAt
                initialHash = (existing.title + existing.content).hashCode()
            }
        }
    }

    val isDirty = (title.isNotBlank() || content.isNotBlank()) &&
        (title + content).hashCode() != initialHash

    // Automatic local save every 4 seconds when dirty and title is non-empty
    LaunchedEffect(title, content, selectedTopicId, selectedDocId) {
        if (title.isNotBlank() && isDirty) {
            delay(4000)
            viewModel.saveWrittenNote(
                WrittenNoteEntity(
                    id = loadedId,
                    subjectId = subjectId,
                    topicId = selectedTopicId,
                    documentId = selectedDocId,
                    title = title.trim(),
                    content = content,
                    createdAt = createdAt
                ),
                silent = true
            ) { savedId ->
                loadedId = savedId
                updatedAt = System.currentTimeMillis()
                initialHash = (title + content).hashCode()
            }
        }
    }

    BackHandler(enabled = isDirty) {
        showUnsavedDialog = true
    }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = if (loadedId > 0L) "Edit Note" else "New Note",
                subtitle = if (loadedId > 0L) "Updated ${DateTimeUtils.formatDateTime(updatedAt)}" else "Unsaved draft",
                onBack = {
                    if (isDirty) showUnsavedDialog = true else onBack()
                },
                actions = {
                    TextButton(
                        enabled = title.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        onClick = {
                            viewModel.saveWrittenNote(
                                WrittenNoteEntity(
                                    id = loadedId,
                                    subjectId = subjectId,
                                    topicId = selectedTopicId,
                                    documentId = selectedDocId,
                                    title = title.trim(),
                                    content = content,
                                    createdAt = createdAt
                                )
                            ) { savedId ->
                                loadedId = savedId
                                updatedAt = System.currentTimeMillis()
                                initialHash = (title + content).hashCode()
                            }
                        }
                    ) {
                        Text("Save", maxLines = 1, softWrap = false)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Note Title *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (topics.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = selectedTopicId == null,
                            onClick = { selectedTopicId = null },
                            label = { Text("No Topic") }
                        )
                    }
                    items(topics) { t ->
                        FilterChip(
                            selected = selectedTopicId == t.id,
                            onClick = { selectedTopicId = t.id },
                            label = { Text(t.title) }
                        )
                    }
                }
            }

            if (docs.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = selectedDocId == null,
                            onClick = { selectedDocId = null },
                            label = { Text("No Attachment") }
                        )
                    }
                    items(docs) { d ->
                        FilterChip(
                            selected = selectedDocId == d.id,
                            onClick = { selectedDocId = d.id },
                            label = { Text("Doc: ${d.title}") }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Write your study notes here...") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Text(
                "Created: ${DateTimeUtils.formatDateTime(createdAt)} • Auto-saves locally",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showUnsavedDialog) {
        ConfirmationDialog(
            title = "Unsaved Changes",
            message = "You have unsaved edits in this note. Discard changes or save first?",
            confirmText = "Discard & Leave",
            isDestructive = true,
            onConfirm = {
                showUnsavedDialog = false
                onBack()
            },
            onDismiss = { showUnsavedDialog = false }
        )
    }
}

@Composable
fun AiNoteGeneratorScreen(
    subjectId: Long?,
    topicId: Long?,
    docId: Long?,
    aiNoteId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val allDocs by viewModel.allDocuments.collectAsStateWithLifecycle()
    val allWrittenNotes by viewModel.allWrittenNotes.collectAsStateWithLifecycle()
    val aiNoteState by viewModel.aiNoteState.collectAsStateWithLifecycle()

    val candidateDocs = remember(allDocs, subjectId) {
        if (subjectId != null && subjectId > 0) allDocs.filter { it.subjectId == subjectId } else allDocs
    }

    var selectedDoc by remember(docId, candidateDocs) {
        mutableStateOf(candidateDocs.firstOrNull { it.id == docId } ?: candidateDocs.firstOrNull())
    }
    var prompt by remember { mutableStateOf("Create clear, exam-ready study notes with key concepts and bullet summaries.") }
    var noteTitle by remember { mutableStateOf("") }
    var editableResult by remember { mutableStateOf("") }
    var currentSavedId by remember { mutableLongStateOf(aiNoteId) }

    LaunchedEffect(aiNoteId) {
        viewModel.resetAiNoteState()
        if (aiNoteId > 0L) {
            viewModel.repo.allAiNotes.collect { list ->
                list.firstOrNull { it.id == aiNoteId }?.let { saved ->
                    noteTitle = saved.title
                    prompt = saved.prompt
                    editableResult = saved.content
                }
            }
        }
    }

    LaunchedEffect(aiNoteState) {
        when (val s = aiNoteState) {
            is AiTaskState.Streaming -> editableResult = s.text
            is AiTaskState.Success -> {
                editableResult = s.text
                if (noteTitle.isBlank()) {
                    noteTitle = selectedDoc?.title?.let { "$it — AI Notes" } ?: "AI Study Notes"
                }
            }
            else -> Unit
        }
    }

    val quickPrompts = listOf(
        "Explain this PDF in Bangla.",
        "Create easy English exam notes.",
        "Make engineering-standard notes.",
        "Explain every slide separately.",
        "Generate important questions and answers.",
        "Translate the material into Bangla."
    )

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = "AI Note Generator",
                subtitle = selectedDoc?.title ?: "From topic or written notes",
                onBack = onBack,
                actions = {
                    if (editableResult.isNotBlank()) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(editableResult))
                            viewModel.notifyUser("Copied notes to clipboard.")
                        }) { Icon(Icons.Default.ContentCopy, "Copy") }
                        IconButton(onClick = {
                            viewModel.repo.storage.shareText(editableResult, noteTitle.ifBlank { "AI Notes" })
                        }) { Icon(Icons.Default.Share, "Share") }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (candidateDocs.isNotEmpty()) {
                Text("Select Document / PDF", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(candidateDocs) { d ->
                        FilterChip(
                            selected = selectedDoc?.id == d.id,
                            onClick = { selectedDoc = d },
                            label = { Text(d.title) }
                        )
                    }
                }
            }

            Text("Quick Prompts", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickPrompts) { qp ->
                    AssistChip(onClick = { prompt = qp }, label = { Text(qp) })
                }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Describe the notes you want AI to create") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            val fallbackText = remember(allWrittenNotes, subjectId, topicId) {
                allWrittenNotes.filter {
                    (topicId != null && it.topicId == topicId) || (subjectId != null && it.subjectId == subjectId)
                }.joinToString("\n\n") { "${it.title}:\n${it.content}" }
            }

            val isBusy = aiNoteState is AiTaskState.Loading || aiNoteState is AiTaskState.Streaming
            Button(
                enabled = !isBusy && prompt.isNotBlank() && (selectedDoc != null || fallbackText.isNotBlank()),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                onClick = {
                    viewModel.generateAiNotes(
                        document = selectedDoc,
                        fallbackTextContext = fallbackText.ifBlank { null },
                        prompt = prompt.trim()
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isBusy) "Processing..." else "Generate AI Notes",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when (val s = aiNoteState) {
                is AiTaskState.Loading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(s.status, style = MaterialTheme.typography.bodySmall)
                }
                is AiTaskState.Streaming -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(s.status, style = MaterialTheme.typography.bodySmall)
                }
                is AiTaskState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                s.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                onClick = {
                                    viewModel.generateAiNotes(selectedDoc, fallbackText.ifBlank { null }, prompt.trim())
                                }
                            ) { Text("Retry", maxLines = 1, softWrap = false) }
                        }
                    }
                }
                else -> Unit
            }

            if (editableResult.isNotBlank()) {
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    label = { Text("Saved Note Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editableResult,
                    onValueChange = { editableResult = it },
                    label = { Text("Generated AI Notes (Editable)") },
                    minLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    onClick = {
                        viewModel.saveGeneratedAiNote(
                            existingId = currentSavedId,
                            title = noteTitle.ifBlank { selectedDoc?.title ?: "AI Study Notes" },
                            prompt = prompt,
                            content = editableResult,
                            subjectId = subjectId ?: selectedDoc?.subjectId,
                            topicId = topicId ?: selectedDoc?.topicId,
                            documentId = selectedDoc?.id
                        ) { savedId -> currentSavedId = savedId }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Note Offline", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun QuizRunnerScreen(
    quizId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val quizzes by viewModel.allQuizzes.collectAsStateWithLifecycle()
    val quiz = quizzes.firstOrNull { it.id == quizId }
    val questions by viewModel.observeQuestionsForQuiz(quizId).collectAsStateWithLifecycle(emptyList())
    val attempts by viewModel.observeAttemptsForQuiz(quizId).collectAsStateWithLifecycle(emptyList())

    var currentIdx by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateMapOf<Long, String>() }
    var submitted by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = quiz?.title ?: "AI Quiz",
                subtitle = if (questions.isNotEmpty()) "${questions.size} questions • Offline ready" else null,
                onBack = onBack
            )
        }
    ) { innerPadding ->
        if (questions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (!submitted) {
            val q = questions[currentIdx.coerceIn(0, questions.lastIndex)]
            val options = remember(q.optionsJson) {
                runCatching {
                    val arr = JSONArray(q.optionsJson)
                    (0 until arr.length()).map { arr.getString(it) }
                }.getOrDefault(emptyList())
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                LinearProgressIndicator(
                    progress = { (currentIdx + 1).toFloat() / questions.size },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Question ${currentIdx + 1} of ${questions.size} (${q.questionType})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(q.questionText, style = MaterialTheme.typography.titleLarge)

                if (options.isNotEmpty()) {
                    options.forEach { opt ->
                        val selected = answers[q.id] == opt
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { answers[q.id] = opt }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                RadioButton(selected = selected, onClick = { answers[q.id] = opt })
                                Spacer(Modifier.width(8.dp))
                                Text(opt, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = answers[q.id] ?: "",
                        onValueChange = { answers[q.id] = it },
                        label = { Text("Your Answer") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        enabled = currentIdx > 0,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        onClick = { currentIdx-- },
                        modifier = Modifier.weight(1f)
                    ) { Text("Previous", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                    if (currentIdx < questions.lastIndex) {
                        Button(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            onClick = { currentIdx++ },
                            modifier = Modifier.weight(1f)
                        ) { Text("Next", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                    } else {
                        Button(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            onClick = {
                                val score = questions.count { item ->
                                    val userAns = answers[item.id]?.trim().orEmpty()
                                    if (item.optionsJson != "[]") {
                                        userAns.equals(item.correctAnswer.trim(), ignoreCase = true)
                                    } else {
                                        userAns.isNotBlank() && item.correctAnswer.lowercase().split(" ")
                                            .any { kw -> kw.length > 4 && userAns.lowercase().contains(kw) }
                                    }
                                }
                                viewModel.saveQuizAttempt(quizId, score, questions.size, answers.toMap())
                                submitted = true
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Submit Quiz", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        } else {
            val score = questions.count { item ->
                val userAns = answers[item.id]?.trim().orEmpty()
                if (item.optionsJson != "[]") {
                    userAns.equals(item.correctAnswer.trim(), ignoreCase = true)
                } else {
                    userAns.isNotBlank() && item.correctAnswer.lowercase().split(" ")
                        .any { kw -> kw.length > 4 && userAns.lowercase().contains(kw) }
                }
            }

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
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text("Quiz Result", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "$score / ${questions.size}",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Previous attempts saved locally: ${attempts.size}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                onClick = {
                                    answers.clear()
                                    currentIdx = 0
                                    submitted = false
                                }
                            ) { Text("Retake Quiz", maxLines = 1, softWrap = false) }
                        }
                    }
                }

                items(questions) { q ->
                    val userAns = answers[q.id].orEmpty()
                    val correct = userAns.trim().equals(q.correctAnswer.trim(), ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text(q.questionText, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Your answer: ${userAns.ifBlank { "No answer" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (correct) EmeraldSuccess else MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Correct answer: ${q.correctAnswer}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldSuccess
                            )
                            if (q.explanation.isNotBlank()) {
                                Text(
                                    "Explanation: ${q.explanation}",
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

@Composable
fun VoiceRecorderDialog(
    viewModel: MainViewModel,
    subjectId: Long?,
    topicId: Long?,
    docs: List<DocumentEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val recState by viewModel.recordingState.collectAsStateWithLifecycle()

    var hasMicPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasMicPerm = it }

    var title by remember { mutableStateOf("Voice Note ${DateTimeUtils.formatShortDate(System.currentTimeMillis())}") }
    var attachDocId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!recState.isRecording) onDismiss()
        },
        title = { Text("Personal Voice Note") },
        text = {
            if (!hasMicPerm) {
                PermissionRationaleCard(
                    icon = Icons.Default.Mic,
                    title = "Microphone Permission",
                    explanation = "STUDORA records your lecture memos directly to local audio files using your phone's physical microphone.",
                    primaryLabel = "Grant Mic",
                    onRequestPermission = { micPermLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onOpenSettings = { openAppSettings(context) }
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Recording Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (docs.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = attachDocId == null,
                                    onClick = { attachDocId = null },
                                    label = { Text("No PDF Link") }
                                )
                            }
                            items(docs) { d ->
                                FilterChip(
                                    selected = attachDocId == d.id,
                                    onClick = { attachDocId = d.id },
                                    label = { Text(d.title) }
                                )
                            }
                        }
                    }

                    Text(
                        DateTimeUtils.formatDurationMs(recState.elapsedMs),
                        style = MaterialTheme.typography.displaySmall,
                        color = if (recState.isRecording && !recState.isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )

                    LinearProgressIndicator(
                        progress = { (recState.amplitude / 24000f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (recState.errorMessage != null) {
                        Text(recState.errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!recState.isRecording) {
                            Button(
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                onClick = { viewModel.startMicRecording() }
                            ) {
                                Icon(Icons.Default.Mic, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Start", maxLines = 1, softWrap = false)
                            }
                        } else {
                            OutlinedButton(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                onClick = {
                                    if (recState.isPaused) viewModel.resumeMicRecording() else viewModel.pauseMicRecording()
                                }
                            ) {
                                Icon(if (recState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                                Spacer(Modifier.width(4.dp))
                                Text(if (recState.isPaused) "Resume" else "Pause", maxLines = 1, softWrap = false)
                            }
                            Button(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                onClick = {
                                    viewModel.stopAndSaveMicRecording(title, subjectId, topicId, attachDocId) { ok ->
                                        if (ok) onDismiss()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Stop, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Save", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                onClick = {
                    if (recState.isRecording) viewModel.cancelMicRecording()
                    onDismiss()
                }
            ) { Text("Close", maxLines = 1, softWrap = false) }
        }
    )
}

@Composable
fun AiAudioGeneratorDialog(
    viewModel: MainViewModel,
    subjectId: Long?,
    topicId: Long?,
    writtenNotes: List<WrittenNoteEntity>,
    aiNotes: List<AiNoteEntity>,
    onDismiss: () -> Unit
) {
    val ttsProgress by viewModel.ttsProgress.collectAsStateWithLifecycle()
    var languages by remember { mutableStateOf(listOf(Locale.US)) }
    var selectedLang by remember { mutableStateOf("en-US") }
    var speechRate by remember { mutableFloatStateOf(1.0f) }
    var title by remember { mutableStateOf("Study Audio Narration") }
    var sourceText by remember {
        mutableStateOf(
            aiNotes.firstOrNull()?.content ?: writtenNotes.firstOrNull()?.content ?: ""
        )
    }

    LaunchedEffect(Unit) {
        languages = viewModel.getTtsLanguages().take(12)
    }

    AlertDialog(
        onDismissRequest = { if (!ttsProgress.isSynthesizing) onDismiss() },
        title = { Text("Generate AI Audio Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (writtenNotes.isNotEmpty() || aiNotes.isNotEmpty()) {
                    Text("Load text from saved note:", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(writtenNotes) { n ->
                            AssistChip(
                                onClick = { title = "${n.title} (Audio)"; sourceText = n.content },
                                label = { Text(n.title) }
                            )
                        }
                        items(aiNotes) { n ->
                            AssistChip(
                                onClick = { title = "${n.title} (Audio)"; sourceText = n.content },
                                label = { Text("AI: ${n.title}") }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Audio Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sourceText,
                    onValueChange = { sourceText = it },
                    label = { Text("Narration Text") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Language / Voice", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(languages) { loc ->
                        val tag = loc.toLanguageTag()
                        FilterChip(
                            selected = selectedLang == tag,
                            onClick = { selectedLang = tag },
                            label = { Text(loc.displayName) }
                        )
                    }
                }
                Text("Speed: ${speechRate}x", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = speechRate,
                    onValueChange = { speechRate = ((it * 4).toInt() / 4f) },
                    valueRange = 0.75f..1.5f
                )
                if (ttsProgress.isSynthesizing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(ttsProgress.statusText, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !ttsProgress.isSynthesizing && sourceText.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                onClick = {
                    viewModel.generateAiAudio(
                        title = title,
                        text = sourceText,
                        sourceType = "NOTE",
                        sourceId = null,
                        subjectId = subjectId,
                        topicId = topicId,
                        languageTag = selectedLang,
                        speechRate = speechRate
                    ) { res ->
                        if (res.isSuccess) onDismiss()
                    }
                }
            ) { Text("Generate & Save", maxLines = 1, softWrap = false) }
        },
        dismissButton = {
            TextButton(
                enabled = !ttsProgress.isSynthesizing,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                onClick = onDismiss
            ) { Text("Cancel", maxLines = 1, softWrap = false) }
        }
    )
}

@Composable
fun QuizGeneratorDialog(
    viewModel: MainViewModel,
    subjectId: Long?,
    topicId: Long?,
    docs: List<DocumentEntity>,
    writtenNotes: List<WrittenNoteEntity>,
    aiNotes: List<AiNoteEntity>,
    onDismiss: () -> Unit,
    onQuizCreated: (Long) -> Unit
) {
    val genState by viewModel.quizGenState.collectAsStateWithLifecycle()
    var selectedDoc by remember { mutableStateOf(docs.firstOrNull()) }
    var questionType by remember { mutableStateOf("MCQ") }
    var difficulty by remember { mutableStateOf("Medium") }
    var count by remember { mutableIntStateOf(5) }

    LaunchedEffect(Unit) { viewModel.resetQuizGenState() }

    val fallbackText = remember(writtenNotes, aiNotes) {
        (writtenNotes.map { "${it.title}: ${it.content}" } + aiNotes.map { "${it.title}: ${it.content}" })
            .joinToString("\n\n")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI Quiz Generator") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (docs.isNotEmpty()) {
                    Text("Source PDF / Document", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(docs) { d ->
                            FilterChip(
                                selected = selectedDoc?.id == d.id,
                                onClick = { selectedDoc = d },
                                label = { Text(d.title) }
                            )
                        }
                    }
                }
                Text("Question Type", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("MCQ", "TRUE_FALSE", "SHORT_ANSWER", "VIVA")) { t ->
                        FilterChip(
                            selected = questionType == t,
                            onClick = { questionType = t },
                            label = { Text(t.replace('_', ' ')) }
                        )
                    }
                }
                Text("Difficulty", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Easy", "Medium", "Hard").forEach { d ->
                        FilterChip(selected = difficulty == d, onClick = { difficulty = d }, label = { Text(d) })
                    }
                }
                Text("Questions: $count", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 8, 10).forEach { c ->
                        FilterChip(selected = count == c, onClick = { count = c }, label = { Text("$c") })
                    }
                }

                when (val s = genState) {
                    is AiTaskState.Loading -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(s.status, style = MaterialTheme.typography.bodySmall)
                    }
                    is AiTaskState.Error -> {
                        Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            Button(
                enabled = genState !is AiTaskState.Loading && (selectedDoc != null || fallbackText.isNotBlank()),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                onClick = {
                    viewModel.generateQuiz(
                        subjectId = subjectId,
                        topicId = topicId,
                        documentId = selectedDoc?.id,
                        sourceText = fallbackText.ifBlank { null },
                        questionType = questionType,
                        questionCount = count,
                        difficulty = difficulty,
                        onCreated = onQuizCreated
                    )
                }
            ) { Text("Generate Quiz", maxLines = 1, softWrap = false) }
        },
        dismissButton = {
            TextButton(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                onClick = onDismiss
            ) { Text("Close", maxLines = 1, softWrap = false) }
        }
    )
}

@Composable
fun FlashcardsManagerDialog(
    viewModel: MainViewModel,
    subjectId: Long?,
    topicId: Long?,
    flashcards: List<FlashcardEntity>,
    docs: List<DocumentEntity>,
    writtenNotes: List<WrittenNoteEntity>,
    onDismiss: () -> Unit
) {
    val genState by viewModel.flashcardGenState.collectAsStateWithLifecycle()
    var studyMode by remember { mutableStateOf(flashcards.isNotEmpty()) }
    var cardIndex by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }

    var frontInput by remember { mutableStateOf("") }
    var backInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.resetFlashcardGenState() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Flashcards (${flashcards.size})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = studyMode, onClick = { studyMode = true }, label = { Text("Review Cards") })
                    FilterChip(selected = !studyMode, onClick = { studyMode = false }, label = { Text("Create / AI") })
                }

                if (studyMode) {
                    if (flashcards.isEmpty()) {
                        Text("No flashcards yet. Switch to 'Create / AI' to add or generate cards.")
                    } else {
                        val card = flashcards[cardIndex.coerceIn(0, flashcards.lastIndex)]
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp)
                                .clickable { revealed = !revealed }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(18.dp)
                            ) {
                                Text(
                                    if (revealed) "ANSWER" else "QUESTION (Tap to reveal)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (revealed) card.backAnswer else card.frontQuestion,
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Status: ${card.masteryStatus} • Reviewed ${card.reviewCount}x",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                onClick = {
                                    viewModel.reviewFlashcard(card, "DIFFICULT")
                                    revealed = false
                                    cardIndex = (cardIndex + 1) % flashcards.size
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Difficult", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                            Button(
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                onClick = {
                                    viewModel.reviewFlashcard(card, "KNOWN")
                                    revealed = false
                                    cardIndex = (cardIndex + 1) % flashcards.size
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Known", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                onClick = {
                                    revealed = false
                                    cardIndex = if (cardIndex > 0) cardIndex - 1 else flashcards.lastIndex
                                }
                            ) { Text("Prev", maxLines = 1, softWrap = false) }
                            Text("${cardIndex + 1} / ${flashcards.size}", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                            TextButton(
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                onClick = { viewModel.deleteFlashcard(card) }
                            ) {
                                Text("Delete", color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false)
                            }
                            TextButton(
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                onClick = {
                                    revealed = false
                                    cardIndex = (cardIndex + 1) % flashcards.size
                                }
                            ) { Text("Next", maxLines = 1, softWrap = false) }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = frontInput,
                        onValueChange = { frontInput = it },
                        label = { Text("Front (Question)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = backInput,
                        onValueChange = { backInput = it },
                        label = { Text("Back (Answer)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = frontInput.isNotBlank() && backInput.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        onClick = {
                            viewModel.saveFlashcard(
                                FlashcardEntity(
                                    subjectId = subjectId,
                                    topicId = topicId,
                                    frontQuestion = frontInput.trim(),
                                    backAnswer = backInput.trim()
                                )
                            )
                            frontInput = ""
                            backInput = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add Manual Card", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }

                    HorizontalDivider()
                    val textSrc = writtenNotes.joinToString("\n\n") { "${it.title}: ${it.content}" }
                    FilledTonalButton(
                        enabled = genState !is AiTaskState.Loading && (docs.isNotEmpty() || textSrc.isNotBlank()),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        onClick = {
                            viewModel.generateFlashcards(
                                subjectId = subjectId,
                                topicId = topicId,
                                documentId = docs.firstOrNull()?.id,
                                sourceText = textSrc.ifBlank { null },
                                count = 6
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Generate 6 AI Flashcards", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    when (val s = genState) {
                        is AiTaskState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        is AiTaskState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        is AiTaskState.Success -> Text(s.text, color = EmeraldSuccess, style = MaterialTheme.typography.bodySmall)
                        else -> Unit
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                onClick = onDismiss
            ) { Text("Close", maxLines = 1, softWrap = false) }
        }
    )
}
