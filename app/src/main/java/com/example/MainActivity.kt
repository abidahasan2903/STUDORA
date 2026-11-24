package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.configureAppCheck
import com.example.data.db.SubjectEntity
import com.example.ui.components.GlobalAudioPlayerBar
import com.example.ui.components.OfflineStatusBanner
import com.example.ui.navigation.*
import com.example.ui.screens.*
import com.example.ui.theme.StudoraTheme
import com.example.ui.theme.ThemePreference
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class TopLevelDestination(
    val route: StudoraRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(
        HomeRoute, "Home",
        Icons.Filled.Home, Icons.Outlined.Home, "nav_home"
    ),
    TopLevelDestination(
        MyNotesRoute, "My Notes",
        Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Filled.MenuBook, "nav_my_notes"
    ),
    TopLevelDestination(
        StudoraAiRoute, "Studora AI",
        Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_studora_ai"
    ),
    TopLevelDestination(
        ProfileSettingsRoute, "Profile",
        Icons.Filled.Person, Icons.Outlined.Person, "nav_profile"
    )
)

class MainActivity : ComponentActivity() {

    private var deepLinkModuleState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Firebase App Check before UI/ViewModels make any Gemini API calls
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        deepLinkModuleState = intent?.getStringExtra("navigate_module")

        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val settings by mainViewModel.settings.collectAsStateWithLifecycle()

