package com.example.twinmind_interview_app.Screen.fragments.HomeTab

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.Adapter.MemoryTranscriptAdapter
import com.example.twinmind_interview_app.Adapter.SessionDisplayItem   // <-- Import your data class here!
import com.example.twinmind_interview_app.database.NewRoomdb.NewTranscriptDatabase
import com.example.twinmind_interview_app.database.NewRoomdb.NewTranscriptSegmentDao
import com.example.twinmind_interview_app.database.NewRoomdb.TranscriptSessionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MemoriesFragment : Fragment() {
    private lateinit var adapter: MemoryTranscriptAdapter
    private lateinit var sessionDao: TranscriptSessionDao
    private lateinit var segmentDao: NewTranscriptSegmentDao

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_memories, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvMemories)
        adapter = MemoryTranscriptAdapter(listOf())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter




        // Init DAOs
        val db = NewTranscriptDatabase.getDatabase(requireContext())
        sessionDao = db.sessionDao()
        segmentDao = db.segmentDao()

        CoroutineScope(Dispatchers.IO).launch {
            val sessions = sessionDao.getAllSessions()
            val sessionDisplayItems = sessions.map { session ->
                val segments = segmentDao.getSegmentsForSession(session.id)
                val duration = if (segments.isNotEmpty()) segments.last().endTime - segments.first().startTime
                else 0
                val description = session.title ?: "No Title"
                SessionDisplayItem(session, duration, description)

            }
            withContext(Dispatchers.Main) {
                adapter.setItems(sessionDisplayItems)
            }
        }



        return view
    }
}
