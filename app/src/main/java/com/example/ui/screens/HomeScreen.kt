package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.DateTimeUtils
import com.example.ui.components.SectionHeader
import com.example.ui.navigation.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

data class DashboardModuleCard(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val route: StudoraRoute,
    val testTag: String
)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigate: (StudoraRoute) -> Unit
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val writtenNotes by viewModel.allWrittenNotes.collectAsStateWithLifecycle()
    val aiNotes by viewModel.allAiNotes.collectAsStateWithLifecycle()
    val documents by viewModel.allDocuments.collectAsStateWithLifecycle()
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val bills by viewModel.allBills.collectAsStateWithLifecycle()
    val transactions by viewModel.walletTransactions.collectAsStateWithLifecycle()
    val studySessions by viewModel.allStudySessions.collectAsStateWithLifecycle()
    val portals by viewModel.portalEntries.collectAsStateWithLifecycle()
    val contacts by viewModel.emergencyContacts.collectAsStateWithLifecycle()
    val routines by viewModel.classRoutines.collectAsStateWithLifecycle()
    val timetable by viewModel.timetableEntries.collectAsStateWithLifecycle()
    val busSchedules by viewModel.busSchedules.collectAsStateWithLifecycle()
    val foodCharts by viewModel.foodCharts.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }

    val todayMid = DateTimeUtils.todayMidnight()
    val tomorrowMid = DateTimeUtils.tomorrowMidnight()

    val todayTasks = tasks.filter { it.status == "PENDING" && DateTimeUtils.startOfDayMillis(it.taskDateMillis) == todayMid }
    val tomorrowTasks = tasks.filter { it.status == "PENDING" && DateTimeUtils.startOfDayMillis(it.taskDateMillis) == tomorrowMid }
    val pendingBills = bills.filter { it.status == "PENDING" }
    val pendingBillsTotal = pendingBills.sumOf { it.amount }
    val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val balance = totalIncome - totalExpense
    val completedSessions = studySessions.count { it.isCompleted }
    val favoritePortals = portals.filter { it.isFavorite }

    val moduleCards = remember(
        routines.size, timetable.size, busSchedules.size, pendingBills.size,
        studySessions.size, completedSessions, subjects.size, tomorrowTasks.size,
        balance, foodCharts.size, contacts.size, portals.size
    ) {
        listOf(
            DashboardModuleCard(
                "Class Routine",
                "${routines.size} docs • ${timetable.size} classes",
                Icons.Default.CalendarViewWeek,
                StudoraNavy,
                ClassRoutineRoute,
                "card_class_routine"
            ),
            DashboardModuleCard(
                "Bus Schedule",
                "${busSchedules.size} saved",
                Icons.Default.DirectionsBus,
                SkyInfo,
                BusScheduleRoute,
                "card_bus_schedule"
            ),
            DashboardModuleCard(
                "Bill Payment Alert",
                "${pendingBills.size} pending",
                Icons.Default.ReceiptLong,
                RoseAccent,
                BillsRoute,
                "card_bills"
            ),
            DashboardModuleCard(
                "Self Study Routine",
                if (studySessions.isEmpty()) "Create study plan" else "$completedSessions/${studySessions.size} done",
                Icons.Default.AutoAwesomeMotion,
                IndigoAccent,
                StudyRoutineRoute,
                "card_study_routine"
            ),
            DashboardModuleCard(
                "My Notes",
                "${subjects.size} subjects • ${documents.size} docs",
                Icons.AutoMirrored.Filled.MenuBook,
                TealAccent,
                MyNotesRoute,
                "card_my_notes"
            ),
            DashboardModuleCard(
                "Task for Tomorrow",
                "${tomorrowTasks.size} tomorrow • ${todayTasks.size} today",
                Icons.Default.TaskAlt,
                EmeraldSuccess,
                TasksRoute,
                "card_tasks"
            ),
            DashboardModuleCard(
                "My Wallet",
                "Balance: ${DateTimeUtils.formatCurrency(balance)}",
                Icons.Default.AccountBalanceWallet,
                StudoraGold,
                WalletRoute,
                "card_wallet"
            ),
            DashboardModuleCard(
                "Nutrition Guide",
                "${foodCharts.size} food charts",
                Icons.Default.RestaurantMenu,
                Color(0xFF16A34A),
                NutritionRoute,
                "card_nutrition"
            ),
            DashboardModuleCard(
                "Emergency Contact",
                "${contacts.size} saved",
                Icons.Default.ContactPhone,
                CrimsonError,
                EmergencyContactsRoute,
                "card_emergency"
            ),
            DashboardModuleCard(
                "My Portal",
                "${portals.size} websites",
                Icons.Default.Public,
                Color(0xFF0284C7),
                MyPortalRoute,
                "card_portal"
            ),
            DashboardModuleCard(
                "Studora AI",
                "Ask & explain offline notes",
                Icons.Default.AutoAwesome,
                StudoraGold,
                StudoraAiRoute,
                "card_studora_ai"
            )
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Hero Welcome Header with STUDORA logo
        item {
            Surface(
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(StudoraNavyDark, StudoraNavy, StudoraNavyLight)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Image(
                                    painter = painterResource(R.drawable.studora_logo),
                                    contentDescription = "STUDORA Logo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .height(52.dp)
                                        .aspectRatio(1067f / 1014f)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (profile.displayName.isNotBlank()) {
                                            "Welcome, ${profile.displayName}"
                                        } else {
                                            "STUDORA"
                                        },
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = DateTimeUtils.formatDayHeader(System.currentTimeMillis()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StudoraGoldBright
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onNavigate(ProfileSettingsRoute) },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.14f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Settings, "Profile and Settings")
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Search subjects, notes, tasks, portals...",
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, null, tint = StudoraGoldBright)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, "Clear search", tint = Color.White)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = StudoraGoldBright,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_search_input")
                        )
                    }
                }
            }
        }

        // Global Search Results when typing
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            val matchedSubjects = subjects.filter { it.name.lowercase().contains(q) || it.code.lowercase().contains(q) }
            val matchedNotes = writtenNotes.filter { it.title.lowercase().contains(q) || it.content.lowercase().contains(q) }
            val matchedTasks = tasks.filter { it.title.lowercase().contains(q) }
            val matchedPortals = portals.filter { it.websiteName.lowercase().contains(q) || it.websiteUrl.lowercase().contains(q) }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        "Search Results",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (matchedSubjects.isEmpty() && matchedNotes.isEmpty() && matchedTasks.isEmpty() && matchedPortals.isEmpty()) {
                        Text(
                            "No local records matched \"$searchQuery\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        matchedSubjects.forEach { s ->
                            SearchResultRow(Icons.AutoMirrored.Filled.MenuBook, s.name, "Subject") {
                                onNavigate(SubjectDetailRoute(s.id))
                            }
                        }
                        matchedNotes.forEach { n ->
                            SearchResultRow(Icons.Default.Description, n.title, "Written Note") {
                                onNavigate(WrittenNoteEditorRoute(n.subjectId, n.topicId ?: -1L, n.id))
                            }
                        }
                        matchedTasks.forEach { t ->
                            SearchResultRow(Icons.Default.TaskAlt, t.title, "Task • ${DateTimeUtils.formatShortDate(t.taskDateMillis)}") {
                                onNavigate(TasksRoute)
                            }
                        }
                        matchedPortals.forEach { p ->
                            SearchResultRow(Icons.Default.Public, p.websiteName, p.websiteUrl) {
                                onNavigate(MyPortalRoute)
                            }
                        }
                    }
                }
            }
        }

        // Quick Actions Strip
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(title = "Quick Actions")
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        AssistChip(
                            onClick = { onNavigate(CameraScannerRoute("SCANNER", "DOCUMENT")) },
                            label = { Text("Scan PDF") },
                            leadingIcon = { Icon(Icons.Default.DocumentScanner, null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { onNavigate(TasksRoute) },
                            label = { Text("+ Task") },
                            leadingIcon = { Icon(Icons.Default.AddTask, null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { onNavigate(StudoraAiRoute) },
                            label = { Text("Ask Studora AI") },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { onNavigate(WalletRoute) },
                            label = { Text("+ Expense") },
                            leadingIcon = { Icon(Icons.Default.Payments, null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }

        // Real Live Summary Snapshot Row (Today / Tomorrow / Pending Bills)
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                SnapshotStatCard(
                    title = "Today Tasks",
                    value = todayTasks.size.toString(),
                    accent = EmeraldSuccess,
                    onClick = { onNavigate(TasksRoute) },
                    modifier = Modifier.weight(1f)
                )
                SnapshotStatCard(
                    title = "Tomorrow",
                    value = tomorrowTasks.size.toString(),
                    accent = SkyInfo,
                    onClick = { onNavigate(TasksRoute) },
                    modifier = Modifier.weight(1f)
                )
                SnapshotStatCard(
                    title = "Bills Due",
                    value = DateTimeUtils.formatCurrency(pendingBillsTotal),
                    accent = RoseAccent,
                    onClick = { onNavigate(BillsRoute) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Favorite Portals Quick Strip (when user has favorites)
        if (favoritePortals.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader("Favorite Portals", "All Portals", onAction = { onNavigate(MyPortalRoute) })
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(favoritePortals.take(8)) { portal ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.clickable {
                                    viewModel.markPortalOpened(portal)
                                    onNavigate(InAppBrowserRoute(portal.websiteUrl, portal.websiteName))
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        null,
                                        tint = StudoraGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(portal.websiteName, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Notes Snapshot
        if (writtenNotes.isNotEmpty() || aiNotes.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader("Recent Notes", "My Notes", onAction = { onNavigate(MyNotesRoute) })
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(writtenNotes.take(4)) { note ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .width(200.dp)
                                    .clickable {
                                        onNavigate(WrittenNoteEditorRoute(note.subjectId, note.topicId ?: -1L, note.id))
                                    }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        note.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        note.content.ifBlank { "Empty note" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        items(aiNotes.take(3)) { note ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, StudoraGold.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .width(200.dp)
                                    .clickable {
                                        onNavigate(
                                            AiNoteGeneratorRoute(
                                                subjectId = note.subjectId ?: -1L,
                                                topicId = note.topicId ?: -1L,
                                                docId = note.documentId ?: -1L,
                                                aiNoteId = note.id
                                            )
                                        )
                                    }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, null, tint = StudoraGold, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            note.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        note.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // All 11 Dashboard Feature Cards
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader("Student Companion Modules")
                Spacer(Modifier.height(10.dp))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columns = when {
                        maxWidth >= 840.dp -> 4
                        maxWidth >= 560.dp -> 3
                        else -> 2
                    }
                    val rows = moduleCards.chunked(columns)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows.forEach { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowItems.forEach { card ->
                                    ModuleGridCard(
                                        card = card,
                                        onClick = { onNavigate(card.route) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(columns - rowItems.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Developer Credit Footer
        item {
            Text(
                text = "STUDORA • Developed by Abid Ahasan",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SnapshotStatCard(
    title: String,
    value: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ModuleGridCard(
    card: DashboardModuleCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
        modifier = modifier
            .testTag(card.testTag)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(card.accent.copy(alpha = 0.14f))
            ) {
                Icon(
                    imageVector = card.icon,
                    contentDescription = card.title,
                    tint = card.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = card.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
