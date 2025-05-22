package com.iven.musicplayergo.models

data class ChatMessage(
    val text: String,
    val sender: Sender
) {
    enum class Sender {
        USER,
        BOT
    }
}
