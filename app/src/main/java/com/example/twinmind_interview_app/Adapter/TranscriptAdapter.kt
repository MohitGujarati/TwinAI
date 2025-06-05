package com.example.twinmind_interview_app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.database.NewRoomdb.NewTranscriptSegmentEntity

class TranscriptAdapter(private var items: List<NewTranscriptSegmentEntity>) :
    RecyclerView.Adapter<TranscriptAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.tv_recording_time)
        val text: TextView = view.findViewById(R.id.transcript_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transcript_segment, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val seg = items[position]
        holder.time.text = String.format(
            "%02d:%02d - %02d:%02d",
            seg.startTime / 60, seg.startTime % 60,
            seg.endTime / 60, seg.endTime % 60
        )
        holder.text.text = seg.text
    }

    fun setItems(newItems: List<NewTranscriptSegmentEntity>) {
        items = newItems
        notifyDataSetChanged()
    }
}