            StudoraTheme(
                themePreference = ThemePreference.fromString(settings.themeMode)
            ) {
                StudoraRootApp(
                    viewModel = mainViewModel,
                    deepLinkModule = deepLinkModuleState,
                    onConsumeDeepLink = { deepLinkModuleState = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("navigate_module")?.let { module ->
            deepLinkModuleState = module
        }
    }
}

@Composable
private fun StudoraRootApp(
    viewModel: MainViewModel,
    deepLinkModule: String?,
    onConsumeDeepLink: () -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Shared transient state for photo returned from Camera to TasksScreen
    var capturedTaskPhotoPath by remember { mutableStateOf<String?>(null) }

    // Pending scanned PDF when launched from Home/MyNotes without a subject preselected
    var pendingQuickScanData by remember { mutableStateOf<Pair<List<File>, String>?>(null) }

    // Optional amount transferred from Wallet Calculator into a new Wallet transaction
    var pendingWalletAmount by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(deepLinkModule) {
        when (deepLinkModule) {
            "tasks" -> {
                navController.navigate(TasksRoute) { launchSingleTop = true }
                onConsumeDeepLink()
            }
            "bills" -> {
                navController.navigate(BillsRoute) { launchSingleTop = true }
                onConsumeDeepLink()
            }
        }
    }

    val isTopLevelTab = TOP_LEVEL_DESTINATIONS.any { dest ->
        currentDestination?.hasRoute(dest.route::class) == true
    }
    val isHomeRoute = currentDestination?.hasRoute<HomeRoute>() == true
    val hideGlobalChrome = currentDestination?.let { dest ->
        dest.hasRoute<CameraScannerRoute>() ||
            dest.hasRoute<PdfViewerRoute>() ||
            dest.hasRoute<InAppBrowserRoute>()
    } == true

    fun navigateToRoute(route: StudoraRoute) {
        val isTopLevel = TOP_LEVEL_DESTINATIONS.any { it.route::class == route::class }
        if (isTopLevel) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!hideGlobalChrome) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        GlobalAudioPlayerBar(
                            playbackState = playbackState,
                            onTogglePlayPause = { viewModel.toggleAudioPlayPause() },
                            onSeek = { viewModel.seekAudio(it) },
                            onCycleSpeed = { viewModel.setAudioSpeed(it) },
                            onClose = { viewModel.stopAudio() },
                            modifier = if (!isTopLevelTab || useNavigationRail) {
                                Modifier.navigationBarsPadding()
                            } else {
                                Modifier
                            }
                        )
                        if (isTopLevelTab && !useNavigationRail) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 4.dp
                            ) {
                                TOP_LEVEL_DESTINATIONS.forEach { tab ->
                                    val selected = currentDestination?.hasRoute(tab.route::class) == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateToRoute(tab.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = {
                                            Text(
                                                tab.label,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && isTopLevelTab && !hideGlobalChrome) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            Image(
                                painter = painterResource(R.drawable.studora_logo),
                                contentDescription = "STUDORA",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .padding(vertical = 12.dp)
                                    .height(42.dp)
                                    .aspectRatio(1067f / 1014f)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    ) {
                        Spacer(Modifier.height(8.dp))
                        TOP_LEVEL_DESTINATIONS.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToRoute(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        tab.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (!hideGlobalChrome) {
                        OfflineStatusBanner(
                            isOnline = isOnline,
                            modifier = if (isHomeRoute) Modifier.statusBarsPadding() else Modifier
                        )
                    }

                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute,
                        enterTransition = { fadeIn(animationSpec = tween(200)) },
                        exitTransition = { fadeOut(animationSpec = tween(180)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                        popExitTransition = { fadeOut(animationSpec = tween(180)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        // 1. Top-Level Navigation Tabs
                        composable<HomeRoute> {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigate = { route -> navigateToRoute(route) }
                            )
                        }

                        composable<MyNotesRoute> {
                            MyNotesScreen(
                                viewModel = viewModel,
                                onBack = null,
                                onOpenSubject = { subjectId ->
                                    navController.navigate(SubjectDetailRoute(subjectId))
                                },
                                onOpenAiNote = { aiNoteId ->
                                    navController.navigate(AiNoteGeneratorRoute(aiNoteId = aiNoteId))
                                },
                                onOpenQuiz = { quizId ->
                                    navController.navigate(QuizRunnerRoute(quizId))
                                },
                                onOpenScanner = {
                                    navController.navigate(CameraScannerRoute("SCANNER", "DOCUMENT"))
                                }
                            )
                        }

                        composable<StudoraAiRoute> {
                            StudoraAiScreen(
                                viewModel = viewModel,
                                onBack = null
                            )
                        }

                        composable<ProfileSettingsRoute> {
                            ProfileSettingsScreen(
                                viewModel = viewModel,
                                onBack = null
                            )
                        }

                        // 2. Dashboard Modules
                        composable<TasksRoute> {
                            TasksScreen(
                                viewModel = viewModel,
                                capturedTaskPhotoPath = capturedTaskPhotoPath,
                                onConsumeCapturedPhoto = { capturedTaskPhotoPath = null },
                                onOpenCameraForTask = {
                                    navController.navigate(CameraScannerRoute("PHOTO", "TASK"))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<BillsRoute> {
                            BillsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<StudyRoutineRoute> {
                            StudyRoutineScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<WalletRoute> {
                            WalletScreen(
                                viewModel = viewModel,
                                onOpenCalculator = { navController.navigate(WalletCalculatorRoute) },
                                prefilledAmount = pendingWalletAmount,
                                onConsumePrefilledAmount = { pendingWalletAmount = null },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<WalletCalculatorRoute> {
                            WalletCalculatorScreen(
                                viewModel = viewModel,
                                onUseResultInTransaction = { amount ->
                                    pendingWalletAmount = amount
                                    navController.popBackStack()
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<NutritionRoute> {
                            NutritionScreen(
                                viewModel = viewModel,
                                onOpenScanner = {
                                    navController.navigate(CameraScannerRoute("SCANNER", "NUTRITION"))
                                },
                                onOpenViewer = { path, title, type ->
                                    navController.navigate(PdfViewerRoute(path, title, type))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<EmergencyContactsRoute> {
                            EmergencyContactsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<MyPortalRoute> {
                            MyPortalScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenInAppBrowser = { url, title ->
                                    navController.navigate(InAppBrowserRoute(url, title))
                                }
                            )
                        }

                        composable<ClassRoutineRoute> {
                            ClassRoutineScreen(
                                viewModel = viewModel,
                                onOpenScanner = {
                                    navController.navigate(CameraScannerRoute("SCANNER", "ROUTINE"))
                                },
                                onOpenViewer = { path, title, type ->
                                    navController.navigate(PdfViewerRoute(path, title, type))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<BusScheduleRoute> {
                            BusScheduleScreen(
                                viewModel = viewModel,
                                onOpenScanner = {
                                    navController.navigate(CameraScannerRoute("SCANNER", "BUS"))
                                },
                                onOpenViewer = { path, title, type ->
                                    navController.navigate(PdfViewerRoute(path, title, type))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 3. My Notes Deep Flows
                        composable<SubjectDetailRoute> { entry ->
                            val args = entry.toRoute<SubjectDetailRoute>()
                            SubjectDetailScreen(
                                subjectId = args.subjectId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenTopic = { sId, tId ->
                                    navController.navigate(TopicWorkspaceRoute(sId, tId))
                                },
                                onOpenPdfViewer = { path, title, type ->
                                    navController.navigate(PdfViewerRoute(path, title, type))
                                },
                                onOpenScanner = { sId, tId ->
                                    navController.navigate(
                                        CameraScannerRoute("SCANNER", "DOCUMENT", sId, tId ?: -1L)
                                    )
                                },
                                onOpenWrittenNoteEditor = { sId, tId, nId, dId ->
                                    navController.navigate(
                                        WrittenNoteEditorRoute(sId, tId ?: -1L, nId, dId ?: -1L)
                                    )
                                },
                                onOpenAiNoteGenerator = { sId, tId, dId, aiId ->
                                    navController.navigate(
                                        AiNoteGeneratorRoute(sId ?: -1L, tId ?: -1L, dId ?: -1L, aiId)
                                    )
                                },
                                onOpenQuizRunner = { qId ->
                                    navController.navigate(QuizRunnerRoute(qId))
                                }
                            )
                        }

                        composable<TopicWorkspaceRoute> { entry ->
                            val args = entry.toRoute<TopicWorkspaceRoute>()
                            TopicWorkspaceScreen(
                                subjectId = args.subjectId,
                                topicId = args.topicId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenPdfViewer = { path, title, type ->
                                    navController.navigate(PdfViewerRoute(path, title, type))
                                },
                                onOpenScanner = { sId, tId ->
                                    navController.navigate(CameraScannerRoute("SCANNER", "DOCUMENT", sId, tId))
                                },
                                onOpenWrittenNoteEditor = { sId, tId, nId, dId ->
                                    navController.navigate(
                                        WrittenNoteEditorRoute(sId, tId, nId, dId ?: -1L)
                                    )
                                },
                                onOpenAiNoteGenerator = { sId, tId, dId, aiId ->
                                    navController.navigate(
                                        AiNoteGeneratorRoute(sId, tId, dId ?: -1L, aiId)
                                    )
                                },
                                onOpenQuizRunner = { qId ->
                                    navController.navigate(QuizRunnerRoute(qId))
                                }
                            )
                        }

                        composable<WrittenNoteEditorRoute> { entry ->
                            val args = entry.toRoute<WrittenNoteEditorRoute>()
                            WrittenNoteEditorScreen(
                                subjectId = args.subjectId,
                                topicId = args.topicId.takeIf { it > 0L },
                                noteId = args.noteId,
                                docId = args.docId.takeIf { it > 0L },
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<AiNoteGeneratorRoute> { entry ->
                            val args = entry.toRoute<AiNoteGeneratorRoute>()
                            AiNoteGeneratorScreen(
                                subjectId = args.subjectId.takeIf { it > 0L },
                                topicId = args.topicId.takeIf { it > 0L },
                                docId = args.docId.takeIf { it > 0L },
                                aiNoteId = args.aiNoteId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<QuizRunnerRoute> { entry ->
                            val args = entry.toRoute<QuizRunnerRoute>()
                            QuizRunnerScreen(
                                quizId = args.quizId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 4. Hardware & Utility Viewers
                        composable<PdfViewerRoute> { entry ->
                            val args = entry.toRoute<PdfViewerRoute>()
                            PdfViewerScreen(
                                filePath = args.path,
                                title = args.title,
                                fileType = args.type,
                                storage = viewModel.repo.storage,
                                onBack = { navController.popBackStack() },
                                onMessage = { viewModel.notifyUser(it) }
                            )
                        }

                        composable<InAppBrowserRoute> { entry ->
                            val args = entry.toRoute<InAppBrowserRoute>()
                            InAppBrowserScreen(
                                url = args.url,
                                title = args.title,
                                onClose = { navController.popBackStack() }
                            )
                        }

                        composable<CameraScannerRoute> { entry ->
                            val args = entry.toRoute<CameraScannerRoute>()
                            val defaultTitle = when (args.target) {
                                "ROUTINE" -> "Class Routine"
                                "BUS" -> "Bus Schedule"
                                "NUTRITION" -> "Food Chart"
                                else -> "Scanned Document"
                            }

                            CameraScannerScreen(
                                mode = args.mode,
                                storage = viewModel.repo.storage,
                                defaultTitle = defaultTitle,
                                onCancel = { navController.popBackStack() },
                                onPhotoConfirmed = { tempFile ->
                                    if (args.target == "TASK") {
                                        scope.launch {
                                            val savedPath = withContext(Dispatchers.IO) {
                                                val dest = viewModel.repo.storage.createNewFile(
                                                    viewModel.repo.storage.tasksDir,
                                                    "task_photo",
                                                    "jpg"
                                                )
                                                tempFile.copyTo(dest, overwrite = true)
                                                tempFile.delete()
                                                dest.absolutePath
                                            }
                                            capturedTaskPhotoPath = savedPath
                                            navController.popBackStack()
                                        }
                                    } else {
                                        navController.popBackStack()
                                    }
                                },
                                onPdfPagesConfirmed = { pages, title ->
                                    when (args.target) {
                                        "ROUTINE" -> {
                                            viewModel.saveScannedRoutinePdf(pages, title)
                                            navController.popBackStack()
                                        }
                                        "BUS" -> {
                                            viewModel.saveScannedBusPdf(pages, title)
                                            navController.popBackStack()
                                        }
                                        "NUTRITION" -> {
                                            viewModel.saveScannedFoodChartPdf(pages, title)
                                            navController.popBackStack()
                                        }
                                        else -> {
                                            if (args.subjectId > 0L) {
                                                viewModel.saveScannedPdf(
                                                    pageFiles = pages,
                                                    title = title,
                                                    subjectId = args.subjectId,
                                                    topicId = args.topicId.takeIf { it > 0L }
                                                ) {
                                                    navController.popBackStack()
                                                }
                                            } else {
                                                pendingQuickScanData = pages to title
                                                navController.popBackStack()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingQuickScanData?.let { (pages, title) ->
        ScanSubjectPickerDialog(
            docTitle = title,
            subjects = subjects,
            onSelectSubject = { sId ->
                pendingQuickScanData = null
                viewModel.saveScannedPdf(pages, title, sId, null) { res ->
                    if (res.isSuccess) {
                        navController.navigate(SubjectDetailRoute(sId))
                    }
                }
            },
            onCreateAndSelect = { newName ->
                pendingQuickScanData = null
                viewModel.saveSubject(SubjectEntity(name = newName)) { newId ->
                    viewModel.saveScannedPdf(pages, title, newId, null) { res ->
                        if (res.isSuccess) {
                            navController.navigate(SubjectDetailRoute(newId))
                        }
                    }
                }
            },
            onDismiss = {
                pendingQuickScanData = null
                pages.forEach { it.delete() }
            }
        )
    }
}

@Composable
private fun ScanSubjectPickerDialog(
    docTitle: String,
    subjects: List<SubjectEntity>,
    onSelectSubject: (Long) -> Unit,
    onCreateAndSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newSubjectName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save \"$docTitle\" To Subject") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Choose which subject folder should store this scanned PDF offline:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (subjects.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        items(subjects, key = { it.id }) { sub ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSubject(sub.id) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(sub.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        if (sub.code.isNotBlank()) {
                                            Text(sub.code, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = newSubjectName,
                    onValueChange = { newSubjectName = it },
                    label = { Text("Or create new Subject") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = newSubjectName.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                onClick = { onCreateAndSelect(newSubjectName.trim()) }
            ) {
                Text("Create & Save", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("Discard", maxLines = 1, softWrap = false) }
        }
    )
}
