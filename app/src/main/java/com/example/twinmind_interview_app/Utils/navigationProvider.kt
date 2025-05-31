package com.example.twinmind_interview_app.Utils

import android.content.Context

interface navigationProvider {
    fun navigateToAnotherActivity(context: Context, desiredActivity: Class<*>)
    fun navigateMsgToAnotherActivity(context: Context, key: String, value: String, desiredActivity: Class<*>)
}