package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface StudoraRoute

// ---- Bottom Navigation / Navigation Rail Top-Level Destinations ----
@Serializable
data object HomeRoute : StudoraRoute

@Serializable
data object MyNotesRoute : StudoraRoute

@Serializable
data object StudoraAiRoute : StudoraRoute

@Serializable
data object ProfileSettingsRoute : StudoraRoute

// ---- Dashboard Student Companion Modules ----
@Serializable
data object TasksRoute : StudoraRoute

@Serializable
data object BillsRoute : StudoraRoute

@Serializable
data object StudyRoutineRoute : StudoraRoute

@Serializable
data object WalletRoute : StudoraRoute

@Serializable
data object WalletCalculatorRoute : StudoraRoute

@Serializable
data object NutritionRoute : StudoraRoute

@Serializable
data object EmergencyContactsRoute : StudoraRoute

@Serializable
data object MyPortalRoute : StudoraRoute

@Serializable
data object ClassRoutineRoute : StudoraRoute

@Serializable
data object BusScheduleRoute : StudoraRoute

// ---- My Notes & Learning Tools Deep Flows ----
@Serializable
data class SubjectDetailRoute(val subjectId: Long) : StudoraRoute

@Serializable
data class TopicWorkspaceRoute(
    val subjectId: Long,
    val topicId: Long
) : StudoraRoute

@Serializable
data class WrittenNoteEditorRoute(
    val subjectId: Long,
    val topicId: Long = -1L,
    val noteId: Long = 0L,
    val docId: Long = -1L
) : StudoraRoute

@Serializable
data class AiNoteGeneratorRoute(
    val subjectId: Long = -1L,
    val topicId: Long = -1L,
    val docId: Long = -1L,
    val aiNoteId: Long = 0L
) : StudoraRoute

@Serializable
data class QuizRunnerRoute(val quizId: Long) : StudoraRoute

// ---- Hardware & Utility Viewers ----
@Serializable
data class PdfViewerRoute(
    val path: String,
    val title: String = "Document",
    val type: String = "PDF"
) : StudoraRoute

@Serializable
data class InAppBrowserRoute(
    val url: String,
    val title: String = "Browser"
) : StudoraRoute

@Serializable
data class CameraScannerRoute(
    val mode: String = "SCANNER", // SCANNER or PHOTO
    val target: String = "DOCUMENT", // DOCUMENT, ROUTINE, BUS, NUTRITION, TASK
    val subjectId: Long = -1L,
    val topicId: Long = -1L
) : StudoraRoute
