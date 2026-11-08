# AI Chat Assistant Android App

An Android-based AI Chat Assistant built using **Kotlin, Firebase Gemini AI, and Room Database**. The application allows users to interact with an AI assistant, manage multiple conversations, search chat history, and export conversations as PDF files.

## Features

- **AI-Powered Chat:** Generate responses using Firebase Gemini AI.
- **Multiple Conversations:** Create and manage separate chat conversations.
- **Chat History:** View and reopen previously saved conversations.
- **Local Data Storage:** Store conversations and messages using Room Database.
- **Search Chat History:** Search across saved conversations and messages.
- **Delete Conversations:** Delete individual conversations.
- **Copy & Share Messages:** Copy or share individual AI and user messages.
- **Share Full Conversation:** Share an entire conversation.
- **PDF Export:** Export conversations as PDF files.
- **Timestamps:** Display message timestamps.
- **Error Handling:** Handle internet connectivity and AI response errors.

## Tech Stack

- **Language:** Kotlin
- **Platform:** Android
- **IDE:** Android Studio
- **AI Integration:** Firebase AI Logic (Gemini)
- **Database:** Room Database
- **UI:** XML Layouts, RecyclerView
- **Architecture Components:** Android Lifecycle and Coroutines
- **Version Control:** Git & GitHub

## Project Structure

```text
AI-Chat-Assistant-Android/
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/example/aichatassistantproject/
│           │       ├── MainActivity.kt
│           │       ├── GeminiHelper.kt
│           │       ├── ChatAdapter.kt
│           │       ├── ChatHistoryActivity.kt
│           │       ├── ChatHistoryAdapter.kt
│           │       ├── ChatDatabase.kt
│           │       ├── ChatDao.kt
│           │       └── ChatEntity.kt
│           └── res/
│               └── layout/
├── build.gradle.kts
└── settings.gradle.kts
```

## Setup Instructions

1. Clone this repository:

   ```bash
   git clone https://github.com/Kirtisharma123/AI-Chat-Assistant-Android.git
   ```

2. Open the project in Android Studio.

3. Configure Firebase for your Android application and register your own Firebase Android app.

4. Add your Firebase configuration file (`google-services.json`) inside the `app/` directory.

5. Enable Firebase AI Logic and configure the appropriate App Check provider.

6. Sync Gradle and run the application on an Android device or emulator.

## Security Note

The Firebase configuration file is excluded from this repository using `.gitignore`. Configure your own Firebase project before running the application.

## Future Improvements

- Improve UI/UX design.
- Add user authentication.
- Add voice input and output.
- Implement additional AI-powered features.

## App Screenshots

### 1. Main Chat Screen
![Main Chat Screen](<img width="702" height="1600" alt="chat interface" src="https://github.com/user-attachments/assets/c9639930-2789-44ae-9d41-c5bd2d89e5e9" />
)

### 2. Chat History
![Chat History](screenshots/chat-history.png)

### 3. Search Chat History
![Search Chat History](screenshots/search-history.png)

### 4. PDF Export
![PDF Export](screenshots/pdf-export.png)

## Author

**Kirti Sharma**

[GitHub Profile](https://github.com/Kirtisharma123)
