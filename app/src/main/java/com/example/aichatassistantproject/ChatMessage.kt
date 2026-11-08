package com.example.aichatassistantproject

data class ChatMessage(
    val message: String,
    val isUser: Boolean,
    val isTyping: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
