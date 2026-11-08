package com.example.aichatassistantproject

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend

class GeminiHelper {

    private val model = Firebase.ai(
        backend = GenerativeBackend.googleAI(),
        useLimitedUseAppCheckTokens = true
    ).generativeModel("gemini-3.5-flash-lite")

    suspend fun generateResponse(prompt: String): String {

        val response = model.generateContent(prompt)

        return response.text
            ?: "Sorry, I couldn't generate a response."
    }
}


