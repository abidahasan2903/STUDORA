package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.DateTimeUtils
import com.example.data.db.WalletTransactionEntity
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StudoraTopBar
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.StudoraGold
import com.example.ui.theme.StudoraNavy
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

private val EXPENSE_CATS = listOf("Food", "Transport", "Books & Print", "Tuition", "Rent", "Internet", "Health", "Other")
private val INCOME_CATS = listOf("Allowance", "Scholarship", "Tuition/Tutoring", "Part-Time", "Gift", "Other")

@Composable
fun WalletScreen(
    viewModel: MainViewModel,
    onOpenCalculator: () -> Unit,
    prefilledAmount: String? = null,
    onConsumePrefilledAmount: () -> Unit = {},
    onBack: () -> Unit
) {
    val transactions by viewModel.walletTransactions.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedPeriod by remember { mutableStateOf("All Time") } // Today, This Month, All Time
    var showForm by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<WalletTransactionEntity?>(null) }
    var initialPrefillAmount by remember { mutableStateOf("") }

    LaunchedEffect(prefilledAmount) {
        if (!prefilledAmount.isNullOrBlank()) {
            editingTx = null
            initialPrefillAmount = prefilledAmount
            showForm = true
            onConsumePrefilledAmount()
        }
    }

    val nowCal = Calendar.getInstance()
    val todayMid = DateTimeUtils.todayMidnight()

    val totalIncome = remember(transactions) { transactions.filter { it.type == "INCOME" }.sumOf { it.amount } }
    val totalExpense = remember(transactions) { transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount } }
    val balance = totalIncome - totalExpense

    val todayExpense = remember(transactions) {
        transactions.filter { it.type == "EXPENSE" && DateTimeUtils.startOfDayMillis(it.transactionDateMillis) == todayMid }
            .sumOf { it.amount }
    }
    val thisMonthExpense = remember(transactions) {
        transactions.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.transactionDateMillis }
            it.type == "EXPENSE" &&
                c.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                c.get(Calendar.MONTH) == nowCal.get(Calendar.MONTH)
        }.sumOf { it.amount }
    }

    val filtered = remember(transactions, searchQuery, selectedCategory, selectedPeriod) {
        val q = searchQuery.trim().lowercase()
        transactions.filter { tx ->
            val catOk = selectedCategory == "All" || tx.category.equals(selectedCategory, ignoreCase = true)
            val periodOk = when (selectedPeriod) {
                "Today" -> DateTimeUtils.startOfDayMillis(tx.transactionDateMillis) == todayMid
                "This Month" -> {
                    val c = Calendar.getInstance().apply { timeInMillis = tx.transactionDateMillis }
                    c.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) && c.get(Calendar.MONTH) == nowCal.get(Calendar.MONTH)
                }
                else -> true
            }
            val qOk = q.isBlank() || tx.category.lowercase().contains(q) ||
                tx.description.lowercase().contains(q) || tx.note.lowercase().contains(q)
            catOk && periodOk && qOk
        }
    }

    // Monthly spending chart data (last 6 months from real SQLite records)
    val monthlyBars = remember(transactions) {
        val fmt = SimpleDateFormat("MMM", Locale.getDefault())
        (5 downTo 0).map { offset ->
            val c = Calendar.getInstance().apply { add(Calendar.MONTH, -offset) }
            val y = c.get(Calendar.YEAR)
            val m = c.get(Calendar.MONTH)
            val sum = transactions.filter {
                val tc = Calendar.getInstance().apply { timeInMillis = it.transactionDateMillis }
                it.type == "EXPENSE" && tc.get(Calendar.YEAR) == y && tc.get(Calendar.MONTH) == m
            }.sumOf { it.amount }
            fmt.format(c.time) to sum
        }
    }

    val categoryBreakdown = remember(transactions) {
        transactions.filter { it.type == "EXPENSE" }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
    }

    val allCats = remember(transactions) {
        listOf("All") + (EXPENSE_CATS + INCOME_CATS + transactions.map { it.category }).distinct()
    }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = "My Wallet",
                subtitle = "Offline income & expense tracker",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = onOpenCalculator,
                        modifier = Modifier.testTag("wallet_topbar_calculator")
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = "Open Calculator")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingTx = null; initialPrefillAmount = ""; showForm = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add Entry", maxLines = 1, softWrap = false) }
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
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = StudoraNavy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Current Balance", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.78f))
                        Text(
                            DateTimeUtils.formatCurrency(balance),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Total Income", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                                Text(DateTimeUtils.formatCurrency(totalIncome), style = MaterialTheme.typography.titleMedium, color = EmeraldSuccess)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Total Expenses", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                                Text(DateTimeUtils.formatCurrency(totalExpense), style = MaterialTheme.typography.titleMedium, color = Color(0xFFFCA5A5))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Today Spent: ${DateTimeUtils.formatCurrency(todayExpense)} • This Month: ${DateTimeUtils.formatCurrency(thisMonthExpense)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Wallet Calculator Feature Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wallet_calculator_card")
                        .clickable(onClick = onOpenCalculator)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StudoraGold.copy(alpha = 0.16f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = "Calculator",
                                    tint = StudoraGold
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Calculator",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Calculate expenses, split bills & reuse amounts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = onOpenCalculator,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Open", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            // Monthly Spending Chart
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Monthly Spending (Last 6 Months)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        val maxVal = (monthlyBars.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
                        val barColor = MaterialTheme.colorScheme.primary
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        ) {
                            val slotW = size.width / monthlyBars.size
                            val barW = slotW * 0.48f
                            monthlyBars.forEachIndexed { idx, (_, value) ->
                                val ratio = (value / maxVal).toFloat().coerceIn(0f, 1f)
                                val h = (size.height - 12f) * ratio
                                val x = idx * slotW + (slotW - barW) / 2f
                                drawRoundRect(
                                    color = if (value > 0) barColor else barColor.copy(alpha = 0.14f),
                                    topLeft = Offset(x, size.height - h.coerceAtLeast(6f)),
                                    size = Size(barW, h.coerceAtLeast(6f)),
                                    cornerRadius = CornerRadius(8f, 8f)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
                            monthlyBars.forEach { (month, _) ->
                                Text(month, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            if (categoryBreakdown.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                            Text("Category-wise Spending", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            categoryBreakdown.take(5).forEach { (cat, sum) ->
                                val share = if (totalExpense > 0) (sum / totalExpense).toFloat().coerceIn(0f, 1f) else 0f
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(cat, style = MaterialTheme.typography.bodySmall)
                                    Text(DateTimeUtils.formatCurrency(sum), style = MaterialTheme.typography.labelSmall)
                                }
                                LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search transactions...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All Time", "Today", "This Month").forEach { p ->
                        FilterChip(selected = selectedPeriod == p, onClick = { selectedPeriod = p }, label = { Text(p) })
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allCats) { c ->
                        FilterChip(selected = selectedCategory == c, onClick = { selectedCategory = c }, label = { Text(c) })
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = "No Transactions Yet",
                        subtitle = "Record your allowance, scholarships, food, transport, or tuition expenses to track real offline balances.",
                        actionLabel = "Add Transaction",
                        onAction = { editingTx = null; initialPrefillAmount = ""; showForm = true }
                    )
                }
            } else {
                items(filtered, key = { it.id }) { tx ->
                    val isIncome = tx.type == "INCOME"
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingTx = tx; showForm = true }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                            Icon(
                                if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                null,
                                tint = if (isIncome) EmeraldSuccess else CrimsonError
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tx.category, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    listOf(tx.description, DateTimeUtils.formatShortDate(tx.transactionDateMillis))
                                        .filter { it.isNotBlank() }.joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                (if (isIncome) "+" else "-") + DateTimeUtils.formatCurrency(tx.amount),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isIncome) EmeraldSuccess else CrimsonError
                            )
                            IconButton(onClick = { viewModel.deleteWalletTransaction(tx) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        WalletFormDialog(
            initial = editingTx,
            prefilledAmount = initialPrefillAmount,
            onDismiss = { showForm = false; initialPrefillAmount = "" },
            onSave = { tx ->
                viewModel.saveWalletTransaction(tx)
                showForm = false
                initialPrefillAmount = ""
            }
        )
    }
}

@Composable
private fun WalletFormDialog(
    initial: WalletTransactionEntity?,
    prefilledAmount: String = "",
    onDismiss: () -> Unit,
    onSave: (WalletTransactionEntity) -> Unit
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(initial?.type ?: "EXPENSE") }
    var amountStr by remember { mutableStateOf(initial?.amount?.toString() ?: prefilledAmount) }
    var category by remember { mutableStateOf(initial?.category ?: "Food") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var dateMillis by remember { mutableLongStateOf(initial?.transactionDateMillis ?: System.currentTimeMillis()) }
    var err by remember { mutableStateOf<String?>(null) }

    val cats = if (type == "INCOME") INCOME_CATS else EXPENSE_CATS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Transaction" else "Edit Transaction") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = type == "EXPENSE",
                            onClick = { type = "EXPENSE"; category = EXPENSE_CATS.first() },
                            label = { Text("Expense") }
                        )
                        FilterChip(
                            selected = type == "INCOME",
                            onClick = { type = "INCOME"; category = INCOME_CATS.first() },
                            label = { Text("Income") }
                        )
                    }
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(cats) { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    dateMillis = Calendar.getInstance().apply { set(y, m, d, 12, 0) }.timeInMillis
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
                            "Date: ${DateTimeUtils.formatDate(dateMillis)}",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (optional)") },
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
                    if (amt == null || amt <= 0.0) { err = "Enter a valid positive amount."; return@Button }
                    onSave(
                        (initial ?: WalletTransactionEntity(type = type, amount = amt, category = category)).copy(
                            type = type,
                            amount = amt,
                            category = category,
                            description = description.trim(),
                            note = note.trim(),
                            transactionDateMillis = dateMillis
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
