package com.example.twinmind_interview_app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.database.room.TranscriptSegmentEntity

class MemoryTranscriptAdapter(private var items: List<TranscriptSegmentEntity>)
    : RecyclerView.Adapter<MemoryTranscriptAdapter.MemoryViewHolder>() {

    inner class MemoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTime = itemView.findViewById<TextView>(R.id.meetingTotalTime)
        private val tvText = itemView.findViewById<TextView>(R.id.meetingdescribtion)
        fun bind(item: TranscriptSegmentEntity) {
            tvTime.text = formatTime(item.endTime)
            tvText.text = item.text
        }
        private fun formatTime(seconds: Int): String {
            val min = seconds / 60
            val sec = seconds % 60
            return String.format("%02d:%02d", min, sec)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_memory_transcript, parent, false)
        return MemoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun setItems(newItems: List<TranscriptSegmentEntity>) {
        items = newItems
        notifyDataSetChanged()
    }
}
