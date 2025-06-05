package com.example.twinmind_interview_app.Screen.fragments.HomeTab

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.R.id.rvMemories
import com.example.twinmind_interview_app.adapter.MemoryTranscriptAdapter
import com.example.twinmind_interview_app.database.room.TranscriptDatabase
import com.example.twinmind_interview_app.database.room.TranscriptSegmentDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MemoriesFragment : Fragment() {
    private lateinit var adapter: MemoryTranscriptAdapter
    private lateinit var transcriptDao: TranscriptSegmentDao

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_memories, container, false)
        val recyclerView = view.findViewById<RecyclerView>(rvMemories)
        adapter = MemoryTranscriptAdapter(listOf())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        transcriptDao = TranscriptDatabase.getDatabase(requireContext()).transcriptDao()

        // Load data
        CoroutineScope(Dispatchers.IO).launch {
            val memories = transcriptDao.getAll()
            withContext(Dispatchers.Main) {
                adapter.setItems(memories)
            }
        }
        return view
    }
}
