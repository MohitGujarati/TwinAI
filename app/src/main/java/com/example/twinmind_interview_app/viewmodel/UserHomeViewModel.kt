package com.example.twinmind_interview_app.viewmodel

import androidx.lifecycle.*
import com.example.twinmind_interview_app.model.CalendarEvent
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class UserHomeViewModel : ViewModel() {
    private val _calendarEvents = MutableLiveData<List<CalendarEvent>>()
    val calendarEvents: LiveData<List<CalendarEvent>> = _calendarEvents

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Call this to fetch events
    fun loadCalendarEvents(token: String) {
        _loading.value = true
        viewModelScope.launch {
            try {
                val events = fetchCalendarEvents(token)
                _calendarEvents.value = events
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.localizedMessage
            } finally {
                _loading.value = false
            }
        }
    }

    // This is your network call logic, adapted from your Activity
    private suspend fun fetchCalendarEvents(token: String): List<CalendarEvent> {
        val now = Calendar.getInstance()
        val weekLater = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val url = URL(
            "https://www.googleapis.com/calendar/v3/calendars/primary/events" +
                    "?timeMin=${sdf.format(now.time)}&timeMax=${sdf.format(weekLater.time)}&orderBy=startTime&singleEvents=true&maxResults=10"
        )
        (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            if (responseCode != 200) throw Exception("HTTP $responseCode")
            return JSONObject(inputStream.bufferedReader().use(BufferedReader::readText))
                .getJSONArray("items").let { items ->
                    (0 until items.length()).map { i ->
                        val json = items.getJSONObject(i)
                        CalendarEvent(
                            json.optString("summary", "No Title"),
                            json.optString("description"),
                            json.optString("location"),
                            json.getJSONObject("start")
                                .optString("dateTime", json.getJSONObject("start").optString("date"))
                        )
                    }
                }
        }
    }
}
