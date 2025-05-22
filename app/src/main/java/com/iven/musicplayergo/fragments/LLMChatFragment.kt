package com.iven.musicplayergo.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.MusicViewModel
import com.iven.musicplayergo.databinding.FragmentLlmChatBinding
import com.iven.musicplayergo.models.ChatMessage
import com.iven.musicplayergo.player.MediaPlayerHolder
import com.iven.musicplayergo.ui.ChatAdapter
import com.iven.musicplayergo.wakeup.LogOutputHelper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LLMChatFragment : Fragment() {

    private var _binding: FragmentLlmChatBinding? = null
    private val binding get() = _binding!!

    private val sessionId = "chat-session-001"
    private val serverUrl = "http://114.132.160.64:8001/asr" // ✅ 修改为你的 FastAPI 地址

    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLlmChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatAdapter = ChatAdapter(chatMessages)
        binding.recyclerChat.adapter = chatAdapter
        binding.recyclerChat.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerChat.setHasFixedSize(true)

        binding.buttonSend.setOnClickListener {
            val question = binding.editTextQuestion.text.toString().trim()
            if (question.isNotEmpty()) {
                addMessage(question, ChatMessage.Sender.USER)
                sendQuestionToServer(question)
                binding.editTextQuestion.text.clear()
            }
        }
        LogOutputHelper.setPrinter { msg, sender ->
            requireActivity().runOnUiThread {
                addMessage("🪵 $msg", sender)
            }
        }
    }

    private fun sendQuestionToServer(question: String) {
        val json = JSONObject().apply {
            put("session_id", sessionId)
            put("text", question)
        }

        val requestBody = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    addMessage("❌ 网络请求失败: ${e.message}", ChatMessage.Sender.BOT)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body?.string()
                try {
                    val responseJson = JSONObject(responseText)
                    val intent = responseJson.optString("intent")

                    requireActivity().runOnUiThread {
                        addMessage(intent, ChatMessage.Sender.BOT)
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        addMessage("❌ JSON解析失败：${e.message}", ChatMessage.Sender.BOT)
                    }
                }
            }
        })
    }

    private fun addMessage(text: String, sender: ChatMessage.Sender) {
        chatMessages.add(ChatMessage(text, sender))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        binding.recyclerChat.scrollToPosition(chatMessages.size - 1)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
