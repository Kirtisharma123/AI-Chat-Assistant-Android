package com.example.aichatassistantproject

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var buttonClearChat: Button
    private lateinit var buttonNewChat: Button
    private lateinit var buttonHistory: Button
    private lateinit var buttonShareChat: Button
    private lateinit var buttonExportPdf: Button

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var geminiHelper: GeminiHelper
    private lateinit var chatDao: ChatDao

    private lateinit var historyLauncher: ActivityResultLauncher<Intent>
    private lateinit var pdfSaveLauncher: ActivityResultLauncher<String>

    private var pendingPdfBytes: ByteArray? = null

    private val messageList = ArrayList<ChatMessage>()

    private var currentConversationId: String = "default"

    private val preferences by lazy {
        getSharedPreferences("chat_preferences", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register History screen result launcher
        historyLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == RESULT_OK) {

                val selectedConversationId =
                    result.data?.getStringExtra(
                        ChatHistoryActivity.EXTRA_CONVERSATION_ID
                    )

                if (!selectedConversationId.isNullOrBlank()) {
                    loadConversation(selectedConversationId)
                }
            }
        }

        // Register PDF save launcher
        pdfSaveLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) { uri ->

            if (uri != null) {
                savePdfToDevice(uri)
            } else {
                pendingPdfBytes = null

                Toast.makeText(
                    this,
                    "PDF saving cancelled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Initialize Firebase
        Firebase.initialize(context = this)

        // Configure Firebase App Check
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        setContentView(R.layout.activity_main)

        // Connect XML views
        recyclerView = findViewById(R.id.recyclerViewMessages)
        messageInput = findViewById(R.id.editTextMessage)
        sendButton = findViewById(R.id.buttonSend)
        buttonClearChat = findViewById(R.id.buttonClearChat)
        buttonNewChat = findViewById(R.id.buttonNewChat)
        buttonHistory = findViewById(R.id.buttonHistory)
        buttonShareChat = findViewById(R.id.buttonShareChat)
        buttonExportPdf = findViewById(R.id.buttonExportPdf)

        // Restore previously active conversation
        currentConversationId = preferences.getString(
            "current_conversation_id",
            "default"
        ) ?: "default"

        // Initialize Gemini
        geminiHelper = GeminiHelper()

        // Initialize Room Database
        chatDao = ChatDatabase.getDatabase(this).chatDao()

        // Set up RecyclerView
        chatAdapter = ChatAdapter(messageList)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = chatAdapter

        // Initially load the active conversation
        loadConversation(currentConversationId)

        // Share full conversation
        buttonShareChat.setOnClickListener {

            if (!sendButton.isEnabled) {
                showWaitMessage()
                return@setOnClickListener
            }

            shareFullConversation()
        }

        // Export conversation as PDF
        buttonExportPdf.setOnClickListener {

            if (!sendButton.isEnabled) {
                showWaitMessage()
                return@setOnClickListener
            }

            exportConversationAsPdf()
        }

        // History button
        buttonHistory.setOnClickListener {

            if (!sendButton.isEnabled) {
                showWaitMessage()
                return@setOnClickListener
            }

            val intent = Intent(
                this,
                ChatHistoryActivity::class.java
            )

            historyLauncher.launch(intent)
        }

        // New Chat button
        buttonNewChat.setOnClickListener {

            if (!sendButton.isEnabled) {
                showWaitMessage()
                return@setOnClickListener
            }

            currentConversationId = UUID.randomUUID().toString()

            preferences.edit()
                .putString(
                    "current_conversation_id",
                    currentConversationId
                )
                .apply()

            messageList.clear()
            chatAdapter.notifyDataSetChanged()

            messageInput.text.clear()

            Toast.makeText(
                this,
                "New chat started",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Clear current conversation
        buttonClearChat.setOnClickListener {

            if (!sendButton.isEnabled) {
                showWaitMessage()
                return@setOnClickListener
            }

            android.app.AlertDialog.Builder(this)
                .setTitle("Clear Chat")
                .setMessage(
                    "Are you sure you want to delete messages from this conversation?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->

                    setChatControlsEnabled(false)

                    lifecycleScope.launch {

                        try {

                            chatDao.deleteMessagesByConversation(
                                currentConversationId
                            )

                            messageList.clear()
                            chatAdapter.notifyDataSetChanged()

                            Toast.makeText(
                                this@MainActivity,
                                "Current conversation cleared",
                                Toast.LENGTH_SHORT
                            ).show()

                        } catch (e: Exception) {

                            Toast.makeText(
                                this@MainActivity,
                                "Could not clear conversation",
                                Toast.LENGTH_SHORT
                            ).show()

                        } finally {
                            setChatControlsEnabled(true)
                        }
                    }
                }
                .show()
        }

        // Send button
        sendButton.setOnClickListener {

            val message = messageInput.text.toString().trim()

            if (message.isNotEmpty()) {

                val userMessage = ChatMessage(
                    message = message,
                    isUser = true
                )

                messageList.add(userMessage)

                chatAdapter.notifyItemInserted(
                    messageList.size - 1
                )

                recyclerView.scrollToPosition(
                    messageList.size - 1
                )

                messageInput.text.clear()

                // Add typing indicator
                val replyPosition = messageList.size

                messageList.add(
                    ChatMessage(
                        message = "●  ●  ●",
                        isUser = false,
                        isTyping = true
                    )
                )

                chatAdapter.notifyItemInserted(replyPosition)

                recyclerView.scrollToPosition(replyPosition)

                setChatControlsEnabled(false)

                val requestConversationId = currentConversationId

                lifecycleScope.launch {

                    try {

                        // Save user's message
                        chatDao.insertMessage(
                            ChatEntity(
                                message = message,
                                isUser = true,
                                conversationId = requestConversationId
                            )
                        )

                        // Generate AI response
                        val aiResponse =
                            geminiHelper.generateResponse(message)

                        messageList[replyPosition] = ChatMessage(
                            message = aiResponse,
                            isUser = false
                        )

                        // Save AI response
                        chatDao.insertMessage(
                            ChatEntity(
                                message = aiResponse,
                                isUser = false,
                                conversationId = requestConversationId
                            )
                        )

                    } catch (e: Exception) {

                        messageList[replyPosition] = ChatMessage(
                            message = "Sorry, I couldn't generate a response right now. Please check your internet connection and try again.",
                            isUser = false
                        )

                    } finally {

                        chatAdapter.notifyItemChanged(replyPosition)

                        recyclerView.scrollToPosition(
                            messageList.size - 1
                        )

                        setChatControlsEnabled(true)
                    }
                }

            } else {
                messageInput.error = "Please enter a message"
            }
        }

        // Handle Send action from keyboard
        messageInput.setOnEditorActionListener { _, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_SEND) {

                sendButton.performClick()
                true

            } else {
                false
            }
        }
    }

    // Share complete current conversation
    private fun shareFullConversation() {

        lifecycleScope.launch {

            try {

                val savedMessages = chatDao
                    .getMessagesByConversation(currentConversationId)
                    .first()

                if (savedMessages.isEmpty()) {

                    Toast.makeText(
                        this@MainActivity,
                        "There is no conversation to share",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@launch
                }

                val dateFormat = SimpleDateFormat(
                    "dd MMM yyyy, hh:mm a",
                    Locale.getDefault()
                )

                val shareText = buildString {

                    appendLine("AI Chat Assistant")
                    appendLine("Conversation")
                    appendLine("------------------------------")
                    appendLine()

                    savedMessages.forEach { chat ->

                        val sender = if (chat.isUser) {
                            "You"
                        } else {
                            "Gemini AI"
                        }

                        appendLine("$sender:")
                        appendLine(chat.message)
                        appendLine(
                            "Time: ${dateFormat.format(Date(chat.timestamp))}"
                        )
                        appendLine()
                    }

                    appendLine("------------------------------")
                    appendLine("Shared from AI Chat Assistant")
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"

                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        "My AI Chat Conversation"
                    )

                    putExtra(
                        Intent.EXTRA_TEXT,
                        shareText
                    )
                }

                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        "Share Conversation"
                    )
                )

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    "Could not share conversation",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Export current conversation as PDF
    private fun exportConversationAsPdf() {

        lifecycleScope.launch {

            setChatControlsEnabled(false)

            try {

                val savedMessages = chatDao
                    .getMessagesByConversation(currentConversationId)
                    .first()

                if (savedMessages.isEmpty()) {

                    Toast.makeText(
                        this@MainActivity,
                        "There is no conversation to export",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@launch
                }

                pendingPdfBytes = createConversationPdf(savedMessages)

                val dateFormat = SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.getDefault()
                )

                val fileName = "AI_Chat_${dateFormat.format(Date())}.pdf"

                pdfSaveLauncher.launch(fileName)

            } catch (e: Exception) {

                pendingPdfBytes = null

                Toast.makeText(
                    this@MainActivity,
                    "Could not create PDF",
                    Toast.LENGTH_SHORT
                ).show()

            } finally {
                setChatControlsEnabled(true)
            }
        }
    }

    // Create a multi-page PDF
    private fun createConversationPdf(
        savedMessages: List<ChatEntity>
    ): ByteArray {

        val pdfDocument = PdfDocument()
        val outputStream = ByteArrayOutputStream()

        val pageWidth = 595
        val pageHeight = 842
        val leftMargin = 40f
        val rightMargin = 40f
        val bottomMargin = 50f
        val usableWidth = pageWidth - leftMargin - rightMargin

        var pageNumber = 1

        val bodyPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }

        val titlePaint = Paint().apply {
            color = android.graphics.Color.rgb(38, 58, 115)
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val senderPaint = Paint().apply {
            color = android.graphics.Color.rgb(38, 58, 115)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val timePaint = Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 9f
            isAntiAlias = true
        }

        val footerPaint = Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 9f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        var page = pdfDocument.startPage(
            PdfDocument.PageInfo.Builder(
                pageWidth,
                pageHeight,
                pageNumber
            ).create()
        )

        var canvas = page.canvas
        var y = 55f

        fun startNewPage() {

            canvas.drawText(
                "AI Chat Assistant",
                pageWidth / 2f,
                pageHeight - 25f,
                footerPaint
            )

            pdfDocument.finishPage(page)

            pageNumber++

            page = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(
                    pageWidth,
                    pageHeight,
                    pageNumber
                ).create()
            )

            canvas = page.canvas
            y = 55f
        }

        // Draw text with automatic line wrapping
        fun drawWrappedText(
            text: String,
            paint: Paint,
            extraIndent: Float = 0f
        ) {

            val maxWidth = usableWidth - extraIndent

            val paragraphs = text.split("\n")

            for (paragraph in paragraphs) {

                if (paragraph.isBlank()) {
                    y += 7f
                    continue
                }

                val words = paragraph.split(Regex("\\s+"))
                var currentLine = ""

                for (word in words) {

                    val testLine = if (currentLine.isEmpty()) {
                        word
                    } else {
                        "$currentLine $word"
                    }

                    if (
                        currentLine.isNotEmpty() &&
                        paint.measureText(testLine) > maxWidth
                    ) {

                        if (y > pageHeight - bottomMargin) {
                            startNewPage()
                        }

                        canvas.drawText(
                            currentLine,
                            leftMargin + extraIndent,
                            y,
                            paint
                        )

                        y += 16f
                        currentLine = word

                    } else {
                        currentLine = testLine
                    }
                }

                if (currentLine.isNotEmpty()) {

                    if (y > pageHeight - bottomMargin) {
                        startNewPage()
                    }

                    canvas.drawText(
                        currentLine,
                        leftMargin + extraIndent,
                        y,
                        paint
                    )

                    y += 16f
                }
            }
        }

        try {

            drawWrappedText(
                "AI Chat Assistant",
                titlePaint
            )

            y += 5f

            drawWrappedText(
                "Conversation Export",
                bodyPaint
            )

            y += 10f

            canvas.drawLine(
                leftMargin,
                y,
                pageWidth - rightMargin,
                y,
                bodyPaint
            )

            y += 25f

            val dateFormat = SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
            )

            savedMessages.forEach { chat ->

                val sender = if (chat.isUser) {
                    "You"
                } else {
                    "Gemini AI"
                }

                drawWrappedText(
                    sender,
                    senderPaint
                )

                drawWrappedText(
                    chat.message,
                    bodyPaint,
                    8f
                )

                drawWrappedText(
                    "Time: ${dateFormat.format(Date(chat.timestamp))}",
                    timePaint
                )

                y += 12f
            }

            canvas.drawText(
                "Generated by AI Chat Assistant",
                pageWidth / 2f,
                pageHeight - 25f,
                footerPaint
            )

            pdfDocument.finishPage(page)

            pdfDocument.writeTo(outputStream)

            return outputStream.toByteArray()

        } finally {
            pdfDocument.close()
            outputStream.close()
        }
    }

    // Save generated PDF to selected device location
    private fun savePdfToDevice(uri: Uri) {

        val pdfBytes = pendingPdfBytes

        if (pdfBytes == null) {

            Toast.makeText(
                this,
                "PDF data not available",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        lifecycleScope.launch {

            try {

                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(pdfBytes)
                } ?: throw Exception("Could not open file")

                pendingPdfBytes = null

                Toast.makeText(
                    this@MainActivity,
                    "PDF saved successfully",
                    Toast.LENGTH_LONG
                ).show()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"

                    putExtra(
                        Intent.EXTRA_STREAM,
                        uri
                    )

                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        "Share PDF Conversation"
                    )
                )

            } catch (e: Exception) {

                pendingPdfBytes = null

                Toast.makeText(
                    this@MainActivity,
                    "Could not save PDF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Load a particular conversation
    private fun loadConversation(conversationId: String) {

        setChatControlsEnabled(false)

        lifecycleScope.launch {

            try {

                val savedMessages = chatDao
                    .getMessagesByConversation(conversationId)
                    .first()

                currentConversationId = conversationId

                preferences.edit()
                    .putString(
                        "current_conversation_id",
                        currentConversationId
                    )
                    .apply()

                messageList.clear()

                messageList.addAll(
                    savedMessages.map { entity ->
                        ChatMessage(
                            message = entity.message,
                            isUser = entity.isUser,
                            timestamp = entity.timestamp
                        )
                    }
                )

                chatAdapter.notifyDataSetChanged()

                if (messageList.isNotEmpty()) {
                    recyclerView.scrollToPosition(
                        messageList.size - 1
                    )
                }

            } catch (e: Exception) {

                Toast.makeText(
                    this@MainActivity,
                    "Could not load conversation",
                    Toast.LENGTH_SHORT
                ).show()

            } finally {
                setChatControlsEnabled(true)
            }
        }
    }

    // Enable or disable chat controls
    private fun setChatControlsEnabled(enabled: Boolean) {

        sendButton.isEnabled = enabled
        buttonNewChat.isEnabled = enabled
        buttonClearChat.isEnabled = enabled
        buttonHistory.isEnabled = enabled
        buttonShareChat.isEnabled = enabled
        buttonExportPdf.isEnabled = enabled
    }

    // Show wait message
    private fun showWaitMessage() {

        Toast.makeText(
            this,
            "Please wait until the current operation finishes.",
            Toast.LENGTH_SHORT
        ).show()
    }
}
