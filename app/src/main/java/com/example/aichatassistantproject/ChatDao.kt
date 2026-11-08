package com.example.aichatassistantproject

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // Insert a new chat message
    @Insert
    suspend fun insertMessage(message: ChatEntity)

    // Retrieve messages from a specific conversation
    @Query(
        "SELECT * FROM chat_messages " +
                "WHERE conversationId = :conversationId " +
                "ORDER BY timestamp ASC, id ASC"
    )
    fun getMessagesByConversation(
        conversationId: String
    ): Flow<List<ChatEntity>>

    // Delete messages from a specific conversation
    @Query(
        "DELETE FROM chat_messages WHERE conversationId = :conversationId"
    )
    suspend fun deleteMessagesByConversation(
        conversationId: String
    )

    // Retrieve all chat messages
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC, id ASC")
    fun getAllMessages(): Flow<List<ChatEntity>>

    // Delete all chat messages
    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()

    // Retrieve conversation summaries for Chat History
    @Query(
        """
        SELECT
            conversationId,
            COALESCE(
                (
                    SELECT message
                    FROM chat_messages AS firstMessage
                    WHERE firstMessage.conversationId = chat_messages.conversationId
                    AND firstMessage.isUser = 1
                    ORDER BY firstMessage.timestamp ASC, firstMessage.id ASC
                    LIMIT 1
                ),
                'New Chat'
            ) AS title,
            MAX(timestamp) AS lastMessageTime
        FROM chat_messages
        GROUP BY conversationId
        ORDER BY MAX(timestamp) DESC
        """
    )
    fun getConversationSummaries():
            Flow<List<ChatConversationSummary>>

    // Search conversations by message content
    @Query(
        """
        SELECT
            conversationId,
            COALESCE(
                (
                    SELECT message
                    FROM chat_messages AS firstMessage
                    WHERE firstMessage.conversationId = chat_messages.conversationId
                    AND firstMessage.isUser = 1
                    ORDER BY firstMessage.timestamp ASC, firstMessage.id ASC
                    LIMIT 1
                ),
                'New Chat'
            ) AS title,
            MAX(timestamp) AS lastMessageTime
        FROM chat_messages
        WHERE conversationId IN (
            SELECT DISTINCT conversationId
            FROM chat_messages
            WHERE message LIKE '%' || :searchQuery || '%'
        )
        GROUP BY conversationId
        ORDER BY MAX(timestamp) DESC
        """
    )
    fun searchConversationSummaries(
        searchQuery: String
    ): Flow<List<ChatConversationSummary>>
}
