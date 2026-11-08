package com.example.aichatassistantproject

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatHistoryAdapter(
    private val conversationList: List<ChatConversationSummary>,
    private val onConversationClick: (ChatConversationSummary) -> Unit,
    private val onConversationLongClick: (ChatConversationSummary) -> Unit = {}
) : RecyclerView.Adapter<ChatHistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        val historyItemContainer =
            itemView.findViewById<LinearLayout>(
                R.id.historyItemContainer
            )

        val conversationTitle =
            itemView.findViewById<TextView>(
                R.id.textViewConversationTitle
            )

        val conversationTime =
            itemView.findViewById<TextView>(
                R.id.textViewConversationTime
            )
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HistoryViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_history, parent, false)

        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: HistoryViewHolder,
        position: Int
    ) {

        val conversation = conversationList[position]

        // Display conversation title
        holder.conversationTitle.text = conversation.title

        // Format last message time
        val dateFormat = SimpleDateFormat(
            "dd MMM yyyy, hh:mm a",
            Locale.getDefault()
        )

        holder.conversationTime.text =
            dateFormat.format(Date(conversation.lastMessageTime))

        // Handle normal conversation click
        holder.historyItemContainer.setOnClickListener {
            onConversationClick(conversation)
        }

        // Handle long press on conversation
        holder.historyItemContainer.setOnLongClickListener {
            onConversationLongClick(conversation)
            true
        }
    }

    override fun getItemCount(): Int {
        return conversationList.size
    }
}
