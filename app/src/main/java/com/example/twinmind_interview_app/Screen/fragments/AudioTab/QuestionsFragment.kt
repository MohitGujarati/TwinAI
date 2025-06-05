package com.example.twinmind_interview_app.Screen.fragments.AudioTab

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.viewmodel.ChatViewModel

class QuestionsFragment : Fragment() {
    private val viewModel: ChatViewModel by activityViewModels()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_questions, container, false)
    }

}