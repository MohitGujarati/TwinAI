package com.example.twinmind_interview_app.network

import com.example.twinmind_interview_app.model.GeminiRequest
import com.example.twinmind_interview_app.model.GeminiResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GeminiApiService {
    // Try this endpoint first - it's the most commonly working one
    @POST("v1beta/models/gemini-1.5-flash-latest:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}