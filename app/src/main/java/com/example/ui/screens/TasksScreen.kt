package com.example.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.DateTimeUtils
import com.example.data.db.TaskEntity
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StudoraTopBar
import com.example.ui.components.openAppSettings
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.StudoraGold
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

@Composable
fun TasksScreen(
    viewModel: MainViewModel,
    capturedTaskPhotoPath: String? = null,
    onConsumeCapturedPhoto: () -> Unit = {},
    onOpenCameraForTask: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()

    var selectedBucket by remember { mutableStateOf("Tomorrow") }
    var searchQuery by remember { mutableStateOf("") }
    var showTaskDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }

    LaunchedEffect(capturedTaskPhotoPath) {
        if (!capturedTaskPhotoPath.isNullOrBlank()) {
            showTaskDialog = true
        }
    }

    val todayMid = DateTimeUtils.todayMidnight()
    val tomorrowMid = DateTimeUtils.tomorrowMidnight()

    val filtered = remember(tasks, selectedBucket, searchQuery) {
        val q = searchQuery.trim().lowercase()
        tasks.filter { t ->
            val taskDay = DateTimeUtils.startOfDayMillis(t.taskDateMillis)
            val bucketMatch = when (selectedBucket) {
                "Today" -> t.status == "PENDING" && taskDay == todayMid
                "Tomorrow" -> t.status == "PENDING" && taskDay == tomorrowMid
                "Upcoming" -> t.status == "PENDING" && taskDay > tomorrowMid
                "Overdue" -> t.status == "PENDING" && taskDay < todayMid
                "Completed" -> t.status == "COMPLETED"
                else -> true
            }
            val textMatch = q.isBlank() || t.title.lowercase().contains(q) || t.description.lowercase().contains(q)
            bucketMatch && textMatch
        }
    }

    val buckets = listOf("Today", "Tomorrow", "Upcoming", "Overdue", "Completed")

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = "Task for Tomorrow",
                subtitle = "${tasks.count { it.status == "PENDING" }} pending • Native local reminders",
                onBack = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingTask = null
                    showTaskDialog = true
                },
                icon = { Icon(Icons.Default.AddTask, null) },
                text = { Text("Add Task", maxLines = 1, softWrap = false) },
                modifier = Modifier.testTag("add_task_fab")
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search tasks...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(buckets) { b ->
                        FilterChip(
                            selected = selectedBucket == b,
                            onClick = { selectedBucket = b },
                            label = { Text(b) }
                        )
                    }
                }
            }

            if (!viewModel.repo.reminderScheduler.areNotificationsEnabledInSystem()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                "Notifications are disabled. Enable to receive task reminders.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { openAppSettings(context) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) { Text("Settings", maxLines = 1, softWrap = false) }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.TaskAlt,
                        title = "No $selectedBucket Tasks",
                        subtitle = "Create tasks defaulting to Tomorrow with priority levels, photo attachments, and native Android notifications.",
                        actionLabel = "Add Task",
                        onAction = {
                            editingTask = null
                            showTaskDialog = true
                        }
                    )
                }
            } else {
                items(filtered, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggleComplete = { viewModel.toggleTaskComplete(task) },
                        onEdit = { editingTask = task; showTaskDialog = true },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }
        }
    }

    if (showTaskDialog) {
        TaskFormDialog(
            initial = editingTask,
            preAttachedPhotoPath = capturedTaskPhotoPath,
            viewModel = viewModel,
            onOpenCamera = onOpenCameraForTask,
            onDismiss = {
                onConsumeCapturedPhoto()
                showTaskDialog = false
            },
            onSave = { task ->
                viewModel.saveTask(task) {
                    onConsumeCapturedPhoto()
                    showTaskDialog = false
                }
            }
        )
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDone = task.status == "COMPLETED"
    val priorityColor = when (task.priority) {
        "HIGH" -> CrimsonError
        "LOW" -> EmeraldSuccess
        else -> StudoraGold
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Checkbox(checked = isDone, onCheckedChange = { onToggleComplete() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null
                )
                if (task.description.isNotBlank()) {
                    Text(task.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        DateTimeUtils.formatShortDate(task.taskDateMillis),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        task.priority,
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColor
                    )
                    if (task.isReminderEnabled && task.reminderTimeMinutes != null) {
                        Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(DateTimeUtils.formatMinutesOfDay(task.reminderTimeMinutes), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            task.imagePath?.let { path ->
                val f = File(path)
                if (f.exists()) {
                    AsyncImage(
                        model = f,
                        contentDescription = "Task image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete task", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskFormDialog(
    initial: TaskEntity?,
    preAttachedPhotoPath: String?,
    viewModel: MainViewModel,
    onOpenCamera: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var taskDateMillis by remember { mutableLongStateOf(initial?.taskDateMillis ?: DateTimeUtils.tomorrowMidnight()) }
    var enableReminder by remember { mutableStateOf(initial?.isReminderEnabled ?: false) }
    var reminderMinutes by remember { mutableIntStateOf(initial?.reminderTimeMinutes ?: (9 * 60)) }
    var priority by remember { mutableStateOf(initial?.priority ?: "MEDIUM") }
    var imagePath by remember { mutableStateOf(preAttachedPhotoPath ?: initial?.imagePath) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.notifyUser("Notification permission denied. Task will save without alarm notification.")
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                viewModel.repo.storage.copyUriToDirectory(uri, viewModel.repo.storage.tasksDir).onSuccess { f ->
                    imagePath = f.absolutePath
                }.onFailure {
                    viewModel.notifyUser(it.localizedMessage ?: "Failed to attach image.")
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Task" else "Edit Task") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it; errorMsg = null },
                        label = { Text("Task Name *") },
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = taskDateMillis }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val picked = Calendar.getInstance().apply {
                                            set(y, m, d, 0, 0, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        taskDateMillis = picked.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                DateTimeUtils.formatShortDate(taskDateMillis),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, h, m -> reminderMinutes = h * 60 + m },
                                    reminderMinutes / 60,
                                    reminderMinutes % 60,
                                    false
                                ).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                DateTimeUtils.formatMinutesOfDay(reminderMinutes),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable Reminder Notification", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = enableReminder,
                            onCheckedChange = { checked ->
                                enableReminder = checked
                                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            }
                        )
                    }
                    if (enableReminder) {
                        Text(
                            "Note: Android battery optimization may delay alarms slightly on some devices unless Exact Alarms are allowed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    Text("Priority", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("LOW", "MEDIUM", "HIGH").forEach { p ->
                            FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p) })
                        }
                    }
                }
                item {
                    Text("Image Attachment (optional)", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            onClick = {
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        ) { Text("Gallery", maxLines = 1, softWrap = false) }
                        OutlinedButton(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            onClick = onOpenCamera
                        ) { Text("Camera", maxLines = 1, softWrap = false) }
                        if (imagePath != null) {
                            TextButton(
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                onClick = { imagePath = null }
                            ) { Text("Remove", maxLines = 1, softWrap = false) }
                        }
                    }
                    imagePath?.let { path ->
                        Text("Attached: ${File(path).name}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (errorMsg != null) {
                    item { Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            Button(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                onClick = {
                    if (title.isBlank()) { errorMsg = "Task Name is required."; return@Button }
                    onSave(
                        (initial ?: TaskEntity(title = "", taskDateMillis = taskDateMillis)).copy(
                            title = title.trim(),
                            description = description.trim(),
                            taskDateMillis = taskDateMillis,
                            reminderTimeMinutes = if (enableReminder) reminderMinutes else null,
                            isReminderEnabled = enableReminder,
                            imagePath = imagePath,
                            priority = priority
                        )
                    )
                }
            ) { Text("Save Task", maxLines = 1, softWrap = false) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("Cancel", maxLines = 1, softWrap = false) }
        }
    )
}
