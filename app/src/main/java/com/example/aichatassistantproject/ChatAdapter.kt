package com.example.aichatassistantproject

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val messageList: ArrayList<ChatMessage>
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        val messageContainer =
            itemView.findViewById<LinearLayout>(R.id.messageContainer)

        val messageBubbleContainer =
            itemView.findViewById<LinearLayout>(R.id.messageBubbleContainer)

        val textViewMessage =
            itemView.findViewById<TextView>(R.id.textViewMessage)

        val textViewTimestamp =
            itemView.findViewById<TextView>(R.id.textViewTimestamp)

        val textViewAiAvatar =
            itemView.findViewById<TextView>(R.id.textViewAiAvatar)

        var typingAnimator: ObjectAnimator? = null
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ChatViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)

        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ChatViewHolder,
        position: Int
    ) {

        val message = messageList[position]

        // Set message text
        holder.textViewMessage.text = message.message

        // Show AI avatar only for AI messages
        holder.textViewAiAvatar.visibility =
            if (message.isUser) View.GONE else View.VISIBLE

        // Format timestamp
        val timeFormat = SimpleDateFormat(
            "hh:mm a",
            Locale.getDefault()
        )

        holder.textViewTimestamp.text =
            timeFormat.format(Date(message.timestamp))

        // Hide timestamp for typing indicator
        holder.textViewTimestamp.visibility =
            if (message.isTyping) View.GONE else View.VISIBLE

        // Reset previous animation
        holder.typingAnimator?.cancel()
        holder.typingAnimator = null
        holder.textViewMessage.alpha = 1f

        // Typing animation
        if (message.isTyping) {

            holder.typingAnimator = ObjectAnimator.ofFloat(
                holder.textViewMessage,
                View.ALPHA,
                0.3f,
                1f
            ).apply {
                duration = 650
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                start()
            }
        }

        // Set message bubble background
        holder.textViewMessage.setBackgroundResource(
            if (message.isUser) {
                R.drawable.bg_user_message
            } else {
                R.drawable.bg_ai_message
            }
        )

        // Set message text color
        holder.textViewMessage.setTextColor(
            if (message.isUser) {
                Color.WHITE
            } else {
                Color.rgb(37, 38, 65)
            }
        )

        // Align user and AI messages
        holder.messageContainer.gravity =
            if (message.isUser) {
                Gravity.END
            } else {
                Gravity.START
            }

        holder.messageBubbleContainer.gravity =
            if (message.isUser) {
                Gravity.END
            } else {
                Gravity.START
            }

        // Set timestamp alignment
        holder.textViewTimestamp.gravity =
            if (message.isUser) {
                Gravity.END
            } else {
                Gravity.START
            }

        // Copy and Share options on long press
        holder.textViewMessage.setOnLongClickListener {

            if (!message.isTyping && message.message.isNotBlank()) {

                val popupMenu = PopupMenu(
                    holder.itemView.context,
                    holder.textViewMessage
                )

                popupMenu.menu.add(0, 1, 0, "Copy")
                popupMenu.menu.add(0, 2, 1, "Share")

                popupMenu.setOnMenuItemClickListener { item ->

                    when (item.itemId) {

                        // Copy message
                        1 -> {

                            val clipboardManager =
                                holder.itemView.context.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                                ) as ClipboardManager

                            val clip = ClipData.newPlainText(
                                "Chat Message",
                                message.message
                            )

                            clipboardManager.setPrimaryClip(clip)

                            Toast.makeText(
                                holder.itemView.context,
                                "Message copied",
                                Toast.LENGTH_SHORT
                            ).show()

                            true
                        }

                        // Share message
                        2 -> {

                            val shareIntent = Intent(
                                Intent.ACTION_SEND
                            ).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    message.message
                                )
                            }

                            holder.itemView.context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    "Share message"
                                )
                            )

                            true
                        }

                        else -> false
                    }
                }

                popupMenu.show()
                true

            } else {
                false
            }
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    // Stop animation when ViewHolder is recycled
    override fun onViewRecycled(holder: ChatViewHolder) {
        super.onViewRecycled(holder)

        holder.typingAnimator?.cancel()
        holder.typingAnimator = null

        holder.textViewMessage.alpha = 1f
        holder.textViewMessage.setOnLongClickListener(null)
    }
}
