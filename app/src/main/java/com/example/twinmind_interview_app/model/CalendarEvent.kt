package com.example.twinmind_interview_app.model

data class CalendarEvent(
    val summary: String?,
    val start: EventDateTime?
)

data class EventDateTime(
    val dateTime: String?,
    val date: String?
)

data class EventsResponse(
    val items: List<CalendarEvent>
)

