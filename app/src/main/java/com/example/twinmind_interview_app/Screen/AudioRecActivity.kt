package com.example.twinmind_interview_app.Screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import com.example.twinmind_interview_app.BuildConfig
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.Utils.navigateHandlers
import com.example.twinmind_interview_app.adapter.ChatAdapter
import com.example.twinmind_interview_app.adapter.TranscriptAdapter
import com.example.twinmind_interview_app.databinding.ActivityAudioRecordingBinding
import com.example.twinmind_interview_app.model.ChatMessage
import com.example.twinmind_interview_app.model.TranscriptSegmentEntity
import com.example.twinmind_interview_app.repository.TranscriptDatabase
import com.example.twinmind_interview_app.repository.TranscriptSegmentDao
import com.example.twinmind_interview_app.viewmodel.ChatViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.noties.markwon.Markwon
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class AudioRecActivity : AppCompatActivity() {

    val API_KEY = BuildConfig.GEMINI_API_KEY

    private lateinit var binding: ActivityAudioRecordingBinding
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

    // AI-enhanced transcript cache
    private var aiEnhancedTranscript: String = ""

    //Title genration
    private var titleGenerated = false
    private var titleJob: Job? = null


    // ViewModel
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioRecordingBinding.inflate(layoutInflater)
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

        binding.btnTranscript.setOnClickListener { showTranscriptBottomSheet() }
    }

    // Permissions
    private fun checkAudioPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            false
        } else true
    }

    private fun getTranscriptWordCount(): Int {
        // Combine all segment texts, or use your aiEnhancedTranscript
        val transcript = aiEnhancedTranscript.ifBlank { transcriptText }
        return transcript.trim().split("\\s+".toRegex()).size
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

    // Speech Recognizer
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
                    val matches =
                        partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
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

        titleJob?.cancel()
        titleJob = CoroutineScope(Dispatchers.Main).launch {
            delay(8000) // 8 seconds
            generateTitleIfNeeded()
        }
    }


    //Title genration
    private fun getInitialTranscriptWords(maxWords: Int = 20): String {
        return transcriptText.trim().split("\\s+".toRegex())
            .take(maxWords)
            .joinToString(" ")
    }

    private fun setTitle(text: String) {
        val tvTitle = findViewById<TextView>(R.id.tv_title)
        tvTitle?.text = text
    }

    private suspend fun generateGeminiTitle(text: String): String {
        val prompt =
            "Suggest a short, clear meeting or topic title (max 6 words) for this conversation: \"$text\""
        // You might want to cache this to avoid duplicate calls
        return withContext(Dispatchers.IO) {
            viewModel.enhanceTranscriptWithGemini(prompt, API_KEY)
        }.trim().replace("\n", "")
    }

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
                    text.take(32) // fallback: first N characters
                }
                setTitle(title)
            }
        } else {
            // Offline: just use the first 6 words as the title
            setTitle(text.split(" ").take(6).joinToString(" "))
        }
    }


    suspend fun stopRecordingAndRefresh(showTranscriptTab: Boolean = false) {
        isRecording = false
        continueListening = false
        transcriptTime = getCurrentTimerText()
        binding.LLRecodingBlock.visibility = View.GONE
        binding.btnstop.visibility = View.GONE
        binding.btnTranscript.visibility = View.VISIBLE
        stopTimer()
        stopSpeechToText()

        // Insert demo segment on stop
        withContext(Dispatchers.IO) {
            val demoText =
                "This is CS50, Harvard University's introduction to the intellectual enterprises of computer science and the art of programming. Today, we’ll explore problem solving, abstraction, and algorithms."
            val segment = TranscriptSegmentEntity(
                text = demoText,
                startTime = 0,
                endTime = 30,
                synced = false
            )
            transcriptDao.insert(segment)
            saveCurrentSegmentSync() // Optionally keep your own data as well
        }

        // Get all transcript segments in correct order
        val allSegments = withContext(Dispatchers.IO) { transcriptDao.getAllOrdered() }

        // Always format the transcript into 30s chunks/paragraphs
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
                aiEnhancedTranscript = enhanced
                displayTranscript = "\n\n$enhanced"
            } catch (e: Exception) {
                aiEnhancedTranscript = rawTranscriptWithTimestamps
                displayTranscript = "\n\n$rawTranscriptWithTimestamps"
            }
        } else {
            aiEnhancedTranscript = rawTranscriptWithTimestamps
            displayTranscript = "\n\n$rawTranscriptWithTimestamps"
        }

        aiEnhancedTranscript = displayTranscript
        withContext(Dispatchers.Main) {
            // Only update views that are currently visible in the layout!
            when (currentTab) {
                2 -> {
                    val transcriptTextView =
                        binding.tabContentContainer.findViewById<TextView>(R.id.tvTranscriptFull)
                    transcriptTextView?.text =
                        displayTranscript.ifBlank { "Press stop to see the transcript." }
                }

                1 -> {
                    val summaryCard =
                        binding.tabContentContainer.findViewById<TextView>(R.id.summaryTextView)
                    if (summaryCard != null) {
                        val markwon = Markwon.create(this@AudioRecActivity)
                        markwon.setMarkdown(
                            summaryCard,
                            viewModel.summary.value ?: "Transcript too short to generate a summary"
                        )
                    }
                }
            }

            viewModel.forceRefreshSummary(transcriptDao, API_KEY)
            // Switch to transcript tab if requested!
            if (showTranscriptTab) {
                selectTab(2)
            }
        }
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

    // --- Save 30-second segment ---
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
                    saveCurrentSegment(force = true)
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

    // Room: Save 30s segment
    private fun saveCurrentSegment(force: Boolean = false) {
        val text = currentSegmentText.trim()
        if (text.isNotEmpty() || force) {
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
            val transcriptTextView =
                binding.tabContentContainer.findViewById<TextView>(R.id.tvTranscriptFull)
            transcriptTextView?.text = ""  // Clear the text
            navigation.navigateToAnotherActivity(this, UserHomeActivity::class.java)
            finish()
        }
        binding.shareBtn.setOnClickListener { /* share */ }
        binding.btnstop.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                //insertDemoTranscript()
                stopRecordingAndRefresh(showTranscriptTab = true)

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

    @SuppressLint("SuspiciousIndentation")
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

        if (position == 1) { // Notes tab
            val summaryCard = view.findViewById<TextView>(R.id.summaryTextView)
            val refreshBtn = view.findViewById<ImageView>(R.id.refreshSummaryBtn)

            // Remove old observers before adding new one!
            viewModel.summary.removeObservers(this)
            viewModel.summary.observe(this) { summary ->
                val markwon = Markwon.create(this)
                markwon.setMarkdown(
                    summaryCard,
                    summary ?: "Transcript too short to generate a summary"
                )
            }

            refreshBtn.setOnClickListener {
                viewModel.forceRefreshSummary(transcriptDao, API_KEY)
            }

            // If summary is already available, display it instantly
            viewModel.summary.value?.let { summary ->
                val markwon = Markwon.create(this)
                markwon.setMarkdown(
                    summaryCard,
                    summary ?: "Transcript too short to generate a summary"
                )
            }
        } else if (position == 2) { // TRANSCRIPT TAB
            val transcriptTextView = view.findViewById<TextView>(R.id.tvTranscriptFull)
            transcriptTextView?.text =
                aiEnhancedTranscript.ifBlank { "Press stop to see the transcript." }
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

    private fun updateTranscriptAllUIs() {
        val transcriptTimeView =
            binding.tabContentContainer.findViewById<TextView>(R.id.tv_recording_time)
        if (currentTab == 2) {
            transcriptTimeView?.text = if (isRecording) getCurrentTimerText() else transcriptTime
        }
    }

    override fun onBackPressed() {
        val transcriptTextView =
            binding.tabContentContainer.findViewById<TextView>(R.id.tvTranscriptFull)
        transcriptTextView?.text = ""  // Clear the text

        super.onBackPressed()
        navigation.navigateToAnotherActivity(this, UserHomeActivity::class.java)
        finish()
    }

//    fun insertDemoTranscript() {
//        CoroutineScope(Dispatchers.IO).launch {
//            // Example from CS50 Harvard - "This is CS50, Harvard University's introduction to the intellectual enterprises of computer science and the art of programming."
//            val demoText =
//                "Absolutely! Here is an extended version of that iconic CS50 introduction, now with at least 200 words, maintaining the spirit and educational vibe of the course:\n" +
//                        "This is CS50, Harvard University's introduction to the intellectual enterprises of computer science and the art of programming. Today, we’ll explore problem solving, abstraction, and algorithms. Throughout this course, you’ll learn not just how to write code, but how to think methodically and solve complex problems efficiently, regardless of the language or technology involved. Computer science is fundamentally about breaking down large, complicated challenges into smaller, manageable steps—a process known as problem decomposition.\n" +
//                        "We begin by learning to represent data in different ways, whether as numbers, text, images, or even sounds. We’ll discuss how computers use binary, and how this foundation supports higher-level concepts like data structures—arrays, lists, stacks, and queues—that help organize information in memory. You’ll discover the importance of abstraction, which lets us manage complexity by focusing on high-level structures and ignoring unnecessary details.\n" +
//                        "Algorithms are step-by-step procedures for solving problems. We’ll introduce classics like searching, sorting, and recursion, examining their trade-offs in terms of speed and memory. You’ll experiment with languages such as C, Python, and JavaScript, gaining hands-on experience building your own programs.\n" +
//                        "CS50 emphasizes collaboration, community, and learning by doing. No prior background is required; curiosity and perseverance are your best assets. By the end of the course, you’ll have not only learned how computers work but also developed the mindset and tools to tackle any problem, technical or otherwise. Welcome to the journey—this is CS50.\n"
//            val segment = TranscriptSegmentEntity(
//                text = demoText,
//                startTime = 0,
//                endTime = 30,
//                synced = false
//            )
//            transcriptDao.insert(segment)
//        }
//    }

}
