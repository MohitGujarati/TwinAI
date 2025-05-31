package com.example.twinmind_interview_app.Screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.Utils.navigateHandlers
import com.example.twinmind_interview_app.databinding.ActivityAudioRecBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.*

class AudioRecActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioRecBinding
    private lateinit var navigation: navigateHandlers

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false
    private var timer: CountDownTimer? = null
    private var secondsElapsed = 0

    private var transcriptText: String = ""
    private var transcriptTime: String = "00:00"

    private var continueListening = false
    private var currentTab = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioRecBinding.inflate(layoutInflater)
        setContentView(binding.root)
        navigation = navigateHandlers()

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
        val transcriptTextView = view.findViewById<TextView>(R.id.bottomSheetTranscript)
        transcriptTextView.text = if (transcriptText.isNotEmpty())
            transcriptText
        else
            getString(R.string.text_will_appear_here_onces_recording_is_stoped)
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
                        transcriptText = appendIfNew(transcriptText, matches[0])
                        updateTranscriptAllUIs()
                    }
                    if (continueListening && isRecording) {
                        restartSpeechToTextWithDelay()
                    }
                }
                override fun onPartialResults(partialResults: Bundle) {
                    val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        transcriptText = appendIfNew(transcriptText, matches[0])
                        updateTranscriptAllUIs()
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
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
        transcriptTime = "00:00"
        secondsElapsed = 0
        binding.LLRecodingBlock.visibility = View.VISIBLE
        binding.btnstop.visibility = View.VISIBLE
        binding.btnTranscript.visibility = View.GONE
        updateTimerText()
        startTimer()
        startSpeechToText()
    }

    private fun stopRecording() {
        isRecording = false
        continueListening = false
        transcriptTime = getCurrentTimerText()
        binding.LLRecodingBlock.visibility = View.GONE
        binding.btnstop.visibility = View.GONE
        binding.btnTranscript.visibility = View.VISIBLE
        stopTimer()
        stopSpeechToText()
        selectTab(2)
        updateTranscriptAllUIs()
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
        // DO NOT destroy SpeechRecognizer here—keep it alive for the activity lifetime.
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        stopSpeechToText()
        speechRecognizer?.destroy()
        speechRecognizer = null
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
        binding.btnstop.setOnClickListener { stopRecording() }
    }

    private fun selectTab(position: Int) {
        if (currentTab == position) return
        currentTab = position
        updateTabAppearance()
        showTabContent(position)
        updateTranscriptAllUIs() // Always refresh transcript UI when switching tabs!
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
        updateTranscriptAllUIs()
    }

    private fun updateTranscriptAllUIs() {
        val transcriptView = binding.tabContentContainer.findViewById<TextView>(R.id.transcript_text)
        val transcriptTimeView = binding.tabContentContainer.findViewById<TextView>(R.id.tv_recording_time)
        if (currentTab == 2) {
            transcriptTimeView?.text = if (isRecording) getCurrentTimerText() else transcriptTime
            transcriptView?.text = if (transcriptText.isNotEmpty()) transcriptText else getString(R.string.text_will_appear_here_onces_recording_is_stoped)
        }
    }
}
