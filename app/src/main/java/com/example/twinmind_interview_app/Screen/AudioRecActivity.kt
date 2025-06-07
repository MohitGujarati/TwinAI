package com.example.twinmind_interview_app.Screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Geocoder
import android.location.Location
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.twinmind_interview_app.Adapter.AudioTabsPagerAdapter
import com.example.twinmind_interview_app.BuildConfig
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.Utils.navigateHandlers
import com.example.twinmind_interview_app.Adapter.ChatAdapter
import com.example.twinmind_interview_app.databinding.ActivityAudioRecordingBinding
import com.example.twinmind_interview_app.model.ChatMessage
import com.example.twinmind_interview_app.viewmodel.ChatViewModel
import com.example.twinmind_interview_app.database.NewRoomdb.NewTranscriptDatabase
import com.example.twinmind_interview_app.database.NewRoomdb.NewTranscriptSegmentEntity
import com.example.twinmind_interview_app.database.NewRoomdb.NewTranscriptSegmentDao
import com.example.twinmind_interview_app.database.NewRoomdb.TranscriptSessionDao
import com.example.twinmind_interview_app.database.NewRoomdb.TranscriptSessionEntity
import com.example.twinmind_interview_app.network.NetworkUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayoutMediator
import io.noties.markwon.Markwon
import kotlinx.coroutines.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class AudioRecActivity : AppCompatActivity() {

    val API_KEY = BuildConfig.GEMINI_API_KEY

    private lateinit var binding: ActivityAudioRecordingBinding
    private lateinit var navigation: navigateHandlers

    private lateinit var newDb: NewTranscriptDatabase
    lateinit var newTranscriptDao: NewTranscriptSegmentDao
    private lateinit var sessionDao: TranscriptSessionDao

    var currentSessionId: Long = -1L  // Session ID for current recording

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

    //Location
    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geocoder: Geocoder

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        navigation = navigateHandlers()

        // ========== USE NEW DATABASE & DAOs ==========
        newDb = NewTranscriptDatabase.getDatabase(applicationContext)
        newTranscriptDao = newDb.segmentDao()
        sessionDao = newDb.sessionDao()

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

        // --- CREATE NEW SESSION WHEN RECORDING STARTS ---
        if (checkAudioPermission()) {
            prepareSpeechRecognizer()
            startNewSessionAndRecording()
        }


        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.getDefault())
        updateLocationAndTime()
        binding.btnTranscript.setOnClickListener { showTranscriptBottomSheet() }
    }

    // Update location and time
    private fun updateLocationAndTime() {
        // Update current date and time
        val currentDate = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date())

        // Check location permission and get location
        if (checkLocationPermission()) {
            getCurrentLocation { locationName ->
                val locationText = "$currentDate • $locationName"
                binding.tvUserLocation.text = locationText
            }
        } else {
            // Request location permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            // Just show date and time without location for now
            binding.tvUserLocation.text = currentDate
        }
    }

    // Check location permissions
    private fun checkLocationPermission(): Boolean {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    // Get current location
    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(callback: (String) -> Unit) {
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        getLocationName(location.latitude, location.longitude, callback)
                    } else {
                        callback("Location not available")
                    }
                }
                .addOnFailureListener {
                    callback("Location not available")
                }
        } catch (e: SecurityException) {
            callback("Location not available")
        }
    }

    // Get location name from coordinates
    private fun getLocationName(latitude: Double, longitude: Double, callback: (String) -> Unit) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    val locationName = if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val city = address.locality ?: address.subAdminArea
                        val state = address.adminArea
                        when {
                            city != null && state != null -> "$city, $state"
                            city != null -> city
                            state != null -> state
                            else -> "Unknown location"
                        }
                    } else {
                        "Unknown location"
                    }

                    withContext(Dispatchers.Main) {
                        callback(locationName)
                    }
                } catch (e: IOException) {
                    withContext(Dispatchers.Main) {
                        callback("Location not available")
                    }
                }
            }
        } catch (e: Exception) {
            callback("Location not available")
        }
    }

    // Start a new session and then recording
    private fun startNewSessionAndRecording() {
        CoroutineScope(Dispatchers.Main).launch {
            // Create and insert a new session
            withContext(Dispatchers.IO) {
                val session = TranscriptSessionEntity(
                    title = null,
                    createdAt = System.currentTimeMillis()
                )
                currentSessionId = sessionDao.insertSession(session)

            }
            startRecording()
        }
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
            startNewSessionAndRecording()
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
        Log.d("TranscriptDebug", "STOP clicked. isRecording=$isRecording currentSegmentText='$currentSegmentText' sessionId=$currentSessionId")

        isRecording = false
        continueListening = false
        transcriptTime = getCurrentTimerText()
        binding.LLRecodingBlock.visibility = View.GONE
        binding.btnstop.visibility = View.GONE
        binding.btnTranscript.visibility = View.VISIBLE
        stopTimer()
        stopSpeechToText()
        viewModel.setLiveTranscript("") // Hide live transcript after stop
        Log.d("TranscriptDebug", "STOP clicked. isRecording=$isRecording currentSegmentText='$currentSegmentText' sessionId=$currentSessionId")

        // Use AI to enhance and segment the complete transcript
        CoroutineScope(Dispatchers.IO).launch {
            val isOnline = NetworkUtils.isInternetAvailable(this@AudioRecActivity)
            if (isOnline && transcriptText.trim().isNotEmpty()) {
                try {
                    Log.d("TranscriptDebug", "Using AI to enhance and segment complete transcript (${secondsElapsed}s)")
                    val aiSegments = aiEnhanceAndSegmentTranscript(transcriptText, secondsElapsed)

                    // Save all AI-generated segments
                    for (segment in aiSegments) {
                        newTranscriptDao.insertSegment(segment)
                    }

                    Log.d("TranscriptDebug", "AI created ${aiSegments.size} enhanced segments")

                    // Set the enhanced transcript for display
                    val enhancedTranscript = aiSegments.joinToString("\n\n") { segment ->
                        "[${formatTime(segment.startTime)} - ${formatTime(segment.endTime)}]: ${segment.text}"
                    }
                    withContext(Dispatchers.Main) {
                        viewModel.setAiEnhancedTranscript(enhancedTranscript)
                    }

                } catch (e: Exception) {
                    Log.e("TranscriptDebug", "AI segmentation failed: ${e.message}")
                    // Fallback: create single segment with complete transcript
                    val fallbackSegment = NewTranscriptSegmentEntity(
                        sessionId = currentSessionId,
                        text = transcriptText.trim(),
                        startTime = 0,
                        endTime = secondsElapsed
                    )
                    newTranscriptDao.insertSegment(fallbackSegment)
                    withContext(Dispatchers.Main) {
                        viewModel.setAiEnhancedTranscript(transcriptText)
                    }
                }
            } else {
                // Offline or no content: create single segment
                if (transcriptText.trim().isNotEmpty()) {
                    val segment = NewTranscriptSegmentEntity(
                        sessionId = currentSessionId,
                        text = transcriptText.trim(),
                        startTime = 0,
                        endTime = secondsElapsed
                    )
                    newTranscriptDao.insertSegment(segment)
                    Log.d("TranscriptDebug", "Saved offline transcript: [0:00 - ${formatTime(secondsElapsed)}]")
                }
                withContext(Dispatchers.Main) {
                    viewModel.setAiEnhancedTranscript(transcriptText)
                }
            }
        }.join()

        withContext(Dispatchers.Main) {
            viewModel.forceRefreshSummary(newTranscriptDao, API_KEY, currentSessionId)
        }
    }


    // --- CHAT BOTTOM SHEET ---
    @SuppressLint("MissingInflatedId")
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

        // Transcript word count logic
        val wordCount = getTranscriptWordCount()
        if (wordCount < 50) {
            chatInputBlock.visibility = View.GONE
            bottomSheetTranscriptText?.setText(R.string.trascript_shortmsg)
            aiStatusDot?.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.red))
        } else {
            aiStatusDot?.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green))
            bottomSheetTranscriptText?.setText(R.string.you_can_ask_questions_and_get_answers_from_the_transcript)
        }

        // Chat UI setup
        val rvChat = view.findViewById<RecyclerView>(R.id.rvChat)
        val etMessage = view.findViewById<EditText>(R.id.etMessage)
        val btnSend = view.findViewById<CardView>(R.id.btnSend)
        val btnclose=view.findViewById<CardView>(R.id.btnClose)

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
                viewModel.sendMessage(userMsg, newTranscriptDao, API_KEY, currentSessionId)
                etMessage.text.clear()
                val imm =
                    getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(etMessage.windowToken, 0)
            } else if (userMsg.isEmpty()) {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            }
        }

        etMessage.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
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

        btnclose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    //Speech Recognition

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

    private fun startSpeechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
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
        Log.d("TranscriptDebug", "onSpeechResult: text='$text' currentSegmentText='$currentSegmentText'")

        viewModel.setLiveTranscript(transcriptText)
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
        val currentTime = getCurrentTimerText()
        binding.tvRecordingTime.text = currentTime
        // Update viewModel so transcript fragment can show timer
        viewModel.setRecordingTime(currentTime)
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

    //--- KEY CHANGE: Save segment with currentSessionId!
    suspend fun saveCurrentSegmentSync() {
        val text = currentSegmentText.trim()
        if (text.isNotEmpty() && currentSessionId != -1L) {
            // If this is the last segment, segmentStartTime could be anywhere, but it's correct!
            val segment = NewTranscriptSegmentEntity(
                sessionId = currentSessionId,
                text = text,
                startTime = segmentStartTime,
                endTime = secondsElapsed
            )
            Log.d("TranscriptDebug", "Saving segment: $segment")
            newTranscriptDao.insertSegment(segment)
            withContext(Dispatchers.Main) {
                viewModel.loadSessionTranscript(newTranscriptDao, currentSessionId)
            }
        } else {
            Log.d("TranscriptDebug", "Not saving segment: empty text or invalid sessionId.")
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
        val transcript =
            viewModel.aiEnhancedTranscript.value?.ifBlank { transcriptText } ?: transcriptText
        return transcript.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
    }

    private fun getInitialTranscriptWords(maxWords: Int = 20): String {
        return transcriptText.trim().split("\\s+".toRegex())
            .take(maxWords)
            .joinToString(" ")
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
            NetworkUtils.isInternetAvailable(this)
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
        val tvTitle = binding.tvTitle
        tvTitle?.text = text

        // ADDED: Save title to database
        if (currentSessionId != -1L) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val session = sessionDao.getSessionById(currentSessionId)
                    session?.let {
                        val updatedSession = it.copy(title = text)
                        sessionDao.updateSession(updatedSession)
                        Log.d("AudioRecActivity", "Title saved to database: $text")
                    }
                } catch (e: Exception) {
                    Log.e("AudioRecActivity", "Failed to save title: ${e.message}")
                }
            }
        }
    }



    private suspend fun generateGeminiTitle(text: String): String {
        val prompt =
            "Suggest a short, clear meeting or topic title (max 6 words) for this conversation: \"$text\""
        return withContext(Dispatchers.IO) {
            viewModel.enhanceTranscriptWithGemini(prompt, API_KEY)
        }.trim().replace("\n", "")
    }

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

    //NewLogin when online
    suspend fun aiEnhanceAndSegmentTranscript(completeTranscript: String, totalDurationSeconds: Int): List<NewTranscriptSegmentEntity> {
        val prompt = """
            You are a transcript processing assistant. I have a complete transcript from a ${totalDurationSeconds/60}:${String.format("%02d", totalDurationSeconds%60)} minute recording.
            
            Your task:
            1. Enhance the text: Fix grammar, add punctuation, improve readability
            2. Divide it into logical segments of approximately 30 seconds each
            3. Each segment should end at natural speech breaks (end of sentences, pauses, topic changes)
            4. Return the result in this EXACT format:
            
            SEGMENT_1|0:00-0:30|Enhanced text for first segment here.
            SEGMENT_2|0:30-1:15|Enhanced text for second segment here.  
            SEGMENT_3|1:15-2:00|Enhanced text for third segment here.
            
            Rules:
            - Each line must start with "SEGMENT_X|"
            - Time format: "MM:SS-MM:SS" 
            - End time of last segment should be ${totalDurationSeconds/60}:${String.format("%02d", totalDurationSeconds%60)}
            - Segment lengths can vary (20-45 seconds) to end at natural breaks
            - Preserve all original content, just enhance and organize it
            
            Original transcript:
            $completeTranscript
        """.trimIndent()

        val aiResponse = viewModel.enhanceTranscriptWithGemini(prompt, API_KEY)
        Log.d("TranscriptDebug", "AI segmentation response: $aiResponse")

        // Parse AI response into segments
        val segments = mutableListOf<NewTranscriptSegmentEntity>()
        val lines = aiResponse.split("\n").filter { it.trim().isNotEmpty() }

        for (line in lines) {
            if (line.startsWith("SEGMENT_") && line.contains("|")) {
                try {
                    val parts = line.split("|")
                    if (parts.size >= 3) {
                        val timeRange = parts[1]
                        val text = parts.drop(2).joinToString("|") // In case text contains |

                        val times = timeRange.split("-")
                        if (times.size == 2) {
                            val startTime = parseTimeToSeconds(times[0])
                            val endTime = parseTimeToSeconds(times[1])

                            val segment = NewTranscriptSegmentEntity(
                                sessionId = currentSessionId,
                                text = text.trim(),
                                startTime = startTime,
                                endTime = endTime
                            )
                            segments.add(segment)
                            Log.d("TranscriptDebug", "Parsed segment: [${formatTime(startTime)} - ${formatTime(endTime)}]: ${text.take(50)}...")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TranscriptDebug", "Error parsing segment line: $line", e)
                }
            }
        }

        return segments
    }

    private fun parseTimeToSeconds(timeStr: String): Int {
        val parts = timeStr.trim().split(":")
        return if (parts.size == 2) {
            val minutes = parts[0].toIntOrNull() ?: 0
            val seconds = parts[1].toIntOrNull() ?: 0
            minutes * 60 + seconds
        } else 0
    }
}
