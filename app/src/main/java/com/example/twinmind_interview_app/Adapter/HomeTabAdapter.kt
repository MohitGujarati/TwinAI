package com.example.twinmind_interview_app.Adapter

import com.example.twinmind_interview_app.Screen.fragments.HomeTab.CalendarFragment
import com.example.twinmind_interview_app.Screen.fragments.HomeTab.MemoriesFragment
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.twinmind_interview_app.Screen.fragments.HomeTab.QuestionFragment

class UserHomePagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> MemoriesFragment()
        1 -> CalendarFragment()
        2 -> QuestionFragment()
        else -> throw IllegalStateException("Invalid position $position")
    }
}
