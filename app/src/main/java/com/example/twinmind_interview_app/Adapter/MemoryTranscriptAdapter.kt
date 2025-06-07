package com.example.twinmind_interview_app.Adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.database.NewRoomdb.TranscriptSessionEntity
import com.example.twinmind_interview_app.Adapter.SessionDisplayItem

// At the top of your MemoryTranscriptAdapter
private var itemslist: List<TranscriptSessionEntity> = emptyList()

class MemoryTranscriptAdapter(
    private var items: List<SessionDisplayItem>
) : RecyclerView.Adapter<MemoryTranscriptAdapter.MemoryViewHolder>() {

    inner class MemoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate = itemView.findViewById<TextView>(R.id.meetingDate)
        private val tvTimePeriod = itemView.findViewById<TextView>(R.id.meetingTimePeriod)
        private val tvTotalTime = itemView.findViewById<TextView>(R.id.meetingTotalTime)
        private val tvDescription = itemView.findViewById<TextView>(R.id.meetingdescribtion)
        private val tvTitle = itemView.findViewById<TextView>(R.id.meetingdescribtion)

        fun bind(item: SessionDisplayItem) {
            val session = item.session
            tvDate.text = formatDateHeader(session.createdAt)
            tvTimePeriod.text = formatTimePeriod(session.createdAt)
            tvTotalTime.text = formatDuration(item.durationSeconds)
            tvDescription.text = item.description // <--- set the actual text
            tvTitle.text = session.title ?: "Untitled Recording"  // ← NEW

        }



        private fun formatDateHeader(millis: Long): String {
            val sdf = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(millis))
        }
        private fun formatTimePeriod(millis: Long): String {
            val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(millis))
        }
        // IMPROVED: formatDuration() to show exact seconds
        private fun formatDuration(seconds: Int): String {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60

            return when {
                h > 0 -> "${h}h ${m}m"      // 1h 25m
                m > 0 -> "${m}m ${s}s"      // 5m 30s ← IMPROVED
                else -> "${s}s"             // 45s    ← IMPROVED
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_memory_transcript, parent, false)
        return MemoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun setItems(newItems: List<SessionDisplayItem>) {
        items = newItems
        notifyDataSetChanged()
    }



}


data class SessionDisplayItem(
    val session: TranscriptSessionEntity,
    val durationSeconds: Int,
    val description: String
)

