package com.example.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.DateTimeUtils
import com.example.data.db.StudyPlanEntity
import com.example.data.db.StudySessionEntity
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StudoraTopBar
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.StudoraGold
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.MainViewModel
import java.util.Calendar

private val WEEK_DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@Composable
fun StudyRoutineScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val plans by viewModel.studyPlans.collectAsStateWithLifecycle()
    val allSessions by viewModel.allStudySessions.collectAsStateWithLifecycle()
    val genState by viewModel.studyPlanGenState.collectAsStateWithLifecycle()

    var selectedPlanId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(plans) {
        if (selectedPlanId == null || plans.none { it.id == selectedPlanId }) {
            selectedPlanId = plans.firstOrNull()?.id
        }
    }

    val activePlan = plans.firstOrNull { it.id == selectedPlanId }
    val sessions = remember(allSessions, selectedPlanId) {
        allSessions.filter { it.planId == selectedPlanId }
    }

    var viewMode by remember { mutableStateOf("Weekly") } // Daily or Weekly
    var selectedDay by remember { mutableStateOf("Monday") }
    var showAiPlannerDialog by remember { mutableStateOf(false) }
    var showSessionDialog by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf<StudySessionEntity?>(null) }

    val displayedSessions = remember(sessions, viewMode, selectedDay) {
        if (viewMode == "Daily") sessions.filter { it.dayOfWeek.equals(selectedDay, ignoreCase = true) }
        else sessions
    }

    val completedCount = sessions.count { it.isCompleted }
    val completionPct = if (sessions.isEmpty()) 0f else completedCount.toFloat() / sessions.size

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = "Self Study Routine",
                subtitle = activePlan?.title ?: "AI-assisted & manual study planner",
                onBack = onBack,
                actions = {
                    if (activePlan != null) {
                        IconButton(onClick = { viewModel.deleteStudyPlan(activePlan) }) {
                            Icon(Icons.Default.DeleteOutline, "Delete plan")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAiPlannerDialog = true },
                icon = { Icon(Icons.Default.AutoAwesome, null) },
                text = { Text("New Plan", maxLines = 1, softWrap = false) }
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
            if (plans.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(plans, key = { it.id }) { p ->
                            FilterChip(
                                selected = p.id == selectedPlanId,
                                onClick = { selectedPlanId = p.id },
                                label = { Text(p.title) }
                            )
                        }
                    }
                }
            }

            if (activePlan != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Progress: ${(completionPct * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("$completedCount / ${sessions.size} sessions", style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { completionPct },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${sessions.size - completedCount} remaining • Daily target: ${activePlan.dailyStudyMinutes} min",
                                style = MaterialTheme.typography.bodySmall
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = viewMode == "Weekly", onClick = { viewMode = "Weekly" }, label = { Text("Weekly") })
                            FilterChip(selected = viewMode == "Daily", onClick = { viewMode = "Daily" }, label = { Text("Daily") })
                        }
                        TextButton(
                            onClick = { editingSession = null; showSessionDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Session", maxLines = 1, softWrap = false)
                        }
                    }
                }

                if (viewMode == "Daily") {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(WEEK_DAYS) { day ->
                                FilterChip(selected = selectedDay == day, onClick = { selectedDay = day }, label = { Text(day.take(3)) })
                            }
                        }
                    }
                }
            }

            if (activePlan == null) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.AutoAwesomeMotion,
                        title = "No Study Plans Yet",
                        subtitle = "Generate a personalized AI study schedule based on your subjects, exam dates, difficult courses, and preferred hours—or create one manually.",
                        actionLabel = "Create Study Plan",
                        onAction = { showAiPlannerDialog = true }
                    )
                }
            } else if (displayedSessions.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.EventNote,
                        title = "No Sessions for This View",
                        subtitle = "Add a study, break, or revision block manually.",
                        actionLabel = "Add Session",
                        onAction = { editingSession = null; showSessionDialog = true }
                    )
                }
            } else {
                items(displayedSessions, key = { it.id }) { session ->
                    val typeColor = when (session.sessionType) {
                        "BREAK" -> EmeraldSuccess
                        "REVISION" -> StudoraGold
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingSession = session; showSessionDialog = true }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                            Checkbox(
                                checked = session.isCompleted,
                                onCheckedChange = { viewModel.toggleStudySessionComplete(session) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    session.subjectName,
                                    style = MaterialTheme.typography.titleSmall,
                                    textDecoration = if (session.isCompleted) TextDecoration.LineThrough else null
                                )
                                Text(
                                    "${session.dayOfWeek} • ${session.startTime}–${session.endTime} (${session.durationMinutes}m)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (session.focusTopic.isNotBlank()) {
                                    Text(session.focusTopic, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Surface(
                                color = typeColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    session.sessionType,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = typeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.deleteStudySession(session) }) {
                                Icon(Icons.Default.Delete, "Delete session", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAiPlannerDialog) {
        StudyPlanCreatorDialog(
            genState = genState,
            onReset = { viewModel.resetStudyPlanGenState() },
            onDismiss = { showAiPlannerDialog = false },
            onGenerateAi = { subjects, level, examDate, examText, mins, prefHours, goals, diff, breaks, avail ->
                viewModel.generateStudyPlan(
                    subjects, level, examDate, examText, mins, prefHours, goals, diff, breaks, avail
                ) { newId ->
                    selectedPlanId = newId
                    showAiPlannerDialog = false
                }
            },
            onCreateManual = { title, subjects ->
                viewModel.createManualStudyPlan(title, subjects) { newId ->
                    selectedPlanId = newId
                    showAiPlannerDialog = false
                }
            }
        )
    }

    if (showSessionDialog && selectedPlanId != null) {
        StudySessionFormDialog(
            planId = selectedPlanId!!,
            initial = editingSession,
            nextOrder = sessions.size,
            onDismiss = { showSessionDialog = false },
            onSave = { s ->
                viewModel.saveStudySession(s)
                showSessionDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyPlanCreatorDialog(
    genState: AiTaskState,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onGenerateAi: (String, String, Long?, String, Int, String, String, String, String, String) -> Unit,
    onCreateManual: (String, String) -> Unit
) {
    val context = LocalContext.current
    var subjects by remember { mutableStateOf("") }
    var academicLevel by remember { mutableStateOf("") }
    var examDateMillis by remember { mutableStateOf<Long?>(null) }
    var dailyMinutes by remember { mutableIntStateOf(180) }
    var preferredHours by remember { mutableStateOf("6:00 PM - 10:00 PM") }
    var goals by remember { mutableStateOf("") }
    var difficultSubjects by remember { mutableStateOf("") }
    var breakPref by remember { mutableStateOf("10 min break every 50 min") }
    var weeklyAvail by remember { mutableStateOf("All 7 days") }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { onReset() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Study Routine") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = subjects,
                        onValueChange = { subjects = it; err = null },
                        label = { Text("Subjects & Priorities *") },
                        placeholder = { Text("e.g. DBMS (High), Java (Medium), Stats") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = academicLevel,
                        onValueChange = { academicLevel = it },
                        label = { Text("Academic Level") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    examDateMillis = Calendar.getInstance().apply {
                                        set(y, m, d, 0, 0, 0)
                                    }.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            examDateMillis?.let { "Exam Date: ${DateTimeUtils.formatDate(it)}" } ?: "Pick Exam Date (optional)",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                item {
                    Text("Daily Study Time: ${dailyMinutes / 60}h ${dailyMinutes % 60}m", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = dailyMinutes.toFloat(),
                        onValueChange = { dailyMinutes = (it.toInt() / 30) * 30 },
                        valueRange = 60f..480f
                    )
                }
                item {
                    OutlinedTextField(
                        value = preferredHours,
                        onValueChange = { preferredHours = it },
                        label = { Text("Preferred Study Hours") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = difficultSubjects,
                        onValueChange = { difficultSubjects = it },
                        label = { Text("Difficult Subjects") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = goals,
                        onValueChange = { goals = it },
                        label = { Text("Study Goals") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = breakPref,
                        onValueChange = { breakPref = it },
                        label = { Text("Break Preference") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = weeklyAvail,
                        onValueChange = { weeklyAvail = it },
                        label = { Text("Weekly Availability") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (err != null) {
                    item { Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                when (genState) {
                    is AiTaskState.Loading -> item {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(genState.status, style = MaterialTheme.typography.bodySmall)
                    }
                    is AiTaskState.Error -> item {
                        Text(genState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) { Text("Cancel", maxLines = 1, softWrap = false) }
                OutlinedButton(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    onClick = {
                        if (subjects.isBlank()) { err = "Enter subjects first."; return@OutlinedButton }
                        onCreateManual("Manual Plan", subjects)
                    }
                ) { Text("Manual Plan", maxLines = 1, softWrap = false) }
                Button(
                    enabled = genState !is AiTaskState.Loading,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    onClick = {
                        if (subjects.isBlank()) { err = "Please enter at least one subject."; return@Button }
                        val examStr = examDateMillis?.let { DateTimeUtils.formatDate(it) } ?: "No fixed exam date"
                        onGenerateAi(subjects, academicLevel, examDateMillis, examStr, dailyMinutes, preferredHours, goals, difficultSubjects, breakPref, weeklyAvail)
                    }
                ) { Text("AI Generate", maxLines = 1, softWrap = false) }
            }
        }
    )
}

@Composable
private fun StudySessionFormDialog(
    planId: Long,
    initial: StudySessionEntity?,
    nextOrder: Int,
    onDismiss: () -> Unit,
    onSave: (StudySessionEntity) -> Unit
) {
    var day by remember { mutableStateOf(initial?.dayOfWeek ?: "Monday") }
    var subjectName by remember { mutableStateOf(initial?.subjectName ?: "") }
    var startTime by remember { mutableStateOf(initial?.startTime ?: "18:00") }
    var endTime by remember { mutableStateOf(initial?.endTime ?: "19:00") }
    var durationStr by remember { mutableStateOf((initial?.durationMinutes ?: 60).toString()) }
    var sessionType by remember { mutableStateOf(initial?.sessionType ?: "STUDY") }
    var focusTopic by remember { mutableStateOf(initial?.focusTopic ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Study Session" else "Edit Session") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(WEEK_DAYS) { d ->
                            FilterChip(selected = day == d, onClick = { day = d }, label = { Text(d.take(3)) })
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = subjectName,
                        onValueChange = { subjectName = it },
                        label = { Text("Subject / Activity *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Start") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text("End") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = durationStr, onValueChange = { durationStr = it }, label = { Text("Min") }, modifier = Modifier.weight(0.8f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("STUDY", "BREAK", "REVISION").forEach { t ->
                            FilterChip(selected = sessionType == t, onClick = { sessionType = t }, label = { Text(t) })
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = focusTopic,
                        onValueChange = { focusTopic = it },
                        label = { Text("Focus Topic (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = subjectName.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                onClick = {
                    onSave(
                        (initial ?: StudySessionEntity(
                            planId = planId,
                            dayOfWeek = day,
                            startTime = startTime,
                            endTime = endTime,
                            durationMinutes = durationStr.toIntOrNull() ?: 45,
                            subjectName = subjectName.trim(),
                            orderIndex = nextOrder
                        )).copy(
                            dayOfWeek = day,
                            startTime = startTime.trim(),
                            endTime = endTime.trim(),
                            durationMinutes = durationStr.toIntOrNull() ?: 45,
                            subjectName = subjectName.trim(),
                            sessionType = sessionType,
                            focusTopic = focusTopic.trim()
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
