package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.LocalStorageManager
import com.example.core.NormalizedCropRect
import com.example.core.PdfUtils
import com.example.core.ScanFilter
import com.example.ui.components.PermissionRationaleCard
import com.example.ui.components.openAppSettings
import com.example.ui.theme.StudoraGoldBright
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot

data class ScannedPageItem(
    val rawFile: File,
    val processedFile: File,
    val cropRect: NormalizedCropRect,
    val filter: ScanFilter
)

private enum class ScannerStage {
    CAMERA,
    CROP_AND_ENHANCE,
    PAGE_REVIEW
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScannerScreen(
    mode: String, // "SCANNER" or "PHOTO"
    storage: LocalStorageManager,
    defaultTitle: String = "Scanned Document",
    onCancel: () -> Unit,
    onPhotoConfirmed: (File) -> Unit,
    onPdfPagesConfirmed: (List<File>, String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var useFrontCamera by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf(ScannerStage.CAMERA) }
    var isCapturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // CameraX handles
    var cameraControlRef by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }

    // Multi-page state
    val scannedPages = remember { mutableStateListOf<ScannedPageItem>() }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var activeRawFile by remember { mutableStateOf<File?>(null) }
    var activeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeCrop by remember { mutableStateOf(NormalizedCropRect()) }
    var activeFilter by remember { mutableStateOf(if (mode == "SCANNER") ScanFilter.ENHANCED else ScanFilter.ORIGINAL) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var documentTitle by remember { mutableStateOf(defaultTitle) }
    var isProcessing by remember { mutableStateOf(false) }

    // Clean up temporary capture files when cancelled
    BackHandler {
        if (stage != ScannerStage.CAMERA) {
            stage = if (scannedPages.isNotEmpty() && stage == ScannerStage.CROP_AND_ENHANCE) {
                ScannerStage.PAGE_REVIEW
            } else {
                ScannerStage.CAMERA
            }
        } else {
            scannedPages.forEach {
                it.rawFile.delete()
                it.processedFile.delete()
            }
            onCancel()
        }
    }

    // Recompute enhanced preview whenever filter changes
    LaunchedEffect(activeBitmap, activeFilter, activeCrop, stage) {
        val raw = activeBitmap
        if (raw != null && stage == ScannerStage.CROP_AND_ENHANCE) {
            previewBitmap = withContext(Dispatchers.Default) {
                PdfUtils.applyCropAndFilter(raw, activeCrop, activeFilter)
            }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (stage) {
                            ScannerStage.CAMERA -> if (mode == "SCANNER") "Document Scanner" else "Camera Capture"
                            ScannerStage.CROP_AND_ENHANCE -> "Crop & Enhance"
                            ScannerStage.PAGE_REVIEW -> "Review Pages (${scannedPages.size})"
                        },
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (stage == ScannerStage.CROP_AND_ENHANCE && scannedPages.isNotEmpty()) {
                            stage = ScannerStage.PAGE_REVIEW
                        } else if (stage == ScannerStage.PAGE_REVIEW) {
                            stage = ScannerStage.CAMERA
                        } else {
                            scannedPages.forEach {
                                it.rawFile.delete()
                                it.processedFile.delete()
                            }
                            onCancel()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A111E))
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!hasCameraPermission) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp)
                ) {
                    PermissionRationaleCard(
                        icon = Icons.Default.CameraAlt,
                        title = "Camera Permission Required",
                        explanation = "STUDORA uses your phone's physical camera to capture study documents, routines, bus schedules, food charts, and task photos locally.",
                        primaryLabel = "Grant Camera",
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = { openAppSettings(context) }
                    )
                }
                return@Box
            }

            when (stage) {
                ScannerStage.CAMERA -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                            },
                            update = { previewView ->
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }
                                        val capture = ImageCapture.Builder()
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                            .build()
                                        imageCaptureRef = capture
                                        val selector = if (useFrontCamera) {
                                            CameraSelector.DEFAULT_FRONT_CAMERA
                                        } else {
                                            CameraSelector.DEFAULT_BACK_CAMERA
                                        }
                                        cameraProvider.unbindAll()
                                        val cam = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            selector,
                                            preview,
                                            capture
                                        )
                                        cameraControlRef = cam
                                        if (cam.cameraInfo.hasFlashUnit()) {
                                            cam.cameraControl.enableTorch(torchEnabled && !useFrontCamera)
                                        }
                                        cameraError = null
                                    } catch (e: Exception) {
                                        Log.e("CameraScanner", "Camera binding failed", e)
                                        cameraError = "Camera unavailable: ${e.localizedMessage}"
                                    }
                                }, ContextCompat.getMainExecutor(context))
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Subtle document framing guide in scanner mode
                        if (mode == "SCANNER") {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val padX = size.width * 0.08f
                                val padY = size.height * 0.12f
                                drawRect(
                                    color = Color.White.copy(alpha = 0.45f),
                                    topLeft = Offset(padX, padY),
                                    size = Size(size.width - padX * 2, size.height - padY * 2),
                                    style = Stroke(width = 3f)
                                )
                            }
                        }

                        if (cameraError != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = cameraError!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Top right camera controls (Flash & Flip)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        ) {
                            if (!useFrontCamera) {
                                FilledIconButton(
                                    onClick = {
                                        torchEnabled = !torchEnabled
                                        cameraControlRef?.let {
                                            if (it.cameraInfo.hasFlashUnit()) {
                                                it.cameraControl.enableTorch(torchEnabled)
                                            }
                                        }
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = if (torchEnabled) StudoraGoldBright else Color.Black.copy(alpha = 0.55f)
                                    )
                                ) {
                                    Icon(
                                        if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                        contentDescription = "Toggle flash",
                                        tint = if (torchEnabled) Color.Black else Color.White
                                    )
                                }
                            }
                            FilledIconButton(
                                onClick = {
                                    useFrontCamera = !useFrontCamera
                                    if (useFrontCamera) torchEnabled = false
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.55f)
                                )
                            ) {
                                Icon(Icons.Default.Cameraswitch, "Switch camera", tint = Color.White)
                            }
                        }

                        // Bottom capture bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.65f))
                                .navigationBarsPadding()
                                .padding(vertical = 20.dp, horizontal = 16.dp)
                        ) {
                            if (mode == "SCANNER" && scannedPages.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { stage = ScannerStage.PAGE_REVIEW },
                                    border = BorderStroke(1.dp, Color.White),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Text("Review (${scannedPages.size})", maxLines = 1, softWrap = false)
                                }
                            } else {
                                Spacer(Modifier.width(88.dp))
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .border(4.dp, Color.White, CircleShape)
                                    .padding(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isCapturing) Color.Gray else Color.White)
                                    .testTag("camera_shutter_button")
                                    .clickable(enabled = !isCapturing) {
                                        val capture = imageCaptureRef ?: return@clickable
                                        isCapturing = true
                                        val outFile = File(storage.tempDir, "cap_${System.currentTimeMillis()}.jpg")
                                        val outputOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
                                        capture.takePicture(
                                            outputOptions,
                                            ContextCompat.getMainExecutor(context),
                                            object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                    scope.launch {
                                                        val bmp = PdfUtils.loadOrientedBitmap(outFile, 1800)
                                                        isCapturing = false
                                                        bmp.onSuccess { loaded ->
                                                            activeRawFile = outFile
                                                            activeBitmap = loaded
                                                            activeCrop = if (mode == "SCANNER") {
                                                                PdfUtils.suggestDocumentBounds(loaded)
                                                            } else {
                                                                NormalizedCropRect(0f, 0f, 1f, 1f)
                                                            }
                                                            activeFilter = if (mode == "SCANNER") ScanFilter.ENHANCED else ScanFilter.ORIGINAL
                                                            editingIndex = -1
                                                            stage = ScannerStage.CROP_AND_ENHANCE
                                                        }.onFailure { err ->
                                                            outFile.delete()
                                                            cameraError = err.localizedMessage
                                                        }
                                                    }
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    isCapturing = false
                                                    outFile.delete()
                                                    cameraError = "Capture failed: ${exception.message}"
                                                }
                                            }
                                        )
                                    }
                            ) {
                                if (isCapturing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(26.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }

                            Spacer(Modifier.width(88.dp))
                        }
                    }
                }

                ScannerStage.CROP_AND_ENHANCE -> {
                    val bmp = activeBitmap
                    if (bmp == null) {
                        stage = ScannerStage.CAMERA
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0A111E))
                        ) {
                            // Interactive Crop & Live Filter Preview Area
                            var showProcessed by remember { mutableStateOf(false) }
                            val displayBmp = if (showProcessed && previewBitmap != null) previewBitmap!! else bmp
                            var containerSize by remember { mutableStateOf(IntSize.Zero) }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .onSizeChanged { containerSize = it }
                            ) {
                                Image(
                                    bitmap = displayBmp.asImageBitmap(),
                                    contentDescription = "Captured page",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Corner crop handles when adjusting crop bounds
                                if (!showProcessed && containerSize.width > 0 && containerSize.height > 0) {
                                    val imgAspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
                                    val boxAspect = containerSize.width.toFloat() / containerSize.height.coerceAtLeast(1)
                                    val drawW: Float
                                    val drawH: Float
                                    if (imgAspect > boxAspect) {
                                        drawW = containerSize.width.toFloat()
                                        drawH = drawW / imgAspect
                                    } else {
                                        drawH = containerSize.height.toFloat()
                                        drawW = drawH * imgAspect
                                    }
                                    val offsetX = (containerSize.width - drawW) / 2f
                                    val offsetY = (containerSize.height - drawH) / 2f

                                    var activeCorner by remember { mutableIntStateOf(-1) }
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(drawW, drawH) {
                                                detectDragGestures(
                                                    onDragStart = { start ->
                                                        val l = offsetX + activeCrop.left * drawW
                                                        val t = offsetY + activeCrop.top * drawH
                                                        val r = offsetX + activeCrop.right * drawW
                                                        val b = offsetY + activeCrop.bottom * drawH
                                                        val corners = listOf(
                                                            Offset(l, t),
                                                            Offset(r, t),
                                                            Offset(l, b),
                                                            Offset(r, b)
                                                        )
                                                        activeCorner = corners.indices.minByOrNull { idx ->
                                                            hypot(start.x - corners[idx].x, start.y - corners[idx].y)
                                                        } ?: -1
                                                    },
                                                    onDragEnd = { activeCorner = -1 },
                                                    onDrag = { change, drag ->
                                                        change.consume()
                                                        val dx = drag.x / drawW
                                                        val dy = drag.y / drawH
                                                        activeCrop = when (activeCorner) {
                                                            0 -> activeCrop.copy(
                                                                left = (activeCrop.left + dx).coerceIn(0f, activeCrop.right - 0.15f),
                                                                top = (activeCrop.top + dy).coerceIn(0f, activeCrop.bottom - 0.15f)
                                                            )
                                                            1 -> activeCrop.copy(
                                                                right = (activeCrop.right + dx).coerceIn(activeCrop.left + 0.15f, 1f),
                                                                top = (activeCrop.top + dy).coerceIn(0f, activeCrop.bottom - 0.15f)
                                                            )
                                                            2 -> activeCrop.copy(
                                                                left = (activeCrop.left + dx).coerceIn(0f, activeCrop.right - 0.15f),
                                                                bottom = (activeCrop.bottom + dy).coerceIn(activeCrop.top + 0.15f, 1f)
                                                            )
                                                            3 -> activeCrop.copy(
                                                                right = (activeCrop.right + dx).coerceIn(activeCrop.left + 0.15f, 1f),
                                                                bottom = (activeCrop.bottom + dy).coerceIn(activeCrop.top + 0.15f, 1f)
                                                            )
                                                            else -> activeCrop
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        val l = offsetX + activeCrop.left * drawW
                                        val t = offsetY + activeCrop.top * drawH
                                        val r = offsetX + activeCrop.right * drawW
                                        val b = offsetY + activeCrop.bottom * drawH
                                        drawRect(
                                            color = StudoraGoldBright,
                                            topLeft = Offset(l, t),
                                            size = Size(r - l, b - t),
                                            style = Stroke(width = 4f)
                                        )
                                        listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach { pt ->
                                            drawCircle(color = StudoraGoldBright, radius = 16f, center = pt)
                                            drawCircle(color = Color.White, radius = 8f, center = pt)
                                        }
                                    }
                                }
                            }

                            // Filter Chips & Preview Toggle
                            Surface(
                                color = Color(0xFF131D31),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "Drag gold corners to crop",
                                            color = Color.White.copy(alpha = 0.8f),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        FilterChip(
                                            selected = showProcessed,
                                            onClick = { showProcessed = !showProcessed },
                                            label = { Text(if (showProcessed) "Adjust Crop" else "Preview Enhanced") }
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        itemsIndexed(ScanFilter.entries) { _, filter ->
                                            FilterChip(
                                                selected = activeFilter == filter,
                                                onClick = {
                                                    activeFilter = filter
                                                    showProcessed = true
                                                },
                                                label = { Text(filter.label) }
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(14.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                activeRawFile?.delete()
                                                stage = ScannerStage.CAMERA
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                        ) {
                                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Retake", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                        }

                                        Button(
                                            enabled = !isProcessing,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                            onClick = {
                                                scope.launch {
                                                    isProcessing = true
                                                    val outFile = withContext(Dispatchers.IO) {
                                                        val finalBmp = PdfUtils.applyCropAndFilter(bmp, activeCrop, activeFilter)
                                                        val dest = File(storage.tempDir, "proc_${System.currentTimeMillis()}.jpg")
                                                        FileOutputStream(dest).use {
                                                            finalBmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
                                                        }
                                                        finalBmp.recycle()
                                                        dest
                                                    }
                                                    isProcessing = false
                                                    if (mode == "PHOTO") {
                                                        activeRawFile?.delete()
                                                        onPhotoConfirmed(outFile)
                                                    } else {
                                                        val rawF = activeRawFile ?: outFile
                                                        val item = ScannedPageItem(rawF, outFile, activeCrop, activeFilter)
                                                        if (editingIndex in scannedPages.indices) {
                                                            scannedPages[editingIndex].processedFile.delete()
                                                            scannedPages[editingIndex] = item
                                                        } else {
                                                            scannedPages.add(item)
                                                        }
                                                        stage = ScannerStage.PAGE_REVIEW
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                if (mode == "PHOTO") "Use Photo" else "Keep Page",
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ScannerStage.PAGE_REVIEW -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = documentTitle,
                            onValueChange = { documentTitle = it },
                            label = { Text("PDF Document Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        if (scannedPages.isEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                Text("No pages scanned yet. Tap 'Add Page' to capture.")
                            }
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                itemsIndexed(scannedPages) { index, page ->
                                    var thumb by remember(page.processedFile) { mutableStateOf<Bitmap?>(null) }
                                    LaunchedEffect(page.processedFile) {
                                        thumb = PdfUtils.loadOrientedBitmap(page.processedFile, 700).getOrNull()
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .width(220.dp)
                                            .fillMaxHeight()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    "Page ${index + 1}",
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val removed = scannedPages.removeAt(index)
                                                        removed.rawFile.delete()
                                                        removed.processedFile.delete()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, "Delete page", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color.Black.copy(alpha = 0.06f))
                                                    .clickable {
                                                        scope.launch {
                                                            activeRawFile = page.rawFile
                                                            activeBitmap = PdfUtils.loadOrientedBitmap(page.rawFile, 1800).getOrNull()
                                                            activeCrop = page.cropRect
                                                            activeFilter = page.filter
                                                            editingIndex = index
                                                            stage = ScannerStage.CROP_AND_ENHANCE
                                                        }
                                                    }
                                            ) {
                                                thumb?.let {
                                                    Image(
                                                        bitmap = it.asImageBitmap(),
                                                        contentDescription = "Page ${index + 1}",
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                IconButton(
                                                    enabled = index > 0,
                                                    onClick = {
                                                        val item = scannedPages.removeAt(index)
                                                        scannedPages.add(index - 1, item)
                                                    }
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Move left")
                                                }
                                                TextButton(
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                    onClick = {
                                                        scope.launch {
                                                            activeRawFile = page.rawFile
                                                            activeBitmap = PdfUtils.loadOrientedBitmap(page.rawFile, 1800).getOrNull()
                                                            activeCrop = page.cropRect
                                                            activeFilter = page.filter
                                                            editingIndex = index
                                                            stage = ScannerStage.CROP_AND_ENHANCE
                                                        }
                                                    }
                                                ) {
                                                    Text("Edit", maxLines = 1, softWrap = false)
                                                }
                                                IconButton(
                                                    enabled = index < scannedPages.lastIndex,
                                                    onClick = {
                                                        val item = scannedPages.removeAt(index)
                                                        scannedPages.add(index + 1, item)
                                                    }
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Move right")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { stage = ScannerStage.CAMERA },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Page", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                            Button(
                                enabled = scannedPages.isNotEmpty() && !isProcessing,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                onClick = {
                                    val files = scannedPages.map { it.processedFile }
                                    onPdfPagesConfirmed(files, documentTitle.ifBlank { defaultTitle })
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save PDF", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
