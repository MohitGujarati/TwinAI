package com.example.twinmind_interview_app.Adapter

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.twinmind_interview_app.Screen.fragments.AudioTab.NotesFragment
import com.example.twinmind_interview_app.Screen.fragments.AudioTab.QuestionsFragment
import com.example.twinmind_interview_app.Screen.fragments.AudioTab.TranscriptFragment

class AudioTabsPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 3
    override fun createFragment(position: Int): Fragment = when(position) {
        0 -> QuestionsFragment()
        1 -> NotesFragment()
        2 -> TranscriptFragment()
        else -> throw IllegalStateException("Invalid tab position")
    }
}
