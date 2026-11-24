package com.example

import com.example.core.AiService
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiServiceFallbackTest {

    @Test
    fun generationActionsReturnFriendlyFailureWithoutFirebaseConfiguration() = runTest {
        val ai = AiService(isConfigured = false)

        val quiz = ai.generateQuiz(null, null, "TEXT", "MCQ", 5, "Easy")
        val cards = ai.generateFlashcards(null, null, "TEXT", 5)
        val plan = ai.generateStudyPlan(
            subjects = "Math",
            academicLevel = "College",
            examInfo = "",
            dailyMinutes = 60,
            preferredHours = "Evening",
            goals = "Revise algebra",
            difficultSubjects = "Math",
            breakPreference = "Short breaks",
            weeklyAvailability = "Weekdays"
        )
        val nutrition = ai.analyzeFoodChart(File("missing-chart.jpg"), "IMAGE", "Suggest general meal ideas")

        listOf(quiz, cards, plan, nutrition).forEach { result ->
            assertEquals(
                AiService.AI_NOT_CONFIGURED_MESSAGE,
                result.exceptionOrNull()?.message
            )
        }
    }

    @Test
    fun chatFlowReportsFriendlyFailureWithoutFirebaseConfiguration() = runTest {
        val ai = AiService(isConfigured = false)
        val failure = runCatching {
            ai.streamChatMessage(emptyList(), "Explain fractions").toList()
        }.exceptionOrNull()

        assertEquals(AiService.AI_NOT_CONFIGURED_MESSAGE, failure?.message)
    }

    @Test
    fun documentNoteFlowReportsFriendlyFailureWithoutFirebaseConfiguration() = runTest {
        val ai = AiService(isConfigured = false)
        val failure = runCatching {
            ai.streamNotesFromDocument(
                documentFile = null,
                fileType = "TEXT",
                userPrompt = "Summarize",
                fallbackTextContext = "Local notes"
            ) {}.toList()
        }.exceptionOrNull()

        assertEquals(AiService.AI_NOT_CONFIGURED_MESSAGE, failure?.message)
    }
}
