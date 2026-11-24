package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.CalculatorEngine
import com.example.core.CalculatorHistoryItem
import com.example.core.CalculatorUiState
import com.example.ui.components.StudoraTopBar
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.StudoraGold
import com.example.ui.theme.StudoraNavy
import com.example.ui.viewmodel.MainViewModel
import java.math.BigDecimal

@Composable
fun WalletCalculatorScreen(
    viewModel: MainViewModel,
    onUseResultInTransaction: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.calculatorState.collectAsStateWithLifecycle()

    // Extract positive numeric value to optionally transfer into a new Wallet transaction
    val transferableAmount = remember(state.currentInput, state.resultDisplay, state.lastResult, state.errorMessage) {
        if (state.errorMessage != null) {
            null
        } else {
            val candidate = state.lastResult?.takeIf { state.justEvaluated }
                ?: state.resultDisplay.takeIf { it.isNotBlank() }?.let {
                    runCatching { BigDecimal(it) }.getOrNull()
                }
                ?: runCatching { BigDecimal(state.currentInput.removeSuffix("%")) }.getOrNull()

            candidate?.takeIf { it.compareTo(BigDecimal.ZERO) > 0 }?.let {
                CalculatorEngine.formatDecimal(it)
            }
        }
    }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = "Wallet Calculator",
                subtitle = "Independent math • Does not alter wallet balance",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWide = maxWidth >= 720.dp
            if (isWide) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1.15f)
                    ) {
                        CalculatorDisplayCard(state = state)
                        CalculatorKeypad(
                            canUseInTransaction = transferableAmount != null,
                            transferableAmount = transferableAmount,
                            onDigit = viewModel::onCalcDigit,
                            onDecimal = viewModel::onCalcDecimal,
                            onOperator = viewModel::onCalcOperator,
                            onPercent = viewModel::onCalcPercent,
                            onToggleSign = viewModel::onCalcToggleSign,
                            onBackspace = viewModel::onCalcBackspace,
                            onClearEntry = viewModel::onCalcClearEntry,
                            onClearAll = viewModel::onCalcClearAll,
                            onEquals = viewModel::onCalcEquals,
                            onUseInTransaction = {
                                transferableAmount?.let(onUseResultInTransaction)
                            }
                        )
                    }
                    CalculatorHistoryPanel(
                        history = state.history,
                        onReuse = viewModel::onCalcReuseHistory,
                        onClearHistory = viewModel::onCalcClearHistory,
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight()
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        CalculatorDisplayCard(state = state)
                    }
                    item {
                        CalculatorKeypad(
                            canUseInTransaction = transferableAmount != null,
                            transferableAmount = transferableAmount,
                            onDigit = viewModel::onCalcDigit,
                            onDecimal = viewModel::onCalcDecimal,
                            onOperator = viewModel::onCalcOperator,
                            onPercent = viewModel::onCalcPercent,
                            onToggleSign = viewModel::onCalcToggleSign,
                            onBackspace = viewModel::onCalcBackspace,
                            onClearEntry = viewModel::onCalcClearEntry,
                            onClearAll = viewModel::onCalcClearAll,
                            onEquals = viewModel::onCalcEquals,
                            onUseInTransaction = {
                                transferableAmount?.let(onUseResultInTransaction)
                            }
                        )
                    }
                    item {
                        CalculatorHistoryHeader(
                            hasHistory = state.history.isNotEmpty(),
                            onClearHistory = viewModel::onCalcClearHistory
                        )
                    }
                    if (state.history.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "No calculations yet. Tap any past calculation here later to reuse it.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(state.history, key = { it.id }) { entry ->
                            CalculatorHistoryRow(
                                item = entry,
                                onClick = { viewModel.onCalcReuseHistory(entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculatorDisplayCard(state: CalculatorUiState) {
    val exprScroll = rememberScrollState()
    val inputScroll = rememberScrollState()

    LaunchedEffect(state.expression) {
        exprScroll.scrollTo(exprScroll.maxValue)
    }
    LaunchedEffect(state.currentInput) {
        inputScroll.scrollTo(inputScroll.maxValue)
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = StudoraNavy,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Calculate,
                        contentDescription = null,
                        tint = StudoraGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Calculator",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    text = "Standalone tool",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudoraGold.copy(alpha = 0.9f),
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(Modifier.height(10.dp))

            // Calculation / Expression display
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(exprScroll)
            ) {
                Text(
                    text = state.expression.ifBlank { " " },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    modifier = Modifier.testTag("calc_expression")
                )
            }

            Spacer(Modifier.height(6.dp))

            // Current Input display
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(inputScroll)
            ) {
                val inputFontSize = when {
                    state.currentInput.length > 14 -> 26.sp
                    state.currentInput.length > 10 -> 32.sp
                    else -> 38.sp
                }
                Text(
                    text = state.currentInput,
                    fontSize = inputFontSize,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    modifier = Modifier.testTag("calc_current_input")
                )
            }

            Spacer(Modifier.height(6.dp))

            // Result / Error display
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFCA5A5),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("calc_result")
                )
            } else {
                val shownResult = when {
                    state.justEvaluated && state.resultDisplay.isNotBlank() -> "= ${state.resultDisplay}"
                    state.resultDisplay.isNotBlank() -> "= ${state.resultDisplay}"
                    else -> " "
                }
                Text(
                    text = shownResult,
                    style = MaterialTheme.typography.titleMedium,
                    color = StudoraGold,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("calc_result")
                )
            }
        }
    }
}

