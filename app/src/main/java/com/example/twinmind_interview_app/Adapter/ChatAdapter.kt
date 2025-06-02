package com.example.twinmind_interview_app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.model.ChatMessage
import io.noties.markwon.Markwon

class ChatAdapter(
    val messages: MutableList<ChatMessage>,
    private val markwon: Markwon
) :
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
            UserViewHolder(view, markwon)
        } else {
            val view = inflater.inflate(R.layout.item_chat_bot, parent, false)
            BotViewHolder(view, markwon)
        }
    }


    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is UserViewHolder) {
            holder.bind(message.message)
        } else if (holder is BotViewHolder) {
            holder.bind(message.message)
        }
    }

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun clearMessages() {
        val previousSize = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, previousSize)
    }

    fun updateMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun removeThinkingBubbleIfPresent() {
        val lastIndex = messages.size - 1
        if (lastIndex >= 0 &&
            messages[lastIndex].message == "Thinking..." &&
            !messages[lastIndex].isUser
        ) {
            messages.removeAt(lastIndex)
            notifyItemRemoved(lastIndex)
        }
    }


    // --- ViewHolder classes ---
    class UserViewHolder(itemView: View, private val markwon: Markwon) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.tvMessageUser)
        fun bind(msg: String) {
            markwon.setMarkdown(textView, msg)
        }
    }

    class BotViewHolder(itemView: View, private val markwon: Markwon) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.tvMessageBot)
        fun bind(msg: String) {
            markwon.setMarkdown(textView, msg)
        }
    }

}