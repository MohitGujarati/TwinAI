package com.example.twinmind_interview_app.network

import com.example.twinmind_interview_app.model.EventsResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleCalendarService {
    @GET("calendars/primary/events")
    suspend fun getEvents(
        @Query("maxResults") maxResults: Int = 10,
        @Query("orderBy") orderBy: String = "startTime",
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("timeMin") timeMin: String
    ): EventsResponse
}