@Composable
private fun CalculatorKeypad(
    canUseInTransaction: Boolean,
    transferableAmount: String?,
    onDigit: (String) -> Unit,
    onDecimal: () -> Unit,
    onOperator: (String) -> Unit,
    onPercent: () -> Unit,
    onToggleSign: () -> Unit,
    onBackspace: () -> Unit,
    onClearEntry: () -> Unit,
    onClearAll: () -> Unit,
    onEquals: () -> Unit,
    onUseInTransaction: () -> Unit
) {
    val gap = 8.dp
    Column(
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Row 1: AC, CE, %, Backspace
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
            CalcKeyButton(
                label = "AC",
                containerColor = CrimsonError.copy(alpha = 0.14f),
                contentColor = CrimsonError,
                onClick = onClearAll,
                modifier = Modifier
                    .weight(1f)
                    .testTag("calc_btn_ac")
            )
            CalcKeyButton(
                label = "CE",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClick = onClearEntry,
                modifier = Modifier.weight(1f)
            )
            CalcKeyButton(
                label = "%",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onPercent,
                modifier = Modifier.weight(1f)
            )
            Surface(
                onClick = onBackspace,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace"
                    )
                }
            }
        }

        // Row 2: 7, 8, 9, ÷
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
            CalcKeyButton("7", onClick = { onDigit("7") }, modifier = Modifier.weight(1f))
            CalcKeyButton("8", onClick = { onDigit("8") }, modifier = Modifier.weight(1f))
            CalcKeyButton("9", onClick = { onDigit("9") }, modifier = Modifier.weight(1f))
            CalcKeyButton(
                label = "÷",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                onClick = { onOperator("÷") },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 3: 4, 5, 6, ×
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
            CalcKeyButton("4", onClick = { onDigit("4") }, modifier = Modifier.weight(1f))
            CalcKeyButton("5", onClick = { onDigit("5") }, modifier = Modifier.weight(1f))
            CalcKeyButton("6", onClick = { onDigit("6") }, modifier = Modifier.weight(1f))
            CalcKeyButton(
                label = "×",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                onClick = { onOperator("×") },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 4: 1, 2, 3, −
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
            CalcKeyButton("1", onClick = { onDigit("1") }, modifier = Modifier.weight(1f))
            CalcKeyButton("2", onClick = { onDigit("2") }, modifier = Modifier.weight(1f))
            CalcKeyButton("3", onClick = { onDigit("3") }, modifier = Modifier.weight(1f))
            CalcKeyButton(
                label = "−",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                onClick = { onOperator("−") },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 5: ±, 0, ., +
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
            CalcKeyButton(
                label = "±",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClick = onToggleSign,
                modifier = Modifier.weight(1f)
            )
            CalcKeyButton("0", onClick = { onDigit("0") }, modifier = Modifier.weight(1f))
            CalcKeyButton(".", onClick = onDecimal, modifier = Modifier.weight(1f))
            CalcKeyButton(
                label = "+",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                onClick = { onOperator("+") },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 6: Optional "Use in Transaction" + "="
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                enabled = canUseInTransaction,
                onClick = onUseInTransaction,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(2.2f)
                    .height(54.dp)
                    .testTag("calc_use_in_transaction")
            ) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (transferableAmount != null) "Use $transferableAmount in Entry" else "Use in Transaction",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onEquals,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudoraGold,
                    contentColor = StudoraNavy
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("calc_btn_equals")
            ) {
                Text(
                    text = "=",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun CalcKeyButton(
    label: String,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
        border = if (containerColor == MaterialTheme.colorScheme.surface) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        } else null,
        modifier = modifier.height(54.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun CalculatorHistoryHeader(
    hasHistory: Boolean,
    onClearHistory: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Calculation History",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        if (hasHistory) {
            TextButton(
                onClick = onClearHistory,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("calc_clear_history")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear", maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun CalculatorHistoryPanel(
    history: List<CalculatorHistoryItem>,
    onReuse: (CalculatorHistoryItem) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            CalculatorHistoryHeader(
                hasHistory = history.isNotEmpty(),
                onClearHistory = onClearHistory
            )
            Spacer(Modifier.height(8.dp))
            if (history.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "No calculations yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history, key = { it.id }) { item ->
                        CalculatorHistoryRow(item = item, onClick = { onReuse(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculatorHistoryRow(
    item: CalculatorHistoryItem,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.expression,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "= ${item.result}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Reuse",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
