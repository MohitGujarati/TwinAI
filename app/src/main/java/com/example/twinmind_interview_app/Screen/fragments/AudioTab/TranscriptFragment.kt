package com.example.twinmind_interview_app.Screen.fragments.AudioTab

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.Screen.AudioRecActivity
import com.example.twinmind_interview_app.viewmodel.ChatViewModel

class TranscriptFragment : Fragment() {
    private val viewModel: ChatViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_transcript, container, false)
        val transcriptTextView = view.findViewById<TextView>(R.id.tvTranscriptFull)

        // Show live transcript while recording
        viewModel.liveTranscript.observe(viewLifecycleOwner) { liveText ->
            if ((activity as? AudioRecActivity)?.isRecording == true) {
                transcriptTextView?.text = liveText.ifBlank { "Listening..." }
            }
        }

        // Show enhanced/final transcript when not recording
        viewModel.aiEnhancedTranscript.observe(viewLifecycleOwner) { finalText ->
            if ((activity as? AudioRecActivity)?.isRecording == false) {
                transcriptTextView?.text = finalText.ifBlank { "Press stop to see the transcript." }
            }
        }

        return view
    }
}


