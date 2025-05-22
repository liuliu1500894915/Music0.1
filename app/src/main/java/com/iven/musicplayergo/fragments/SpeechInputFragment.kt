package com.iven.musicplayergo.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.iven.musicplayergo.R
import com.iven.musicplayergo.xf.WavAudioRecorder
import com.iven.musicplayergo.xf.XfWebSocketClient
import java.io.File

class SpeechInputFragment : Fragment() {

    private lateinit var editText: EditText
    private lateinit var recordButton: Button
    private lateinit var recorder: WavAudioRecorder
    private lateinit var wsClient: XfWebSocketClient
    private var pcmFile: File? = null

    // ✅ 替换为你自己的讯飞账号信息
    private val appId = "ee0dcf57"
    private val apiKey = "c11f2888aae37e6059b4de777907fffd"
    private val apiSecret = "NzhmYTljNjNmMGU5OTA0YmM2ZDg2YjZm"

    // ✅ 动态申请麦克风权限
    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            Toast.makeText(requireContext(), "请授权麦克风权限", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_speech_input, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        editText = view.findViewById(R.id.edit_text_result)
        recordButton = view.findViewById(R.id.button_start_speech)

        recorder = WavAudioRecorder(requireContext())
        wsClient = XfWebSocketClient(
            appId = appId,
            apiKey = apiKey,
            apiSecret = apiSecret,
            onResult = { text ->
                requireActivity().runOnUiThread {
                    editText.append(text)
                }
            },
            onError = { error ->
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "识别失败：$error", Toast.LENGTH_SHORT).show()
                }
            }
        )

        // ✅ 长按开始录音，松开结束识别
        recordButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        startRecording()
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    recordButton.text = "🎤 开始录音"
                    recorder.stop()
                    pcmFile?.let { wsClient.startStreaming(it) }
                    recordButton.performClick()
                }
            }
            true
        }

        // ✅ 无障碍辅助点击声明
        recordButton.setOnClickListener { /* nothing */ }
    }

    // ✅ 录音逻辑抽出，避免重复
    private fun startRecording() {
        recordButton.text = "🎤 录音中..."
        pcmFile = recorder.start()
    }
}
