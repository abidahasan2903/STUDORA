package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.DateTimeUtils
import com.example.data.db.AiConversationEntity
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StudoraTopBar
import com.example.ui.viewmodel.MainViewModel

@Composable
fun StudoraAiScreen(
    viewModel: MainViewModel,
    onBack: (() -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    val conversations by viewModel.aiConversations.collectAsStateWithLifecycle()
    val pendingQueue by viewModel.pendingAiRequests.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val streamingText by viewModel.aiChatStreamingText.collectAsStateWithLifecycle()
    val chatError by viewModel.aiChatError.collectAsStateWithLifecycle()
    val isSending by viewModel.isAiChatSending.collectAsStateWithLifecycle()

    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val topics by viewModel.allTopics.collectAsStateWithLifecycle()
    val docs by viewModel.allDocuments.collectAsStateWithLifecycle()
    val writtenNotes by viewModel.allWrittenNotes.collectAsStateWithLifecycle()
    val aiNotes by viewModel.allAiNotes.collectAsStateWithLifecycle()

    var activeConvId by remember { mutableStateOf<Long?>(null) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showContextPicker by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var lastSentText by remember { mutableStateOf("") }

    var selectedContextType by remember { mutableStateOf<String?>(null) }
    var selectedContextId by remember { mutableStateOf<Long?>(null) }
    var selectedContextLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(conversations) {
        if (activeConvId == null && conversations.isNotEmpty()) {
            activeConvId = conversations.first().id
        }
    }

    val activeConv = conversations.firstOrNull { it.id == activeConvId }
    val messages by remember(activeConvId) {
        if (activeConvId != null) viewModel.observeMessages(activeConvId!!)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, streamingText) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val voiceInputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
            }
        }
    }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = activeConv?.title ?: "Studora AI",
                subtitle = selectedContextLabel ?: activeConv?.contextLabel ?: "Academic & productivity companion",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showContextPicker = true }) {
                        Icon(Icons.Default.AttachFile, "Select study context")
                    }
                    IconButton(onClick = { showHistorySheet = true }) {
                        Icon(Icons.Default.History, "Conversation history")
                    }
                    IconButton(onClick = {
                        viewModel.createAiConversation("New AI Conversation") { id ->
                            activeConvId = id
                            viewModel.clearAiChatError()
                        }
                    }) {
                        Icon(Icons.Default.AddComment, "New conversation")
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
            // Explicit context disclosure banner
            val activeCtxLabel = selectedContextLabel ?: activeConv?.contextLabel
            if (!activeCtxLabel.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Shared Context: $activeCtxLabel",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = {
                                selectedContextType = null
                                selectedContextId = null
                                selectedContextLabel = null
                                activeConvId?.let { viewModel.setConversationContext(it, null, null, null) }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, "Remove context", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Offline pending queue strip
            if (pendingQueue.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "${pendingQueue.size} queued offline request(s)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(
                            enabled = isOnline && !isSending,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            onClick = { viewModel.retryPendingRequest(pendingQueue.first()) }
                        ) {
                            Text("Send Next", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            // Chat Messages List
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (messages.isEmpty() && streamingText == null) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.AutoAwesome,
                            title = "Ask STUDORA AI",
                            subtitle = "Explain complex topics in English or Bangla, debug SQL/Java/Python, summarize notes, or attach a specific Subject, Topic, or Note as context."
                        )
                        Spacer(Modifier.height(12.dp))
                        val starters = listOf(
                            "Explain DBMS normalization (1NF, 2NF, 3NF) with simple examples",
                            "Write a clear SQL JOIN cheat sheet",
                            "Explain Binary Search time complexity in Bangla",
                            "Help me structure an assignment outline"
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(starters) { s ->
                                AssistChip(onClick = { inputText = s }, label = { Text(s, maxLines = 1) })
                            }
                        }
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        val isUser = msg.role == "USER"
                        Column(
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    if (!msg.contextSummary.isNullOrBlank()) {
                                        Text(
                                            "Context: ${msg.contextSummary}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    Text(
                                        msg.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            DateTimeUtils.formatDateTime(msg.timestamp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!isUser) {
                                            IconButton(
                                                onClick = {
                                                    clipboard.setText(AnnotatedString(msg.content))
                                                    viewModel.notifyUser("Response copied.")
                                                },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, "Copy response", modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (streamingText != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    streamingText!!.ifBlank { "Thinking..." },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

                if (chatError != null) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    chatError!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                if (lastSentText.isNotBlank() && isOnline) {
                                    TextButton(
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        onClick = {
                                            activeConvId?.let { cid ->
                                                viewModel.sendAiChatMessage(
                                                    conversationId = cid,
                                                    userText = lastSentText,
                                                    contextType = selectedContextType,
                                                    contextId = selectedContextId,
                                                    alreadyRecordedUserMessage = true
                                                )
                                            }
                                        }
                                    ) { Text("Retry", maxLines = 1, softWrap = false) }
                                }
                            }
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your question for STUDORA AI")
                        }
                        runCatching { voiceInputLauncher.launch(intent) }
                            .onFailure { viewModel.notifyUser("Speech recognition is not available on this device.") }
                    }) {
                        Icon(Icons.Default.Mic, "Voice input")
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text(if (isOnline) "Ask STUDORA AI..." else "Offline • Queue message...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_chat_input")
                    )

                    Spacer(Modifier.width(6.dp))

                    FilledIconButton(
                        enabled = !isSending && inputText.isNotBlank(),
                        onClick = {
                            val prompt = inputText.trim()
                            lastSentText = prompt
                            inputText = ""
                            val currentId = activeConvId
                            if (currentId == null) {
                                viewModel.createAiConversation(
                                    title = prompt.take(40),
                                    contextType = selectedContextType,
                                    contextId = selectedContextId,
                                    contextLabel = selectedContextLabel
                                ) { newId ->
                                    activeConvId = newId
                                    viewModel.sendAiChatMessage(newId, prompt, selectedContextType, selectedContextId)
                                }
                            } else {
                                viewModel.sendAiChatMessage(currentId, prompt, selectedContextType, selectedContextId)
                            }
                        },
                        modifier = Modifier.testTag("ai_chat_send")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send")
                    }
                }
            }
        }
    }

    if (showHistorySheet) {
        AlertDialog(
            onDismissRequest = { showHistorySheet = false },
            title = { Text("Conversation History (${conversations.size})") },
            text = {
                if (conversations.isEmpty()) {
                    Text("No saved conversations yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(conversations, key = { it.id }) { conv ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeConvId = conv.id
                                        selectedContextType = conv.contextType
                                        selectedContextId = conv.contextId
                                        selectedContextLabel = conv.contextLabel
                                        showHistorySheet = false
                                    }
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(conv.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(DateTimeUtils.formatShortDate(conv.updatedAt), style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = {
                                    if (activeConvId == conv.id) activeConvId = null
                                    viewModel.deleteAiConversation(conv)
                                }) {
                                    Icon(Icons.Default.Delete, "Delete conversation", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showHistorySheet = false },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("Close", maxLines = 1, softWrap = false) }
            }
        )
    }

    if (showContextPicker) {
        AlertDialog(
            onDismissRequest = { showContextPicker = false },
            title = { Text("Select Context to Share") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Text(
                            "Only the item you explicitly choose here is shared with STUDORA AI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (subjects.isNotEmpty()) {
                        item { Text("Subjects", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                        items(subjects) { s ->
                            ListItem(
                                headlineContent = { Text(s.name) },
                                modifier = Modifier.clickable {
                                    selectedContextType = "SUBJECT"
                                    selectedContextId = s.id
                                    selectedContextLabel = "Subject: ${s.name}"
                                    activeConvId?.let { viewModel.setConversationContext(it, "SUBJECT", s.id, selectedContextLabel) }
                                    showContextPicker = false
                                }
                            )
                        }
                    }
                    if (topics.isNotEmpty()) {
                        item { Text("Topics", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                        items(topics) { t ->
                            ListItem(
                                headlineContent = { Text(t.title) },
                                modifier = Modifier.clickable {
                                    selectedContextType = "TOPIC"
                                    selectedContextId = t.id
                                    selectedContextLabel = "Topic: ${t.title}"
                                    activeConvId?.let { viewModel.setConversationContext(it, "TOPIC", t.id, selectedContextLabel) }
                                    showContextPicker = false
                                }
                            )
                        }
                    }
                    if (writtenNotes.isNotEmpty()) {
                        item { Text("Written Notes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                        items(writtenNotes) { n ->
                            ListItem(
                                headlineContent = { Text(n.title) },
                                modifier = Modifier.clickable {
                                    selectedContextType = "WRITTEN_NOTE"
                                    selectedContextId = n.id
                                    selectedContextLabel = "Note: ${n.title}"
                                    activeConvId?.let { viewModel.setConversationContext(it, "WRITTEN_NOTE", n.id, selectedContextLabel) }
                                    showContextPicker = false
                                }
                            )
                        }
                    }
                    if (aiNotes.isNotEmpty()) {
                        item { Text("AI Notes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                        items(aiNotes) { n ->
                            ListItem(
                                headlineContent = { Text(n.title) },
                                modifier = Modifier.clickable {
                                    selectedContextType = "AI_NOTE"
                                    selectedContextId = n.id
                                    selectedContextLabel = "AI Note: ${n.title}"
                                    activeConvId?.let { viewModel.setConversationContext(it, "AI_NOTE", n.id, selectedContextLabel) }
                                    showContextPicker = false
                                }
                            )
                        }
                    }
                    if (docs.isNotEmpty()) {
                        item { Text("PDFs & Documents", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                        items(docs) { d ->
                            ListItem(
                                headlineContent = { Text(d.title) },
                                modifier = Modifier.clickable {
                                    selectedContextType = "DOCUMENT"
                                    selectedContextId = d.id
                                    selectedContextLabel = "Doc: ${d.title}"
                                    activeConvId?.let { viewModel.setConversationContext(it, "DOCUMENT", d.id, selectedContextLabel) }
                                    showContextPicker = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showContextPicker = false },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("Close", maxLines = 1, softWrap = false) }
            }
        )
    }
}
