package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.DateTimeUtils
import com.example.data.db.PortalEntryEntity
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StudoraTopBar
import com.example.ui.theme.StudoraGold
import com.example.ui.viewmodel.MainViewModel
import java.io.File

private val PORTAL_CATEGORIES = listOf(
    "All", "Favorites", "Education", "University", "School & College",
    "Programming", "Productivity", "Communication", "Entertainment", "Cloud Storage", "Other"
)

@Composable
fun MyPortalScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenInAppBrowser: (String, String) -> Unit
) {
    val context = LocalContext.current
    val portals by viewModel.portalEntries.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var editingPortal by remember { mutableStateOf<PortalEntryEntity?>(null) }
    var showFormDialog by remember { mutableStateOf(false) }
    var portalToDelete by remember { mutableStateOf<PortalEntryEntity?>(null) }

    val filtered = remember(portals, searchQuery, selectedCategory) {
        portals.filter { entry ->
            val catMatch = when (selectedCategory) {
                "All" -> true
                "Favorites" -> entry.isFavorite
                else -> entry.category.equals(selectedCategory, ignoreCase = true)
            }
            val q = searchQuery.trim().lowercase()
            val textMatch = q.isBlank() ||
                entry.websiteName.lowercase().contains(q) ||
                entry.websiteUrl.lowercase().contains(q) ||
                entry.description.lowercase().contains(q)
            catMatch && textMatch
        }
    }

    val recentlyAdded = remember(portals) {
        portals.sortedByDescending { it.createdAt }.take(4)
    }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = "My Portal",
                subtitle = "${portals.size} saved websites • Offline directory",
                onBack = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingPortal = null
                    showFormDialog = true
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add Portal", maxLines = 1, softWrap = false) },
                modifier = Modifier.testTag("add_portal_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 90.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by website name, URL or description...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(PORTAL_CATEGORIES) { _, cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            if (recentlyAdded.isNotEmpty() && selectedCategory == "All" && searchQuery.isBlank()) {
                item {
                    Text("Recently Added", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(recentlyAdded) { _, item ->
                            AssistChip(
                                onClick = {
                                    viewModel.markPortalOpened(item)
                                    onOpenInAppBrowser(item.websiteUrl, item.websiteName)
                                },
                                label = { Text(item.websiteName) },
                                leadingIcon = { Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Public,
                        title = if (portals.isEmpty()) "No Portals Saved Yet" else "No Matching Portals",
                        subtitle = "Save university portals, Google Classroom, GitHub, W3Schools, or any valid HTTPS/HTTP website for fast access.",
                        actionLabel = "Add Portal",
                        onAction = {
                            editingPortal = null
                            showFormDialog = true
                        }
                    )
                }
            } else {
                itemsIndexed(filtered, key = { _, p -> p.id }) { idx, portal ->
                    PortalCard(
                        portal = portal,
                        canMoveUp = idx > 0 && selectedCategory == "All" && searchQuery.isBlank(),
                        canMoveDown = idx < filtered.lastIndex && selectedCategory == "All" && searchQuery.isBlank(),
                        onOpenInApp = {
                            viewModel.markPortalOpened(portal)
                            onOpenInAppBrowser(portal.websiteUrl, portal.websiteName)
                        },
                        onOpenExternal = {
                            viewModel.markPortalOpened(portal)
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(portal.websiteUrl)))
                            }.onFailure {
                                viewModel.notifyUser("No browser app found to open this link.")
                            }
                        },
                        onToggleFavorite = {
                            viewModel.savePortalEntry(portal.copy(isFavorite = !portal.isFavorite)) {}
                        },
                        onEdit = {
                            editingPortal = portal
                            showFormDialog = true
                        },
                        onDelete = { portalToDelete = portal },
                        onMoveUp = { viewModel.reorderPortals(filtered, idx, idx - 1) },
                        onMoveDown = { viewModel.reorderPortals(filtered, idx, idx + 1) }
                    )
                }
            }

            item {
                Text(
                    "Note: Opening live websites requires internet. External services require signing in directly on their pages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showFormDialog) {
        PortalFormDialog(
            initial = editingPortal,
            onDismiss = { showFormDialog = false },
            onSave = { entry, onError ->
                viewModel.savePortalEntry(entry) { res ->
                    res.onSuccess { showFormDialog = false }
                        .onFailure { onError(it.localizedMessage ?: "Invalid URL.") }
                }
            }
        )
    }

    portalToDelete?.let { target ->
        ConfirmationDialog(
            title = "Delete Portal",
            message = "Remove \"${target.websiteName}\" from My Portal?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                viewModel.deletePortalEntry(target)
                portalToDelete = null
            },
            onDismiss = { portalToDelete = null }
        )
    }
}

