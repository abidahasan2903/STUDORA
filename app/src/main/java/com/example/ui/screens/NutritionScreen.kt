package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.DateTimeUtils
import com.example.data.db.FoodChartEntity
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StudoraTopBar
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.MainViewModel

@Composable
fun NutritionScreen(
    viewModel: MainViewModel,
    onOpenScanner: () -> Unit,
    onOpenViewer: (String, String, String) -> Unit,
    onBack: () -> Unit
) {
    val charts by viewModel.foodCharts.collectAsStateWithLifecycle()
    val suggestions by viewModel.nutritionSuggestions.collectAsStateWithLifecycle()
    val genState by viewModel.nutritionGenState.collectAsStateWithLifecycle()

    var analyzingChart by remember { mutableStateOf<FoodChartEntity?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = viewModel.repo.storage.getDisplayNameFromUri(uri).substringBeforeLast('.')
            viewModel.importFoodChartUri(uri, name)
        }
    }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = "Nutrition Guide",
                subtitle = "Food charts & general educational meal tips",
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
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Disclaimer: Nutrition Guide provides general educational information only and is not medical advice or a substitute for a qualified healthcare professional.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onOpenScanner,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DocumentScanner, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scan Chart", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import Chart", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (charts.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.RestaurantMenu,
                        title = "No Food Charts Saved",
                        subtitle = "Capture or import your hostel mess menu, meal plan, or food chart PDF/image to keep offline and get AI summaries."
                    )
                }
            } else {
                item { Text("Saved Food Charts (${charts.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(charts, key = { it.id }) { chart ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenViewer(chart.filePath, chart.title, chart.fileType) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                            Icon(Icons.Default.RestaurantMenu, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(chart.title, style = MaterialTheme.typography.titleSmall)
                                Text("${chart.fileType} • ${chart.pageCount} pg", style = MaterialTheme.typography.bodySmall)
                            }
                            FilledTonalButton(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                onClick = {
                                    viewModel.resetNutritionGenState()
                                    analyzingChart = chart
                                }
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("AI Tips", maxLines = 1, softWrap = false)
                            }
                            IconButton(onClick = { viewModel.deleteFoodChart(chart) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            if (suggestions.isNotEmpty()) {
                item { Text("Saved Nutrition Analyses (${suggestions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(suggestions, key = { it.id }) { s ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(s.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { viewModel.deleteNutritionSuggestion(s) }) {
                                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Text("Extracted from Chart:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(s.extractedInfo, style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider()
                            Text("AI Educational Suggestions:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            Text(s.aiSuggestions, style = MaterialTheme.typography.bodySmall)
                            Text("Saved ${DateTimeUtils.formatShortDate(s.createdAt)}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    analyzingChart?.let { chart ->
        var prompt by remember { mutableStateOf("Summarize the listed food items and organize a balanced student meal plan.") }
        val presets = listOf(
            "Explain this food chart in simple Bangla.",
            "Summarize the listed food items.",
            "Suggest a general student meal-planning structure."
        )
        AlertDialog(
            onDismissRequest = { if (genState !is AiTaskState.Loading) analyzingChart = null },
            title = { Text("Analyze ${chart.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(presets) { p -> AssistChip(onClick = { prompt = p }, label = { Text(p) }) }
                    }
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Your Request") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    when (val s = genState) {
                        is AiTaskState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        is AiTaskState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        is AiTaskState.Success -> {
                            LaunchedEffect(s) { analyzingChart = null }
                        }
                        else -> Unit
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = genState !is AiTaskState.Loading && prompt.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    onClick = { viewModel.analyzeFoodChart(chart, prompt) }
                ) { Text("Analyze", maxLines = 1, softWrap = false) }
            },
            dismissButton = {
                TextButton(
                    enabled = genState !is AiTaskState.Loading,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    onClick = { analyzingChart = null }
                ) { Text("Cancel", maxLines = 1, softWrap = false) }
            }
        )
    }
}
