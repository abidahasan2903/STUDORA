package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.core.DateTimeUtils
import com.example.core.LocalStorageManager
import com.example.core.PdfUtils
import com.example.core.formatFileSize
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StudoraTopBar
import java.io.File

@Composable
fun PdfViewerScreen(
    filePath: String,
    title: String,
    fileType: String,
    storage: LocalStorageManager,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val file = remember(filePath) { File(filePath) }
    var pageCount by remember { mutableIntStateOf(1) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showInfoDialog by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = newScale
        offset = if (newScale > 1f) offset + panChange else Offset.Zero
    }

    LaunchedEffect(filePath, fileType) {
        if (!file.exists()) {
            errorMsg = "Document file was not found on local storage."
            isLoading = false
            return@LaunchedEffect
        }
        if (fileType == "PDF") {
            PdfUtils.getPdfPageCount(file).onSuccess { count ->
                pageCount = count.coerceAtLeast(1)
            }.onFailure { err ->
                errorMsg = err.localizedMessage ?: "Invalid PDF file."
                isLoading = false
            }
        } else {
            pageCount = 1
        }
    }

    LaunchedEffect(filePath, currentPage, fileType) {
        if (!file.exists() || errorMsg != null) return@LaunchedEffect
        isLoading = true
        val res = if (fileType == "PDF") {
            PdfUtils.renderPdfPageToBitmap(file, currentPage, targetWidthPx = 1440)
        } else {
            PdfUtils.loadOrientedBitmap(file, maxDimension = 2000)
        }
        res.onSuccess { bmp ->
            pageBitmap = bmp
            scale = 1f
            offset = Offset.Zero
        }.onFailure { err ->
            errorMsg = err.localizedMessage ?: "Unable to render document."
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            StudoraTopBar(
                title = title,
                subtitle = if (errorMsg == null && fileType == "PDF") "Page ${currentPage + 1} of $pageCount" else fileType,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, "File info")
                    }
                    IconButton(onClick = {
                        val mime = if (fileType == "PDF") "application/pdf" else "image/*"
                        storage.shareFile(file, mime, "Share $title")
                            .onFailure { onMessage(it.localizedMessage ?: "Share failed.") }
                    }) {
                        Icon(Icons.Default.Share, "Share document")
                    }
                }
            )
        },
        bottomBar = {
            if (errorMsg == null) {
                Surface(
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    scale = (scale - 0.5f).coerceAtLeast(1f)
                                    if (scale == 1f) offset = Offset.Zero
                                }
                            ) {
                                Icon(Icons.Default.ZoomOut, "Zoom out")
                            }
                            Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            IconButton(onClick = { scale = (scale + 0.5f).coerceAtMost(4f) }) {
                                Icon(Icons.Default.ZoomIn, "Zoom in")
                            }
                            IconButton(onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            }) {
                                Icon(Icons.Default.FitScreen, "Fit to screen")
                            }
                        }

                        if (fileType == "PDF" && pageCount > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    enabled = currentPage > 0,
                                    onClick = { currentPage-- }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous page")
                                }
                                Text("${currentPage + 1}/$pageCount", style = MaterialTheme.typography.labelLarge)
                                IconButton(
                                    enabled = currentPage < pageCount - 1,
                                    onClick = { currentPage++ }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next page")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            when {
                errorMsg != null -> {
                    EmptyStateCard(
                        icon = Icons.Default.BrokenImage,
                        title = "Cannot Open Document",
                        subtitle = errorMsg!!,
                        actionLabel = "Go Back",
                        onAction = onBack,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                isLoading && pageBitmap == null -> {
                    CircularProgressIndicator()
                }
                pageBitmap != null -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(state = transformState)
                    ) {
                        Image(
                            bitmap = pageBitmap!!.asImageBitmap(),
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        )
                    }
                }
            }
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Document Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Title: $title")
                    Text("Type: $fileType")
                    Text("Pages: $pageCount")
                    if (file.exists()) {
                        Text("Size: ${formatFileSize(file.length())}")
                        Text("Saved: ${DateTimeUtils.formatDateTime(file.lastModified())}")
                    }
                    Text("Stored locally for offline access.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    onClick = {
                        val mime = if (fileType == "PDF") "application/pdf" else "image/*"
                        storage.openFileExternally(file, mime)
                            .onFailure { onMessage("No external viewer found on device.") }
                        showInfoDialog = false
                    }
                ) {
                    Text("Open External", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    onClick = { showInfoDialog = false }
                ) { Text("Close", maxLines = 1, softWrap = false) }
            }
        )
    }
}
