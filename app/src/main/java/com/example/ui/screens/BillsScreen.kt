package com.example.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.DateTimeUtils
import com.example.data.db.BillEntity
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StudoraTopBar
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseAccent
import com.example.ui.viewmodel.MainViewModel
import java.util.Calendar

@Composable
fun BillsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val bills by viewModel.allBills.collectAsStateWithLifecycle()
    var filterTab by remember { mutableStateOf("Upcoming") }
    var searchQuery by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<BillEntity?>(null) }

    val todayMid = DateTimeUtils.todayMidnight()
    val pendingTotal = remember(bills) { bills.filter { it.status == "PENDING" }.sumOf { it.amount } }

    val filtered = remember(bills, filterTab, searchQuery) {
        val q = searchQuery.trim().lowercase()
        bills.filter { b ->
            val statusMatch = when (filterTab) {
                "Upcoming" -> b.status == "PENDING" && DateTimeUtils.startOfDayMillis(b.dueDateMillis) >= todayMid
                "Overdue" -> b.status == "PENDING" && DateTimeUtils.startOfDayMillis(b.dueDateMillis) < todayMid
                "Paid" -> b.status == "PAID"
                else -> true
            }
            val textMatch = q.isBlank() || b.title.lowercase().contains(q) || b.description.lowercase().contains(q)
            statusMatch && textMatch
        }
    }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = "Bill Payment Alert",
                subtitle = "Pending Total: ${DateTimeUtils.formatCurrency(pendingTotal)}",
                onBack = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingBill = null; showForm = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add Bill", maxLines = 1, softWrap = false) }
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
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Column {
                            Text("Total Pending Bills", style = MaterialTheme.typography.labelLarge)
                            Text(
                                DateTimeUtils.formatCurrency(pendingTotal),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(Icons.Default.ReceiptLong, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search bills...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Upcoming", "Overdue", "Paid", "All").forEach { tab ->
                        FilterChip(selected = filterTab == tab, onClick = { filterTab = tab }, label = { Text(tab) })
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.ReceiptLong,
                        title = "No $filterTab Bills",
                        subtitle = "Add tuition fees, hostel rent, Wi-Fi, or utility bills with automatic advance reminder notifications.",
                        actionLabel = "Add Bill",
                        onAction = { editingBill = null; showForm = true }
                    )
                }
            } else {
                items(filtered, key = { it.id }) { bill ->
                    val isOverdue = bill.status == "PENDING" && DateTimeUtils.startOfDayMillis(bill.dueDateMillis) < todayMid
                    val isPaid = bill.status == "PAID"
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingBill = bill; showForm = true }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bill.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Due: ${DateTimeUtils.formatDate(bill.dueDateMillis)} • Alert: ${if (bill.reminderIntervalDays == 0) "Custom" else "${bill.reminderIntervalDays}d before"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isOverdue) CrimsonError else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (bill.description.isNotBlank()) {
                                    Text(bill.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    DateTimeUtils.formatCurrency(bill.amount),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isPaid) EmeraldSuccess else RoseAccent
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { viewModel.toggleBillPaid(bill) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(if (isPaid) "Reopen" else "Mark Paid", maxLines = 1, softWrap = false)
                                    }
                                    IconButton(onClick = { viewModel.deleteBill(bill) }) {
                                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        BillFormDialog(
            initial = editingBill,
            viewModel = viewModel,
            onDismiss = { showForm = false },
            onSave = { bill ->
                viewModel.saveBill(bill) { showForm = false }
            }
        )
    }
}

@Composable
private fun BillFormDialog(
    initial: BillEntity?,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (BillEntity) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amountStr by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var dueDateMillis by remember { mutableLongStateOf(initial?.dueDateMillis ?: DateTimeUtils.addDays(DateTimeUtils.todayMidnight(), 3)) }
    var reminderHour by remember { mutableIntStateOf(initial?.reminderHour ?: 9) }
    var reminderMinute by remember { mutableIntStateOf(initial?.reminderMinute ?: 0) }
    var intervalDays by remember { mutableIntStateOf(initial?.reminderIntervalDays ?: 1) }
    var customMillis by remember { mutableStateOf(initial?.customReminderMillis) }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var err by remember { mutableStateOf<String?>(null) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Bill Alert" else "Edit Bill") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it; err = null },
                        label = { Text("Bill Title *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it; err = null },
                        label = { Text("Amount *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = dueDateMillis }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        dueDateMillis = Calendar.getInstance().apply {
                                            set(y, m, d, 0, 0, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "Due: ${DateTimeUtils.formatShortDate(dueDateMillis)}",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, h, m -> reminderHour = h; reminderMinute = m },
                                    reminderHour,
                                    reminderMinute,
                                    false
                                ).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                DateTimeUtils.formatHourMinute(reminderHour, reminderMinute),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                item {
                    Text("Reminder Interval", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(1 to "1d before", 2 to "2d before", 3 to "3d before", 7 to "7d before", 0 to "Custom")) { (days, lbl) ->
                            FilterChip(
                                selected = intervalDays == days,
                                onClick = {
                                    intervalDays = days
                                    if (days == 0) {
                                        val cal = Calendar.getInstance()
                                        DatePickerDialog(
                                            context,
                                            { _, y, m, d ->
                                                customMillis = Calendar.getInstance().apply {
                                                    set(y, m, d, reminderHour, reminderMinute, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                }.timeInMillis
                                            },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                                },
                                label = { Text(lbl) }
                            )
                        }
                    }
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
                if (err != null) {
                    item { Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            Button(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (title.isBlank() || amt == null || amt <= 0.0) {
                        err = "Enter a valid bill title and positive amount."
                        return@Button
                    }
                    onSave(
                        (initial ?: BillEntity(title = "", amount = amt, dueDateMillis = dueDateMillis)).copy(
                            title = title.trim(),
                            amount = amt,
                            dueDateMillis = dueDateMillis,
                            reminderHour = reminderHour,
                            reminderMinute = reminderMinute,
                            reminderIntervalDays = intervalDays,
                            customReminderMillis = customMillis,
                            description = description.trim()
                        )
                    )
                }
            ) { Text("Save Bill", maxLines = 1, softWrap = false) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("Cancel", maxLines = 1, softWrap = false) }
        }
    )
}
