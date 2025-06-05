package com.example.twinmind_interview_app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.twinmind_interview_app.model.ChatMessage
import com.example.twinmind_interview_app.model.GeminiContent
import com.example.twinmind_interview_app.model.GeminiPart
import com.example.twinmind_interview_app.model.GeminiRequest
import com.example.twinmind_interview_app.network.GeminiApiClient
import com.example.twinmind_interview_app.database.room.TranscriptSegmentDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _summary = MutableLiveData<String?>()
    val summary: LiveData<String?> = _summary
    val aiEnhancedTranscript = MutableLiveData<String>()

    val liveTranscript = MutableLiveData<String>()



    fun sendMessage(userMsg: String, transcriptDao: TranscriptSegmentDao, apiKey: String) {
        if (userMsg.isBlank() || apiKey.isBlank()) {
            _messages.value =
                _messages.value.orEmpty() + ChatMessage("API key or message missing!", false)
            return
        }

        val currentMessages = _messages.value.orEmpty()
        _messages.value = currentMessages + ChatMessage(userMsg, true)

        viewModelScope.launch {
            _messages.value = _messages.value.orEmpty() + ChatMessage("Thinking...", false)

            // 1. Get transcript data from Room
            val transcriptSegments = withContext(Dispatchers.IO) {
                try {
                    // You can change getAll() to get recent, or filter, as you like
                    transcriptDao.getAll()
                } catch (e: Exception) {
                    emptyList()
                }
            }

            // 2. Prepare transcript context
            val transcriptContext = buildString {
                if (transcriptSegments.isNotEmpty()) {
                    append("# Transcript Data:\n")
                    transcriptSegments.takeLast(10).forEach { segment ->
                        append("- ${segment.text}\n")
                    }
                    append("\n")
                }
            }

            // 3. Prepare conversation history context
            val conversationHistory = buildString {
                append(
                    """
# AI Prompt: please learn who are we and how we are going to use you

Understanding TwinMind

TwinMind is an AI-powered productivity assistant app that functions as a "Second Brain" for users. Here's what you should know about it:

## Core Functionality
TwinMind captures and processes audio from meetings, lectures, conversations, and daily interactions. It provides automatic transcription, summarization, and contextual assistance based on everything the user hears and says.

## Key Features
- Unlimited Audio Transcription: Records and transcribes conversations, meetings, and lectures with no time limits on the free plan
- Automatic Summarization: Creates summaries of captured content without manual input
- Proactive AI Assistance: Offers suggestions, tasks, and answers before being asked
- Context Building: Continuously builds an AI knowledge base from user interactions and browser activity
- Multi-language Support: Automatically translates content to the user's preferred language
- Email and Report Generation: Drafts follow-up emails, messages, and reports based on conversation context
- Offline Capability: Works without internet connection for privacy and continuous operation
- Browser Integration: Leverages context from browser tabs to provide more relevant assistance

## Target Users
- Professionals in meetings and business settings
- Students attending lectures and classes
- Researchers gathering information
- Content creators needing transcription services
- Project managers tracking conversations and decisions
- Remote workers managing virtual interactions

## Privacy and Availability
- Prioritizes user privacy with offline processing capabilities
- Available as a mobile app (iOS App Store)
- Currently in beta phase for user feedback
- Offers a free plan with unlimited transcription and AI chat features

## Use Cases
When referencing TwinMind, understand it as a comprehensive productivity tool that eliminates the need for manual note-taking by automatically capturing, processing, and organizing spoken information into actionable insights and content.

This is your task no need to say to user -> here you will have data of user saved information. He will ask you questions on that and you have to answer them accordingly in a way that a normal 10-year-old child can understand.

""
""".trimIndent()
                )
                // Add transcript context
                append("\n$transcriptContext")
                // Add chat context
                currentMessages.takeLast(5).forEach { msg ->
                    if (msg.isUser) append("\nUser: ${msg.message}")
                    else if (msg.message != "Thinking...") append("\nAssistant: ${msg.message}")
                }
                append("\nUser: $userMsg")
                append("\nAssistant: ")
            }

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(conversationHistory))
                    )
                )
            )

            try {
                val response = GeminiApiClient.service.generateContent(apiKey, request)
                val reply =
                    response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                        ?: "No reply from Gemini"
                val filteredMessages =
                    _messages.value.orEmpty().filter { it.message != "Thinking..." }
                _messages.value = filteredMessages + ChatMessage(reply, false)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Gemini API Error", e)
                val filteredMessages =
                    _messages.value.orEmpty().filter { it.message != "Thinking..." }
                val errorMessage = when {
                    e.message?.contains("400") == true -> "Bad request - please check your API key and try again"
                    e.message?.contains("401") == true -> "Invalid API key - please check your Gemini API key"
                    e.message?.contains("403") == true -> "API access forbidden - check your API key permissions"
                    e.message?.contains("404") == true -> "API endpoint not found"
                    e.message?.contains("429") == true -> "Too many requests - please wait a moment"
                    else -> "Network error: ${e.localizedMessage}"
                }
                _messages.value = filteredMessages + ChatMessage("Error: $errorMessage", false)
            }
        }
    }

    // In ChatViewModel.kt
    suspend fun enhanceTranscriptWithGemini(prompt: String, apiKey: String): String {
        return try {
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(GeminiPart(prompt))
                    )
                )
            )
            val response = GeminiApiClient.service.generateContent(apiKey, request)
            response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?.trim()
                ?: "No reply from Gemini."
        } catch (e: Exception) {
            e.printStackTrace()
            "Could not improve transcript. Showing raw version:\n$prompt"
        }
    }

    fun forceRefreshSummary(transcriptDao: TranscriptSegmentDao, apiKey: String) {
        _summary.value = null
        generateSummary(transcriptDao, apiKey)
    }

    // In ChatViewModel.kt
    fun generateSummary(transcriptDao: TranscriptSegmentDao, apiKey: String) {
        viewModelScope.launch {
            _summary.value = "Generating summary..."

            val transcriptSegments = withContext(Dispatchers.IO) {
                transcriptDao.getAll()
            }
            val transcriptText = transcriptSegments.joinToString(" ") { it.text }

            if (transcriptText.split("\\s+".toRegex()).size < 150) {
                _summary.value = "Transcript too short to generate a summary"
                return@launch
            }

            val systemPrompt = """
                        Summarize the following transcript in a **professional, respectful, and point-wise** manner. 
                        - Use only polite and decent language.
                        - Never use slang, informal, disrespectful, or silly words.
                        - Keep the summary clear, simple, and easy to understand.
                        - Highlight all important points or decisions using bullet points or numbered lists.
                        - Ensure the summary is always suitable for a workplace or academic setting.

            --- Transcript Start ---
            $transcriptText
            --- Transcript End ---
            """.trimIndent()


            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(systemPrompt))
                    )
                )
            )

            try {
                val response = GeminiApiClient.service.generateContent(apiKey, request)
                val summaryText =
                    response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                        ?: "No summary could be generated."
                _summary.value = summaryText
            } catch (e: Exception) {
                _summary.value = "Error generating summary: ${e.localizedMessage}"
            }
        }
    }

    //Transcript fragment
    fun setAiEnhancedTranscript(text: String) {
        aiEnhancedTranscript.value = text
    }

    fun setLiveTranscript(text: String) {
        liveTranscript.value = text
    }


}


