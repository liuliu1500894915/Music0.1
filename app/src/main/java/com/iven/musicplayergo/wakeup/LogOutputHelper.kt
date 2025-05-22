package com.iven.musicplayergo.wakeup

import com.iven.musicplayergo.models.ChatMessage

object LogOutputHelper {
    private var printer: ((String, ChatMessage.Sender) -> Unit)? = null

    fun setPrinter(p: (String, ChatMessage.Sender) -> Unit) {
        printer = p
    }

    fun print(text: String, sender: ChatMessage.Sender = ChatMessage.Sender.BOT) {
        printer?.invoke(text, sender) ?: run {
            val tag = if (sender == ChatMessage.Sender.USER) "🧑 USER" else "🤖 BOT"
            android.util.Log.i("LogOutputHelper", "[$tag] $text")
        }
    }
}
