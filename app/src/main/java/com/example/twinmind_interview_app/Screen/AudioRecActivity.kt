package com.example.twinmind_interview_app.Screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.twinmind_interview_app.adapter.ChatAdapter
import com.example.twinmind_interview_app.adapter.removeThinkingBubbleIfPresent
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.Utils.navigateHandlers
import com.example.twinmind_interview_app.adapter.TranscriptAdapter
import com.example.twinmind_interview_app.databinding.ActivityAudioRecBinding
import com.example.twinmind_interview_app.model.ChatMessage
import com.example.twinmind_interview_app.model.GeminiContent
import com.example.twinmind_interview_app.model.GeminiPart
import com.example.twinmind_interview_app.model.GeminiRequest
import com.example.twinmind_interview_app.model.TranscriptSegmentEntity
import com.example.twinmind_interview_app.network.GeminiApiClient
import com.example.twinmind_interview_app.repository.TranscriptDatabase
import com.example.twinmind_interview_app.repository.TranscriptSegmentDao
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.*
import java.util.*

class AudioRecActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioRecBinding
    private lateinit var navigation: navigateHandlers
    private lateinit var transcriptDao: TranscriptSegmentDao

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false
    private var timer: CountDownTimer? = null
    private var secondsElapsed = 0

    private var transcriptText: String = ""
    private var transcriptTime: String = "00:00"
    private var continueListening = false
    private var currentTab = 1

    // For segmenting
    private var segmentStartTime = 0
    private var currentSegmentText = ""

    // Adapter for transcript RecyclerView
    private var transcriptAdapter: TranscriptAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioRecBinding.inflate(layoutInflater)
        setContentView(binding.root)
        navigation = navigateHandlers()

        transcriptDao = TranscriptDatabase.getDatabase(applicationContext).transcriptDao()

        setupTabs()
        setupClickListeners()
        selectTab(1) // Default to Notes tab

        if (checkAudioPermission()) {
            prepareSpeechRecognizer()
            startRecording()
        }

        binding.btnTranscript.setOnClickListener {
            showTranscriptBottomSheet()
        }
    }

    private fun showTranscriptBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_transcript, null)
        bottomSheetDialog.setContentView(view)
        val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT

        val closeBtn = view.findViewById<ImageView>(R.id.closeBtn)
        closeBtn.setOnClickListener { bottomSheetDialog.dismiss() }

        // ---- BEGIN CHAT SETUP ----
        val rvChat = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvChat)
        val etMessage = view.findViewById<EditText>(R.id.etMessage)
        val btnSend = view.findViewById<ImageView>(R.id.btnSend)
        val bottomSheetTranscript = view.findViewById<TextView>(R.id.bottomSheetTranscript)
        bottomSheetTranscript.visibility = View.GONE // Hide info text

        val chatMessages = mutableListOf<ChatMessage>()
        val chatAdapter = ChatAdapter(chatMessages)
        rvChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvChat.adapter = chatAdapter

        btnSend.setOnClickListener {
            val userMsg = etMessage.text.toString().trim()
            if (userMsg.isNotEmpty()) {
                // 1. Show user message
                chatAdapter.addMessage(ChatMessage(userMsg, true))
                rvChat.scrollToPosition(chatAdapter.itemCount - 1)
                etMessage.text.clear()
                btnSend.isEnabled = false

                // 2. Get transcript context and call Gemini API (coroutine)
                CoroutineScope(Dispatchers.Main).launch {
                    // Show typing indicator (optional)
                    chatAdapter.addMessage(ChatMessage("Thinking...", false))
                    rvChat.scrollToPosition(chatAdapter.itemCount - 1)

                    // Load transcript from Room (on IO dispatcher)
                    val segments = withContext(Dispatchers.IO) { transcriptDao.getAll() }
                    val contextText = segments.joinToString("\n") { it.text }
                    Log.d("AudioRecActivity", "Context: $contextText")

                    // Compose Gemini prompt
                    val prompt = """
                        ${getString(R.string.gemini_prompt)}{}
                Context:
                $contextText

                Question:
                $userMsg

                Answer in clear, helpful language:
            """.trimIndent()

                    // Build Gemini request
                    val request = GeminiRequest(
                        contents = listOf(
                            GeminiContent(
                                parts = listOf(GeminiPart(prompt))
                            )
                        )
                    )

                    try {
                        // Make Gemini API call
                        val response = GeminiApiClient.service.generateContent(
                            apiKey = "",
                            request = request
                        )
                        // Remove "Thinking..." bubble
                        chatAdapter.removeThinkingBubbleIfPresent()
                        // Show Gemini's reply
                        val reply = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            ?: "Sorry, no answer from Gemini."
                        chatAdapter.addMessage(ChatMessage(reply, false))
                        rvChat.scrollToPosition(chatAdapter.itemCount - 1)
                    } catch (e: Exception) {
                        chatAdapter.removeThinkingBubbleIfPresent()
                        chatAdapter.addMessage(ChatMessage("Error: ${e.localizedMessage}", false))
                        rvChat.scrollToPosition(chatAdapter.itemCount - 1)
                    } finally {
                        btnSend.isEnabled = true
                    }
                }
            }
        }


        // ---- END CHAT SETUP ----

        bottomSheetDialog.show()
    }

    private fun checkAudioPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1
            )
            false
        } else true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            prepareSpeechRecognizer()
            startRecording()
        } else {
            Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun prepareSpeechRecognizer() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (continueListening && isRecording) {
                        restartSpeechToTextWithDelay()
                    }
                }
                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onSpeechResult(matches[0])
                    }
                    if (continueListening && isRecording) {
                        restartSpeechToTextWithDelay()
                    }
                }
                override fun onPartialResults(partialResults: Bundle) {
                    val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onSpeechResult(matches[0])
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun onSpeechResult(text: String) {
        transcriptText = appendIfNew(transcriptText, text)
        currentSegmentText = appendIfNew(currentSegmentText, text)
        updateTranscriptAllUIs()
    }

    private fun appendIfNew(existing: String, newText: String): String {
        return if (existing.isEmpty() || !existing.endsWith(newText)) {
            (existing + " " + newText).trim()
        } else {
            existing
        }
    }

    private fun startRecording() {
        isRecording = true
        continueListening = true
        transcriptText = ""
        currentSegmentText = ""
        transcriptTime = "00:00"
        secondsElapsed = 0
        segmentStartTime = 0
        binding.LLRecodingBlock.visibility = View.VISIBLE
        binding.btnstop.visibility = View.VISIBLE
        binding.btnTranscript.visibility = View.GONE
        updateTimerText()
        startTimer()
        startSpeechToText()
    }

    suspend fun stopRecordingAndRefresh() {
        isRecording = false
        continueListening = false
        transcriptTime = getCurrentTimerText()
        binding.LLRecodingBlock.visibility = View.GONE
        binding.btnstop.visibility = View.GONE
        binding.btnTranscript.visibility = View.VISIBLE
        stopTimer()
        stopSpeechToText()
        // Save the final segment and wait for DB insert to finish
        withContext(Dispatchers.IO) {
            saveCurrentSegmentSync()
        }
        selectTab(2)
        updateTranscriptAllUIs()
    }

    // Use this in place of the old saveCurrentSegment()
    suspend fun saveCurrentSegmentSync() {
        val text = currentSegmentText.trim()
        if (text.isNotEmpty()) {
            val segment = TranscriptSegmentEntity(
                text = text,
                startTime = segmentStartTime,
                endTime = secondsElapsed,
                synced = false
            )
            transcriptDao.insert(segment)
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(60 * 60 * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsElapsed++
                // Every 30 seconds, save the segment
                if ((secondsElapsed - segmentStartTime) >= 30) {
                    saveCurrentSegment()
                    segmentStartTime = secondsElapsed
                    currentSegmentText = ""
                }
                updateTimerText()
                updateTranscriptAllUIs()
            }
            override fun onFinish() {}
        }.start()
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    private fun getCurrentTimerText(): String {
        val min = secondsElapsed / 60
        val sec = secondsElapsed % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun updateTimerText() {
        binding.tvRecordingTime.text = getCurrentTimerText()
    }

    private fun startSpeechToText() {
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun restartSpeechToTextWithDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (continueListening && isRecording) {
                startSpeechToText()
            }
        }, 100)
    }

    private fun stopSpeechToText() {
        speechRecognizer?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        stopSpeechToText()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    // --- Room: Save 30s segment ---
    private fun saveCurrentSegment() {
        val text = currentSegmentText.trim()
        if (text.isNotEmpty()) {
            val segment = TranscriptSegmentEntity(
                text = text,
                startTime = segmentStartTime,
                endTime = secondsElapsed,
                synced = false
            )
            CoroutineScope(Dispatchers.IO).launch {
                transcriptDao.insert(segment)
            }
        }
    }

    private fun formatTime(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format("%02d:%02d", min, sec)
    }

    // --- UI Tabs ---
    private fun setupTabs() {
        binding.tabQuestions.setOnClickListener { selectTab(0) }
        binding.tabNotes.setOnClickListener { selectTab(1) }
        binding.tabTranscript.setOnClickListener { selectTab(2) }
    }

    private fun setupClickListeners() {
        binding.backBtn.setOnClickListener {
            navigation.navigateToAnotherActivity(this, UserHomeActivity::class.java)
            finish()
        }
        binding.shareBtn.setOnClickListener { /* share */ }
        binding.editNotesFab.setOnClickListener { }
        binding.btnTranscript.setOnClickListener { }
        binding.btnstop.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                stopRecordingAndRefresh()
            }
        }
    }

    private fun selectTab(position: Int) {
        if (currentTab == position) return
        currentTab = position
        updateTabAppearance()
        showTabContent(position)
        updateTranscriptAllUIs()
    }

    private fun updateTabAppearance() {
        resetTabAppearance(binding.tabQuestions)
        resetTabAppearance(binding.tabNotes)
        resetTabAppearance(binding.tabTranscript)
        when (currentTab) {
            0 -> applySelectedTabAppearance(binding.tabQuestions)
            1 -> applySelectedTabAppearance(binding.tabNotes)
            2 -> applySelectedTabAppearance(binding.tabTranscript)
        }
    }

    private fun resetTabAppearance(tab: TextView) {
        tab.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        tab.setBackgroundResource(R.drawable.tab_unselected_bg)
    }

    private fun applySelectedTabAppearance(tab: TextView) {
        tab.setTextColor(ContextCompat.getColor(this, R.color.accent_blue))
        tab.setBackgroundResource(R.drawable.tab_selected_bg)
    }

    private fun showTabContent(position: Int) {
        val layoutId = when (position) {
            0 -> R.layout.view_questions_tab
            1 -> R.layout.view_notes_tab
            2 -> R.layout.view_transcript_tab
            else -> R.layout.view_notes_tab
        }
        val view = layoutInflater.inflate(layoutId, binding.tabContentContainer, false)
        binding.tabContentContainer.removeAllViews()
        binding.tabContentContainer.addView(view)
        binding.editNotesFab.visibility = if (position == 1) View.VISIBLE else View.GONE

        if (position == 2) {
            val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvTranscriptSegments)
            transcriptAdapter = TranscriptAdapter(listOf())
            rv.adapter = transcriptAdapter
            rv.layoutManager = LinearLayoutManager(this)
            loadTranscriptSegments()
        }
    }

    private fun loadTranscriptSegments() {
        CoroutineScope(Dispatchers.IO).launch {
            val segments = transcriptDao.getAll().reversed()
            withContext(Dispatchers.Main) {
                transcriptAdapter?.setItems(segments)
            }
        }
    }

    private fun updateTranscriptAllUIs() {
        val transcriptTimeView = binding.tabContentContainer.findViewById<TextView>(R.id.tv_recording_time)
        if (currentTab == 2) {
            transcriptTimeView?.text = if (isRecording) getCurrentTimerText() else transcriptTime
            loadTranscriptSegments()
        }
    }
}
