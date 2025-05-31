package com.example.twinmind_interview_app.Screen

import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.Utils.navigateHandlers
import com.example.twinmind_interview_app.databinding.ActivityAudioRecBinding

class AudioRecActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioRecBinding
    private var currentTab = 1 // Default to Notes tab
    private lateinit var navigation: navigateHandlers

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: String = ""
    private var timer: CountDownTimer? = null
    private var secondsElapsed = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioRecBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navigation = navigateHandlers()
        setupTabs()
        setupClickListeners()

        // Use the value
        val isRecordingStart = intent.getStringExtra("RecodingStart")
        if (isRecordingStart == "true") {
            binding.btnTranscript.visibility = View.GONE
            binding.btnstop.visibility = View.VISIBLE
            binding.LLRecodingBlock.visibility = View.VISIBLE

            startRecording()

            binding.btnstop.setOnClickListener {
                stopRecording()
                binding.btnstop.visibility = View.GONE
                binding.btnTranscript.visibility = View.VISIBLE
                binding.LLRecodingBlock.visibility = View.GONE
                Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
            }
        }


        // Default to Notes tab
        selectTab(1)
    }



    private fun startRecording() {
        outputFile = "${externalCacheDir?.absolutePath}/audiorecord.3gp"
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(outputFile)
            prepare()
            start()
        }
        startTimer()
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        stopTimer()
    }
    private fun startTimer() {
        secondsElapsed = 0
        timer = object : CountDownTimer(60 * 60 * 1000, 1000) { // 1 hour max
            override fun onTick(millisUntilFinished: Long) {
                secondsElapsed++
                val min = secondsElapsed / 60
                val sec = secondsElapsed % 60
                binding.tvRecordingTime.text = String.format("%02d:%02d", min, sec)
            }
            override fun onFinish() {}
        }.start()
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }




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

        binding.shareBtn.setOnClickListener {
            // Implement share functionality
        }

        binding.editNotesFab.setOnClickListener {
            // Implement edit notes functionality
        }

        binding.btnTranscript.setOnClickListener {
            // Implement chat with transcript functionality
        }

        binding.btnstop.setOnClickListener {
            // Implement stop recording functionality
        }
    }

    private fun selectTab(position: Int) {
        if (currentTab == position) return

        currentTab = position
        updateTabAppearance()
        showTabContent(position)
    }

    private fun updateTabAppearance() {
        // Reset all tabs to unselected state
        resetTabAppearance(binding.tabQuestions)
        resetTabAppearance(binding.tabNotes)
        resetTabAppearance(binding.tabTranscript)

        // Apply selected state to current tab
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

        // Show/hide FAB based on tab
        binding.editNotesFab.visibility = if (position == 1) View.VISIBLE else View.GONE
    }
}