@Composable
private fun PortalCard(
    portal: PortalEntryEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpenInApp: () -> Unit,
    onOpenExternal: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenInApp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            val iconFile = portal.customIconPath?.let { File(it) }?.takeIf { it.exists() }
            if (iconFile != null) {
                AsyncImage(
                    model = iconFile,
                    contentDescription = portal.websiteName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = portal.websiteName.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        portal.websiteName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (portal.isFavorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Star, "Favorite", tint = StudoraGold, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    portal.websiteUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (portal.description.isNotBlank()) {
                    Text(
                        portal.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        portal.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    portal.lastOpenedAt?.let {
                        Text(
                            "• Opened ${DateTimeUtils.formatShortDate(it)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (portal.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Toggle favorite",
                    tint = if (portal.isFavorite) StudoraGold else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, "More options")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Open in App") },
                        leadingIcon = { Icon(Icons.Default.Web, null) },
                        onClick = { menuExpanded = false; onOpenInApp() }
                    )
                    DropdownMenuItem(
                        text = { Text("Open in Browser") },
                        leadingIcon = { Icon(Icons.Default.OpenInBrowser, null) },
                        onClick = { menuExpanded = false; onOpenExternal() }
                    )
                    if (canMoveUp) {
                        DropdownMenuItem(
                            text = { Text("Move Up") },
                            leadingIcon = { Icon(Icons.Default.ArrowUpward, null) },
                            onClick = { menuExpanded = false; onMoveUp() }
                        )
                    }
                    if (canMoveDown) {
                        DropdownMenuItem(
                            text = { Text("Move Down") },
                            leadingIcon = { Icon(Icons.Default.ArrowDownward, null) },
                            onClick = { menuExpanded = false; onMoveDown() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PortalFormDialog(
    initial: PortalEntryEntity?,
    onDismiss: () -> Unit,
    onSave: (PortalEntryEntity, (String) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(initial?.websiteName ?: "") }
    var url by remember { mutableStateOf(initial?.websiteUrl ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "Education") }
    var isFavorite by remember { mutableStateOf(initial?.isFavorite ?: false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val cats = PORTAL_CATEGORIES.filter { it != "All" && it != "Favorites" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Website Portal" else "Edit Portal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = null },
                    label = { Text("Website Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; errorMsg = null },
                    label = { Text("Website URL * (e.g. github.com)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(cats) { _, c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isFavorite, onCheckedChange = { isFavorite = it })
                    Text("Mark as Favorite")
                }

                if (errorMsg != null) {
                    Text(
                        errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                onClick = {
                    if (name.isBlank()) {
                        errorMsg = "Website Name is required."
                        return@Button
                    }
                    val entity = (initial ?: PortalEntryEntity(websiteName = "", websiteUrl = "")).copy(
                        websiteName = name.trim(),
                        websiteUrl = url.trim(),
                        description = description.trim(),
                        category = category,
                        isFavorite = isFavorite
                    )
                    onSave(entity) { err -> errorMsg = err }
                }
            ) {
                Text("Save", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("Cancel", maxLines = 1, softWrap = false) }
        }
    )
}
