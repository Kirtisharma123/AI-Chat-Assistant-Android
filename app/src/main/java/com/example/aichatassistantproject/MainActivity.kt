package com.example.aichatassistantproject

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
    private lateinit var buttonVoiceInput: Button

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var geminiHelper: GeminiHelper
    private lateinit var chatDao: ChatDao

    private lateinit var historyLauncher: ActivityResultLauncher<Intent>
    private lateinit var pdfSaveLauncher: ActivityResultLauncher<String>
    private lateinit var voiceInputLauncher: ActivityResultLauncher<Intent>
    private lateinit var microphonePermissionLauncher: ActivityResultLauncher<String>

    private var pendingPdfBytes: ByteArray? = null

    private val messageList = ArrayList<ChatMessage>()

    private var currentConversationId: String = "default"

    private val preferences by lazy {
        getSharedPreferences("chat_preferences", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle system navigation bar and keyboard separately
        WindowCompat.setDecorFitsSystemWindows(window, false)

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

        voiceInputLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == RESULT_OK) {

                val spokenText = result.data
                    ?.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS
                    )
                    ?.firstOrNull()

                if (!spokenText.isNullOrBlank()) {

                    messageInput.setText(spokenText)
                    messageInput.setSelection(messageInput.text.length)

                } else {

                    Toast.makeText(
                        this,
                        "No speech recognized. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        microphonePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (isGranted) {
                startVoiceRecognition()
            } else {

                Toast.makeText(
                    this,
                    "Microphone permission is required for voice input.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // Initialize Firebase
        Firebase.initialize(context = this)

        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        setContentView(R.layout.activity_main)

        // Handle system navigation bar and keyboard insets
        val rootLayout = findViewById<View>(R.id.rootLayout)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            val keyboard = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, keyboard.bottom)
            )

            insets
        }

        ViewCompat.requestApplyInsets(rootLayout)

        // Connect XML views
        recyclerView = findViewById(R.id.recyclerViewMessages)
        messageInput = findViewById(R.id.editTextMessage)
        sendButton = findViewById(R.id.buttonSend)
        buttonClearChat = findViewById(R.id.buttonClearChat)
        buttonNewChat = findViewById(R.id.buttonNewChat)
        buttonHistory = findViewById(R.id.buttonHistory)
        buttonShareChat = findViewById(R.id.buttonShareChat)
        buttonExportPdf = findViewById(R.id.buttonExportPdf)
        buttonVoiceInput = findViewById(R.id.buttonVoiceInput)

        // Keep input and buttons active
        messageInput.isEnabled = true
        messageInput.isFocusable = true
        messageInput.isFocusableInTouchMode = true
        messageInput.isClickable = true

        sendButton.isEnabled = true
        buttonVoiceInput.isEnabled = true

        // Restore active conversation
        currentConversationId = preferences.getString(
            "current_conversation_id",
            "default"
        ) ?: "default"

        geminiHelper = GeminiHelper()

        chatDao = ChatDatabase.getDatabase(this).chatDao()

        // RecyclerView setup
        chatAdapter = ChatAdapter(messageList)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = chatAdapter

        // Share conversation
        buttonShareChat.setOnClickListener {

            if (!sendButton.isEnabled) {
                showWaitMessage()
                return@setOnClickListener
            }

            shareFullConversation()
        }

        // Export PDF
        buttonExportPdf.setOnClickListener {

            if (!sendButton.isEnabled) {
                showWaitMessage()
                return@setOnClickListener
            }

            exportConversationAsPdf()
        }

        // History
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

        // New Chat
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

        // Clear Chat
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

        // Voice Input
        buttonVoiceInput.setOnClickListener {

            if (!sendButton.isEnabled) {
                showWaitMessage()
                return@setOnClickListener
            }

            if (
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                startVoiceRecognition()
            } else {
                microphonePermissionLauncher.launch(
                    Manifest.permission.RECORD_AUDIO
                )
            }
        }

        // Send Message
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

                val replyPosition = messageList.size

                // Typing indicator
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

                        chatDao.insertMessage(
                            ChatEntity(
                                message = message,
                                isUser = true,
                                conversationId = requestConversationId
                            )
                        )

                        val aiResponse =
                            geminiHelper.generateResponse(message)

                        messageList[replyPosition] = ChatMessage(
                            message = aiResponse,
                            isUser = false
                        )

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

        // Keyboard Send action
        messageInput.setOnEditorActionListener { _, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendButton.performClick()
                true
            } else {
                false
            }
        }

        // Load conversation after listeners are ready
        loadConversation(currentConversationId)
    }

    // Speech Recognition
    private fun startVoiceRecognition() {

        try {

            val speechIntent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.getDefault().toString()
                )

                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Speak your message"
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    1
                )
            }

            voiceInputLauncher.launch(speechIntent)

        } catch (e: ActivityNotFoundException) {

            Toast.makeText(
                this,
                "Speech recognition is not available on this device.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Share conversation
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

                        val sender = if (chat.isUser) "You" else "Gemini AI"

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
                    putExtra(Intent.EXTRA_SUBJECT, "My AI Chat Conversation")
                    putExtra(Intent.EXTRA_TEXT, shareText)
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

    // Export PDF
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

    // Create PDF
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

            drawWrappedText("AI Chat Assistant", titlePaint)

            y += 5f

            drawWrappedText("Conversation Export", bodyPaint)

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

                val sender = if (chat.isUser) "You" else "Gemini AI"

                drawWrappedText(sender, senderPaint)
                drawWrappedText(chat.message, bodyPaint, 8f)

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

    // Save PDF
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

                    putExtra(Intent.EXTRA_STREAM, uri)

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

    // Load conversation
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
                    recyclerView.scrollToPosition(messageList.size - 1)
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

    // Enable or disable controls
    private fun setChatControlsEnabled(enabled: Boolean) {

        sendButton.isEnabled = enabled
        buttonNewChat.isEnabled = enabled
        buttonClearChat.isEnabled = enabled
        buttonHistory.isEnabled = enabled
        buttonShareChat.isEnabled = enabled
        buttonExportPdf.isEnabled = enabled
        buttonVoiceInput.isEnabled = enabled

        // Never disable the message input
        messageInput.isEnabled = true
        messageInput.isFocusable = true
        messageInput.isFocusableInTouchMode = true
        messageInput.isClickable = true

        messageInput.alpha = 1f
        sendButton.alpha = if (enabled) 1f else 0.5f
        buttonVoiceInput.alpha = if (enabled) 1f else 0.5f
    }

    // Wait message
    private fun showWaitMessage() {

        Toast.makeText(
            this,
            "Please wait until the current operation finishes.",
            Toast.LENGTH_SHORT
        ).show()
    }
}
