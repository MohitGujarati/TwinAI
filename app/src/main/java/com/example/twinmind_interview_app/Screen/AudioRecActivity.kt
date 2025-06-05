package com.example.twinmind_interview_app.Screen

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.twinmind_interview_app.Adapter.AudioTabsPagerAdapter
import com.example.twinmind_interview_app.BuildConfig
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.Utils.navigateHandlers
import com.example.twinmind_interview_app.Adapter.ChatAdapter
import com.example.twinmind_interview_app.databinding.ActivityAudioRecordingBinding
import com.example.twinmind_interview_app.model.ChatMessage
import com.example.twinmind_interview_app.database.room.TranscriptSegmentEntity
import com.example.twinmind_interview_app.database.room.TranscriptDatabase
import com.example.twinmind_interview_app.database.room.TranscriptSegmentDao
import com.example.twinmind_interview_app.viewmodel.ChatViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayoutMediator
import io.noties.markwon.Markwon
import kotlinx.coroutines.*
import java.util.*

class AudioRecActivity : AppCompatActivity() {

    val API_KEY = BuildConfig.GEMINI_API_KEY

    private lateinit var binding: ActivityAudioRecordingBinding
    private lateinit var navigation: navigateHandlers
    lateinit var transcriptDao: TranscriptSegmentDao

    private var speechRecognizer: SpeechRecognizer? = null
    var isRecording = false
    private var timer: CountDownTimer? = null
    private var secondsElapsed = 0

    private var transcriptText: String = ""
    private var transcriptTime: String = "00:00"
    private var continueListening = false
    private var currentTab = 1

    // For segmenting
    private var segmentStartTime = 0
    private var currentSegmentText = ""

    // Title
    private var titleGenerated = false
    private var titleJob: Job? = null

    // ViewModel
    val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        navigation = navigateHandlers()
        transcriptDao = TranscriptDatabase.getDatabase(applicationContext).transcriptDao()

