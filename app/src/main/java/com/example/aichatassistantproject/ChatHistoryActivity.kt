package com.example.aichatassistantproject

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatHistoryActivity : ComponentActivity() {

    private lateinit var recyclerViewHistory: RecyclerView
    private lateinit var textViewEmptyHistory: TextView
    private lateinit var editTextSearchHistory: EditText
    private lateinit var chatDao: ChatDao

    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_chat_history)

        // Connect RecyclerView
        recyclerViewHistory =
            findViewById(R.id.recyclerViewHistory)

        // Connect Empty State TextView
        textViewEmptyHistory =
            findViewById(R.id.textViewEmptyHistory)

        // Connect Search Bar
        editTextSearchHistory =
            findViewById(R.id.editTextSearchHistory)

        recyclerViewHistory.layoutManager =
            LinearLayoutManager(this)

        // Initialize Room Database
        chatDao = ChatDatabase.getDatabase(this).chatDao()

        // Listen for search text changes
        editTextSearchHistory.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                }

                override fun afterTextChanged(s: Editable?) {

                    val searchQuery =
                        s?.toString()?.trim().orEmpty()

                    loadConversationHistory(searchQuery)
                }
            }
        )

        // Load all conversations initially
        loadConversationHistory()
    }

    // Load or search conversation history
    private fun loadConversationHistory(
        searchQuery: String = ""
    ) {

        // Cancel previous search if still running
        searchJob?.cancel()

        searchJob = lifecycleScope.launch {

            try {

                val conversations = if (searchQuery.isBlank()) {

                    chatDao.getConversationSummaries()
                        .first()
                        .toMutableList()

                } else {

                    chatDao.searchConversationSummaries(
                        searchQuery
                    )
                        .first()
                        .toMutableList()
                }

                // Update empty state
                updateEmptyState(
                    conversations.isEmpty(),
                    searchQuery
                )

                // Create History Adapter
                val historyAdapter = ChatHistoryAdapter(
                    conversations,

                    // Normal click: Open conversation
                    onConversationClick = { selectedConversation ->

                        val resultIntent = Intent().apply {
                            putExtra(
                                EXTRA_CONVERSATION_ID,
                                selectedConversation.conversationId
                            )
                        }

                        setResult(RESULT_OK, resultIntent)
                        finish()
                    },

                    // Long press: Delete conversation
                    onConversationLongClick = { selectedConversation ->

                        showDeleteConfirmation(
                            selectedConversation
                        )
                    }
                )

                recyclerViewHistory.adapter = historyAdapter

            } catch (e: CancellationException) {

                throw e

            } catch (e: Exception) {

                Toast.makeText(
                    this@ChatHistoryActivity,
                    "Could not load chat history",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Show or hide Empty State
    private fun updateEmptyState(
        isEmpty: Boolean,
        searchQuery: String
    ) {

        if (isEmpty) {

            textViewEmptyHistory.text =
                if (searchQuery.isNotBlank()) {
                    "No conversations found for \"$searchQuery\"."
                } else {
                    "No conversations yet.\nStart a new chat to see your history here."
                }

            textViewEmptyHistory.visibility = View.VISIBLE
            recyclerViewHistory.visibility = View.GONE

        } else {

            textViewEmptyHistory.visibility = View.GONE
            recyclerViewHistory.visibility = View.VISIBLE
        }
    }

    // Show Delete Confirmation Dialog
    private fun showDeleteConfirmation(
        conversation: ChatConversationSummary
    ) {

        AlertDialog.Builder(this)
            .setTitle("Delete Conversation")
            .setMessage(
                "Are you sure you want to delete this conversation?"
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->

                lifecycleScope.launch {

                    try {

                        // Delete only selected conversation
                        chatDao.deleteMessagesByConversation(
                            conversation.conversationId
                        )

                        // Refresh current search results
                        val currentQuery =
                            editTextSearchHistory.text
                                .toString()
                                .trim()

                        loadConversationHistory(currentQuery)

                        Toast.makeText(
                            this@ChatHistoryActivity,
                            "Conversation deleted",
                            Toast.LENGTH_SHORT
                        ).show()

                    } catch (e: Exception) {

                        Toast.makeText(
                            this@ChatHistoryActivity,
                            "Could not delete conversation",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .show()
    }

    companion object {
        const val EXTRA_CONVERSATION_ID =
            "extra_conversation_id"
    }
}
