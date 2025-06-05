package com.example.twinmind_interview_app.Screen.fragments.AudioTab

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.R.id.summaryTextView
import com.example.twinmind_interview_app.Screen.AudioRecActivity
import com.example.twinmind_interview_app.viewmodel.ChatViewModel
import io.noties.markwon.Markwon

class NotesFragment : Fragment() {

    private val viewModel: ChatViewModel by activityViewModels()

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notes, container, false)
        val summaryCard = view.findViewById<TextView>(summaryTextView)
        val refreshBtn = view.findViewById<ImageView>(R.id.refreshSummaryBtn)

        // Observe summary LiveData
        viewModel.summary.observe(viewLifecycleOwner) { summary ->
            val markwon = Markwon.create(requireContext())
            markwon.setMarkdown(
                summaryCard,
                summary ?: "Transcript too short to generate a summary"
            )
        }

        refreshBtn.setOnClickListener {
            (activity as? AudioRecActivity)?.let {
                it.viewModel.forceRefreshSummary(it.transcriptDao, it.API_KEY)
            }
        }

        return view
    }
}
