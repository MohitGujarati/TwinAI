package com.example.twinmind_interview_app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.model.ChatMessage

class ChatAdapter(val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_BOT = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) TYPE_USER else TYPE_BOT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            val view = inflater.inflate(R.layout.item_chat_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_bot, parent, false)
            BotViewHolder(view)
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position].message
        if (holder is UserViewHolder) {
            holder.bind(msg)
        } else if (holder is BotViewHolder) {
            holder.bind(msg)
        }
    }

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    // Optional: clear all messages
    fun clearMessages() {
        messages.clear()
        notifyDataSetChanged()
    }

    // --- ViewHolder classes ---

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.tvMessageUser)
        fun bind(msg: String) {
            textView.text = msg
        }
    }

    class BotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.tvMessageBot)
        fun bind(msg: String) {
            textView.text = msg
        }
    }
}

// --- Extension helpers (outside the class) ---

fun ChatAdapter.removeThinkingBubbleIfPresent() {
    if (itemCount > 0 && getItem(itemCount - 1).message == "Thinking..." && !getItem(itemCount - 1).isUser) {
        removeLast()
    }
}

fun ChatAdapter.getItem(index: Int): ChatMessage = messages[index]

fun ChatAdapter.removeLast() {
    if (itemCount > 0) {
        messages.removeAt(messages.size - 1)
        notifyItemRemoved(messages.size)
    }
}
