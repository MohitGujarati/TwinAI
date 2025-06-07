package com.example.twinmind_interview_app.Screen.fragments.AudioTab

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.Screen.AudioRecActivity
import com.example.twinmind_interview_app.viewmodel.ChatViewModel
import com.example.twinmind_interview_app.database.NewRoomdb.NewTranscriptSegmentDao

class TranscriptFragment : Fragment() {
    private val viewModel: ChatViewModel by activityViewModels()
    private lateinit var transcriptTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_transcript, container, false)
        transcriptTextView = view.findViewById(R.id.tvTranscriptFull)
        val timerTextView = view.findViewById<TextView>(R.id.tv_recording_time)

        viewModel.sessionTranscript.observe(viewLifecycleOwner) { updateTranscriptDisplay() }
        viewModel.liveTranscript.observe(viewLifecycleOwner) { updateTranscriptDisplay() }
        viewModel.aiEnhancedTranscript.observe(viewLifecycleOwner) { updateTranscriptDisplay() }
        // Update timer display
        viewModel.recordingTime.observe(viewLifecycleOwner) { timeText ->
            timerTextView?.text = timeText ?: "00:00"
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        // Always refresh from DB when this fragment is visible
        val activity = activity as? AudioRecActivity
        val segmentDao = activity?.newTranscriptDao
        val sessionId = activity?.currentSessionId ?: -1
        if (segmentDao != null && sessionId != -1L) {
            viewModel.refreshTranscript(segmentDao, sessionId)
        }
    }

    private fun updateTranscriptDisplay() {
        val activity = activity as? AudioRecActivity
        val isRecording = activity?.isRecording == true
        val savedText = viewModel.sessionTranscript.value ?: ""
        val liveBuffer = viewModel.liveTranscript.value ?: ""
        val aiEnhanced = viewModel.aiEnhancedTranscript.value ?: ""

        val displayText = when {
            aiEnhanced.isNotBlank() -> aiEnhanced
            isRecording && liveBuffer.isNotBlank() ->
                if (savedText.isNotBlank()) "$savedText\n\n[Live]: $liveBuffer"
                else "[Live]: $liveBuffer"
            savedText.isNotBlank() -> savedText
            isRecording -> "Listening... Speak to start transcription."
            else -> "No transcript available. Start recording to generate transcript."
        }

        transcriptTextView.text = displayText
    }
}