        val pagerAdapter = AudioTabsPagerAdapter(this)
        binding.tabPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.tabPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.questions)
                1 -> getString(R.string.notes)
                2 -> getString(R.string.transcript)
                else -> ""
            }
        }.attach()

        setupClickListeners()

        if (checkAudioPermission()) {
            prepareSpeechRecognizer()
            startRecording()
        }

        binding.btnTranscript.setOnClickListener { showTranscriptBottomSheet() }
    }


    //LifeCycle
    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        stopSpeechToText()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }


    override fun onBackPressed() {
        super.onBackPressed()
        navigation.navigateToAnotherActivity(this, UserHomeActivity::class.java)
        finish()
    }

    //Permissions
    private fun checkAudioPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            false
        } else true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            prepareSpeechRecognizer()
            startRecording()
        } else {
            Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show()
        }
    }


    //UI set up
    private fun setupClickListeners() {
        binding.backBtn.setOnClickListener {
            navigation.navigateToAnotherActivity(this, UserHomeActivity::class.java)
            finish()
        }
        binding.shareBtn.setOnClickListener { /* share */ }
        binding.btnstop.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                stopRecordingAndRefresh(showTranscriptTab = true)
            }
        }
    }


    //Recording Control (main entrypoints)
    // start recording
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
        viewModel.setLiveTranscript("")
        updateTimerText()
        startTimer()
        startSpeechToText()

        titleJob?.cancel()
        titleJob = CoroutineScope(Dispatchers.Main).launch {
            delay(8000)
            generateTitleIfNeeded()
        }
    }

    //stop recording
    suspend fun stopRecordingAndRefresh(showTranscriptTab: Boolean = false) {
        isRecording = false
        continueListening = false
        transcriptTime = getCurrentTimerText()
        binding.LLRecodingBlock.visibility = View.GONE
        binding.btnstop.visibility = View.GONE
        binding.btnTranscript.visibility = View.VISIBLE
        stopTimer()
        stopSpeechToText()
        viewModel.setLiveTranscript("") // Hide live transcript after stop

        // Save the last segment if any (just in case the timer didn’t hit 30 seconds)
        CoroutineScope(Dispatchers.IO).launch {
            saveCurrentSegmentSync()
        }.join()

        // Get all transcript segments in correct order
        val allSegments = withContext(Dispatchers.IO) { transcriptDao.getAllOrdered() }
        Log.d("TranscriptDebug", "All loaded segments: $allSegments")

        // Format transcript
        val rawTranscriptWithTimestamps = buildString {
            if (allSegments.isEmpty()) {
                append("No transcript available yet.")
            } else {
                for (seg in allSegments) {
                    append("[${formatTime(seg.startTime)} - ${formatTime(seg.endTime)}]: ")
                    append(seg.text.trim())
                    append("\n\n")
                }
            }
        }


        // Check if online and prepare the final transcript for display
        val isOnline =
            com.example.twinmind_interview_app.network.NetworkUtils.isInternetAvailable(this@AudioRecActivity)
        var displayTranscript: String
        if (isOnline) {
            try {
                val enhanced = aiEnhanceTranscript(rawTranscriptWithTimestamps)
                viewModel.setAiEnhancedTranscript(enhanced)
                displayTranscript = "\n\n$enhanced"
            } catch (e: Exception) {
                Log.e("TranscriptDebug", "AI Enhance error: ${e.message}")
                viewModel.setAiEnhancedTranscript(rawTranscriptWithTimestamps)
                displayTranscript = "\n\n$rawTranscriptWithTimestamps"
            }
        } else {
            viewModel.setAiEnhancedTranscript(rawTranscriptWithTimestamps)
            displayTranscript = "\n\n$rawTranscriptWithTimestamps"
        }

        // Update only via LiveData (don’t set TextView directly, let Fragment observe!)
        viewModel.setAiEnhancedTranscript(displayTranscript)

        withContext(Dispatchers.Main) {
            viewModel.forceRefreshSummary(transcriptDao, API_KEY)
            // If you want to auto-switch tabs:
            // if (showTranscriptTab) selectTab(2)
        }
    }

    // --- CHAT BOTTOM SHEET ---
    private fun showTranscriptBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_transcript, null)
        bottomSheetDialog.setContentView(view)
        val bottomSheet =
            bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT

        // Header/status views
        val chatInputBlock = view.findViewById<View>(R.id.chatInputBlock)
        val aiStatusDot = view.findViewById<View>(R.id.aiStatusDot)
        val bottomSheetTranscriptText = view.findViewById<TextView>(R.id.bottomSheetTranscriptText)

        // Close button
        val closeBtn = view.findViewById<ImageView>(R.id.closeBtn)
        closeBtn.setOnClickListener { bottomSheetDialog.dismiss() }

        // Transcript word count logic
        val wordCount = getTranscriptWordCount()
        if (wordCount < 50) {
            // Show error message and red dot
            chatInputBlock.visibility = View.GONE
            bottomSheetTranscriptText?.setText(R.string.trascript_shortmsg)
            aiStatusDot?.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.red))
            //  chatContainer?.visibility = View.GONE

        } else {
            // Normal chat UI, green dot
            aiStatusDot?.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green))
            // chatContainer?.visibility = View.VISIBLE
            bottomSheetTranscriptText?.setText(R.string.you_can_ask_questions_and_get_answers_from_the_transcript)
        }

        // Chat UI setup (can be outside the if/else, it's hidden when chatContainer is GONE)
        val rvChat = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvChat)
        val etMessage = view.findViewById<EditText>(R.id.etMessage)
        val btnSend = view.findViewById<CardView>(R.id.btnSend)

        if (API_KEY.isBlank()) {
            Toast.makeText(this, "Gemini API key not configured", Toast.LENGTH_LONG).show()
            return
        }

        val markwon = Markwon.create(this)
        val chatAdapter = ChatAdapter(mutableListOf(), markwon)
        rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
            reverseLayout = false
        }
        rvChat.adapter = chatAdapter

        viewModel.messages.observe(this) { messages ->
            chatAdapter.updateMessages(messages)
            if (messages.isNotEmpty()) {
                rvChat.scrollToPosition(chatAdapter.itemCount - 1)
            }
            val isThinking = messages.any { it.message == "Thinking..." && !it.isUser }
            btnSend.isEnabled = !isThinking
            btnSend.alpha = if (isThinking) 0.5f else 1.0f
        }

        btnSend.setOnClickListener {
            val userMsg = etMessage.text.toString().trim()
            if (userMsg.isNotEmpty() && btnSend.isEnabled) {
                viewModel.sendMessage(userMsg, transcriptDao, API_KEY)
                etMessage.text.clear()
                val imm =
                    getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(etMessage.windowToken, 0)
            } else if (userMsg.isEmpty()) {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            }
        }

        etMessage.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)
            ) {
                btnSend.performClick()
                true
            } else {
                false
            }
        }

        // Welcome message if empty
        if (viewModel.messages.value.isNullOrEmpty()) {
            val welcomeMessages = listOf(
                ChatMessage(
                    "Hello! I'm here to help you with questions about your transcript.",
                    false
                )
            )
            viewModel._messages.value = welcomeMessages
        }

        bottomSheetDialog.show()
    }


    //Speech Recognition

    private fun prepareSpeechRecognizer() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("TranscriptDebug", "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d("TranscriptDebug", "Speech beginning")
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    Log.d("TranscriptDebug", "Speech ended")
                }

                override fun onError(error: Int) {
                    Log.d("TranscriptDebug", "SpeechRecognizer error: $error")
                    if (continueListening && isRecording) {
                        restartSpeechToTextWithDelay()
                    }
                }

                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        Log.d("TranscriptDebug", "onResults: ${matches[0]}")
                        onSpeechResult(matches[0])
                    }
                    if (continueListening && isRecording) {
                        restartSpeechToTextWithDelay()
                    }
                }

                override fun onPartialResults(partialResults: Bundle) {
                    val matches =
                        partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        Log.d("TranscriptDebug", "onPartialResults: ${matches[0]}")
                        onSpeechResult(matches[0])
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startSpeechToText() {
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopSpeechToText() {
        speechRecognizer?.stopListening()
    }

    private fun restartSpeechToTextWithDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (continueListening && isRecording) {
                startSpeechToText()
            }
        }, 100)
    }


    private fun onSpeechResult(text: String) {
        transcriptText = appendIfNew(transcriptText, text)
        currentSegmentText = appendIfNew(currentSegmentText, text)

        // Send live transcript to ViewModel for the fragment to observe
        viewModel.setLiveTranscript(transcriptText)

        Log.d(
            "TranscriptDebug",
            "Transcript updated: $transcriptText | Current Segment: $currentSegmentText"
        )
        updateTranscriptAllUIs()
    }


    private fun appendIfNew(existing: String, newText: String): String {
        return if (existing.isEmpty() || !existing.endsWith(newText)) {
            (existing + " " + newText).trim()
        } else {
            existing
        }
    }


    // Timer Logic

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(60 * 60 * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsElapsed++
                // Every 30 seconds, save the segment
                if ((secondsElapsed - segmentStartTime) >= 30) {
                    CoroutineScope(Dispatchers.IO).launch {
                        saveCurrentSegmentSync()
                    }
                    segmentStartTime = secondsElapsed
                    currentSegmentText = ""
                }
                updateTimerText()
                updateTranscriptAllUIs()
            }

            override fun onFinish() {}
        }.start()
    }

    private fun getCurrentTimerText(): String {
        val min = secondsElapsed / 60
        val sec = secondsElapsed % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun updateTimerText() {
        binding.tvRecordingTime.text = getCurrentTimerText()
    }


    //Transcript Management
    private fun saveCurrentSegment(force: Boolean = false) {
        val text = currentSegmentText.trim()
        if (text.isNotEmpty() || force) {
            CoroutineScope(Dispatchers.IO).launch {
                saveCurrentSegmentSync()
            }
        }
    }

    //--- KEY CHANGE: Save segment on STOP or timer!
    suspend fun saveCurrentSegmentSync() {
        val text = currentSegmentText.trim()
        if (text.isNotEmpty()) {
            val segment = TranscriptSegmentEntity(
                text = text,
                startTime = segmentStartTime,
                endTime = secondsElapsed,
                synced = false
            )
            Log.d("TranscriptDebug", "Saving segment: $segment")
            transcriptDao.insert(segment)
        }
    }

    private fun updateTranscriptAllUIs() {
        val transcriptTimeView =
            binding.tabContentContainer.findViewById<TextView>(R.id.tv_recording_time)
        if (currentTab == 2) {
            transcriptTimeView?.text = if (isRecording) getCurrentTimerText() else transcriptTime
        }
    }

    private fun getTranscriptWordCount(): Int {
        // Get the latest transcript from LiveData, or fallback to current string
        val transcript =
            viewModel.aiEnhancedTranscript.value?.ifBlank { transcriptText } ?: transcriptText
        // Count words (splitting on any whitespace)
        return transcript.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
    }

    private fun getInitialTranscriptWords(maxWords: Int = 20): String {
        return transcriptText.trim().split("\\s+".toRegex())
            .take(maxWords)
            .joinToString(" ")
    }


    //Title Generation
    private fun generateTitleIfNeeded() {
        if (titleGenerated) return
        titleGenerated = true
        val text = getInitialTranscriptWords(20)
        if (text.isBlank()) {
            setTitle(getString(R.string.untitled))
            return
        }
        val isOnline =
            com.example.twinmind_interview_app.network.NetworkUtils.isInternetAvailable(this)
        if (isOnline) {
            CoroutineScope(Dispatchers.Main).launch {
                val title = try {
                    generateGeminiTitle(text)
                } catch (e: Exception) {
                    text.split(" ").take(5).joinToString(" ")
                }
                setTitle(
                    if (title.isNotBlank()) title
                    else getString(R.string.untitled)
                )
            }
        } else {
            val fallbackTitle = text.split(" ").take(5).joinToString(" ")
            setTitle(if (fallbackTitle.isNotBlank()) fallbackTitle else getString(R.string.untitled))
        }
    }

    private fun setTitle(text: String) {
        val tvTitle = findViewById<TextView>(R.id.tv_title)
        tvTitle?.text = text
    }

    private suspend fun generateGeminiTitle(text: String): String {
        val prompt =
            "Suggest a short, clear meeting or topic title (max 6 words) for this conversation: \"$text\""
        return withContext(Dispatchers.IO) {
            viewModel.enhanceTranscriptWithGemini(prompt, API_KEY)
        }.trim().replace("\n", "")
    }


    //Transcript Formating and Enhancement

    private fun formatTime(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format("%02d:%02d", min, sec)
    }

    suspend fun aiEnhanceTranscript(rawText: String): String {
        val prompt = """
            This transcript was created by a meeting assistant app with the following features:
            - Users start transcription at the beginning of meetings via a simple interface.
            - The app captures continuous audio from the device microphone.
            - Speech is transcribed into 30-second blocks, each labeled with its time range.
            - The app is offline-first: it saves audio/text segments on device, handles network drops, and syncs when back online.
            - No segment should be lost, missing, or reordered.
            Your task:
            1. Polish and enhance each 30-second block individually: Add punctuation, correct grammar, and improve readability, but do not combine or split the blocks and do not change their order.
            2. Keep the original time range label before each block.
            3. Do not remove, merge, or re-interpret content—only clarify, fix grammar, and ensure it reads naturally.
            4. Enhance the transcript to make it more meaningful and understandable.
            Here is the raw transcript data:
            $rawText
        """.trimIndent()
        return viewModel.enhanceTranscriptWithGemini(prompt, API_KEY)
    }


}
