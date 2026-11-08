package com.example.aichatassistantproject

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "chat_messages")
data class ChatEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val message: String,

    val isUser: Boolean,

    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(defaultValue = "'default'")
    val conversationId: String = "default"
)
