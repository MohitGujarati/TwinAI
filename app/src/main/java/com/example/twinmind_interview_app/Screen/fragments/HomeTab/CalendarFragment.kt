package com.example.twinmind_interview_app.Screen.fragments.HomeTab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.Screen.UserHomeActivity
import com.example.twinmind_interview_app.viewmodel.UserHomeViewModel

class CalendarFragment : Fragment() {

    private val viewModel: UserHomeViewModel by activityViewModels()
    private var eventsLoaded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvCalendarEvents = view.findViewById<TextView>(R.id.tvCalendarEvents)

        // Observe LiveData for events, loading, error
        viewModel.calendarEvents.observe(viewLifecycleOwner) { events ->
            eventsLoaded = !events.isNullOrEmpty()
            tvCalendarEvents.text = if (events.isNullOrEmpty()) {
                "No events found."
            } else {
                events.joinToString("\n\n") {
                    "📅 ${it.summary}\n⏰ ${it.start?.dateTime ?: it.start?.date}"
                }
            }
        }

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                tvCalendarEvents.text = "Loading events..."
            }
        }
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let { tvCalendarEvents.text = "Error: $it" }
        }

        // Only trigger load if not already loaded (avoid repeat fetches)
        if (!eventsLoaded) {
            val token = (activity as? UserHomeActivity)?.getGoogleCalendarToken()
            if (!token.isNullOrBlank()) {
                viewModel.loadCalendarEvents(token)
            } else {
                tvCalendarEvents.text = "Google Calendar access token not found."
            }
        }
    }
}
