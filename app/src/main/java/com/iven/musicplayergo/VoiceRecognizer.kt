//package com.iven.musicplayergo
package com.iven.musicplayergo

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.iflytek.sparkchain.core.SparkChain
import com.iflytek.sparkchain.core.SparkChainConfig
import com.iflytek.sparkchain.core.asr.ASR
import com.iflytek.sparkchain.core.asr.AsrCallbacks
import com.iven.musicplayergo.models.ChatMessage
import com.iven.musicplayergo.utils.BluetoothScoHelper
import com.iven.musicplayergo.wakeup.LogOutputHelper

object VoiceRecognizer {

    private const val TAG = "VoiceRecognizer"
    private lateinit var mAsr: ASR
    private var isInitialized = false
    private var onRecognizeCallback: ((String) -> Unit)? = null

    class MyAsrCallbacks(private val onResult: (String) -> Unit) : AsrCallbacks {
        override fun onResult(result: ASR.ASRResult?, usrTag: Any?) {
            val text = result?.bestMatchText ?: ""
            Log.i(TAG, "[onResult] 🎤 识别结果：$text")
            onResult(text)
        }
        override fun onError(error: ASR.ASRError?, usrTag: Any?) {
            Log.e(TAG, "[onError] ❌ 识别失败：${error?.code}")
        }
        override fun onBeginOfSpeech() {
            Log.i(TAG, "[onBeginOfSpeech] 🗣️ 开始说话")
        }
        override fun onEndOfSpeech() {
            Log.i(TAG, "[onEndOfSpeech] 🛑 说话结束")
        }
    }

    fun init(context: Context) {
        if (isInitialized) return
        mAsr = ASR("zh_cn", "iat", "mandarin")
        mAsr.vinfo(true)
        mAsr.registerCallbacks(object : AsrCallbacks {
            override fun onResult(result: ASR.ASRResult?, usrTag: Any?) {
                val text = result?.bestMatchText ?: ""
                Log.i(TAG, "[VoiceRecognizer][speedtotext] 🎤 识别结果：$text")
                onRecognizeCallback?.invoke(text)
            }
            override fun onError(error: ASR.ASRError?, usrTag: Any?) {
                Log.e(TAG, "[VoiceRecognizer][speedtotext] ❌ 识别失败：${error?.code}")
            }
            override fun onBeginOfSpeech() {
                Log.i(TAG, "[VoiceRecognizer][speedtotext] 🗣️ 开始说话")
            }
            override fun onEndOfSpeech() {
                Log.i(TAG, "[VoiceRecognizer][speedtotext] 🛑 结束说话")
            }
        })
        isInitialized = true
        Log.i(TAG, "[VoiceRecognizer][init] ✅ 初始化完成")
    }

    fun startRecognize(context: Context, callback: (String) -> Unit) {
        if (!isInitialized) {
            Log.e(TAG, "[VoiceRecognizer][speedtotext] ❌ 未初始化，自动调用 init()")
            init(context)
        }
        onRecognizeCallback = callback
        val tag = System.currentTimeMillis().toString()
        val ret = mAsr.start(tag)
        if (ret != 0) {
            Log.e(TAG, "[VoiceRecognizer][speedtotext] ❌ 启动失败，ret=$ret")
            return
        }
        LogOutputHelper.print("▶ 启动语音识别", ChatMessage.Sender.BOT)
        startAudioCaptureAndFeed(context)
    }

    private fun startAudioCaptureAndFeed(context: Context) {
        Thread {
            try {
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

                val preferredSources = listOf(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.MIC
                )

                var audioRecord: AudioRecord? = null
                for (source in preferredSources) {
                    val ar = AudioRecord(source, sampleRate, channelConfig, audioFormat, minBufSize)
                    if (ar.state == AudioRecord.STATE_INITIALIZED) {
                        LogOutputHelper.print("✅ 使用音源 $source 初始化成功", ChatMessage.Sender.BOT)
                        audioRecord = ar
                        break
                    } else {
                        LogOutputHelper.print("❌ 音源 $source 初始化失败", ChatMessage.Sender.BOT)
                    }
                }

                if (audioRecord == null) {
                    LogOutputHelper.print("❌ 所有音源初始化失败，终止识别", ChatMessage.Sender.BOT)
                    return@Thread
                }

                audioRecord.startRecording()
                LogOutputHelper.print("🎙️ 开始录音", ChatMessage.Sender.BOT)

                val buffer = ByteArray(minBufSize)
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 5000) {
                    val len = audioRecord.read(buffer, 0, buffer.size)
                    if (len > 0) {
                        val data = buffer.copyOf(len)
                        mAsr.write(data)
                    }
                }

                audioRecord.stop()
                audioRecord.release()
                LogOutputHelper.print("🛑 停止录音", ChatMessage.Sender.BOT)
                mAsr.stop(true)
                LogOutputHelper.print("⏹️ 停止识别", ChatMessage.Sender.BOT)

            } catch (e: SecurityException) {
                Log.e(TAG, "[VoiceRecognizer][speedtotext] ❌ 缺少录音权限", e)
            } catch (e: Exception) {
                Log.e(TAG, "[VoiceRecognizer][speedtotext] ❌ 录音异常", e)
            }
        }.start()
    }
}
//import android.content.Context
//import android.media.AudioFormat
//import android.media.AudioRecord
//import android.media.MediaRecorder
//import android.util.Log
//import com.iflytek.sparkchain.core.SparkChain
//import com.iflytek.sparkchain.core.SparkChainConfig
//import com.iflytek.sparkchain.core.asr.ASR
//import com.iflytek.sparkchain.core.asr.AsrCallbacks
//import com.iven.musicplayergo.models.ChatMessage
//import com.iven.musicplayergo.wakeup.LogOutputHelper
//
//object VoiceRecognizer {
//
//    private const val TAG = "VoiceRecognizer"
//    private lateinit var mAsr: ASR
//    private var isInitialized = false
//
//    class MyAsrCallbacks(private val onResult: (String) -> Unit) : AsrCallbacks {
//
//        override fun onResult(result: ASR.ASRResult?, usrTag: Any?) {
//            val text = result?.bestMatchText ?: ""
//            Log.i("VoiceRecognizer", "[onResult] 🎤 识别结果：$text")
//            onResult(text)
//        }
//
//        override fun onError(error: ASR.ASRError?, usrTag: Any?) {
//            Log.e("VoiceRecognizer", "[onError] ❌ 识别失败：${error?.code}")
//        }
//
//        override fun onBeginOfSpeech() {
//            Log.i("VoiceRecognizer", "[onBeginOfSpeech] 🗣️ 开始说话")
//        }
//
//        override fun onEndOfSpeech() {
//            Log.i("VoiceRecognizer", "[onEndOfSpeech] 🛑 说话结束")
//        }
//    }
//    /** 初始化 ASR 引擎 */
//    fun init(context: Context) {
//        if (isInitialized) return
//
//        // ✅ 官方推荐初始化方式（构造器）
//        mAsr = ASR("zh_cn", "iat", "mandarin")
//        mAsr.vinfo(true)
//
//        // ✅ 注册识别回调
//        mAsr.registerCallbacks(object : AsrCallbacks {
//            override fun onResult(result: ASR.ASRResult?, usrTag: Any?) {
//                val text = result?.bestMatchText ?: ""
//                Log.i(TAG, "[VoiceRecognizer][speedtotext] 🎤 识别结果：$text")
//                onRecognizeCallback?.invoke(text)
//            }
//
//            override fun onError(error: ASR.ASRError?, usrTag: Any?) {
//                Log.e(TAG, "[VoiceRecognizer][speedtotext] ❌ 识别失败：${error?.code}")
//            }
//
//            override fun onBeginOfSpeech() {
//                Log.i(TAG, "[VoiceRecognizer][speedtotext] 🗣️ 开始说话")
//            }
//
//            override fun onEndOfSpeech() {
//                Log.i(TAG, "[VoiceRecognizer][speedtotext] 🛑 结束说话")
//            }
//        })
//
//        isInitialized = true
//        Log.i(TAG, "[VoiceRecognizer][init] ✅ 初始化完成（构造器方式）")
//    }
//    private var onRecognizeCallback: ((String) -> Unit)? = null
//
//    /** 开始识别（启动后内部调用 start + audio + stop） */
//    fun startRecognize(context: Context, callback: (String) -> Unit) {
//        if (!isInitialized) {
//            Log.e(TAG, "[VoiceRecognizer][speedtotext] ❌ 未初始化，自动调用 init()")
//            init(context ?: return) // 假设你存储了 context
//        }
//
//        onRecognizeCallback = callback
//        val tag = System.currentTimeMillis().toString()
//        val ret = mAsr.start(tag)
//        if (ret != 0) {
//            Log.e(TAG, "[VoiceRecognizer][speedtotext] ❌ 启动失败，ret=$ret")
//            return
//        }
//
////        Log.i(TAG, "[VoiceRecognizer][speedtotext] ▶ 启动识别")
////        LogOutputHelper.print("启动识别", ChatMessage.Sender.BOT)
////        LogOutputHelper.print("🤖 启动识别")
//
//
//        // 可用 AudioRecord 采集并写入：
//        startAudioCaptureAndFeed(context)
//    }
//
//    /** 示例：简化处理音频采集并 feed 到 ASR */
//    private fun startAudioCaptureAndFeed(context: Context) {
//        Thread {
//            try {
//                val sampleRate = 16000
//                val channelConfig = AudioFormat.CHANNEL_IN_MONO
//                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
//                val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
//                val audioRecord = AudioRecord(
//                    MediaRecorder.AudioSource.MIC,
//                    sampleRate,
//                    channelConfig,
//                    audioFormat,
//                    minBufSize
//                )
//
//                // ✅ 权限检查建议在调用 startRecognize() 时处理
//                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
//                    Log.e(TAG, "[VoiceRecognizer][speedtotext] ❌ AudioRecord 初始化失败")
//                    return@Thread
//                }
//
//                val buffer = ByteArray(minBufSize)
//                audioRecord.startRecording()
//                Log.i(TAG, "[VoiceRecognizer][speedtotext] 🎙️ 开始录音")
//                LogOutputHelper.print("🎙️ 有什么需要帮助的", ChatMessage.Sender.BOT)
//
//                val startTime = System.currentTimeMillis()
//                while (System.currentTimeMillis() - startTime < 5000) { // 采集 5 秒音频
//                    val len = audioRecord.read(buffer, 0, buffer.size)
//                    if (len > 0) {
//                        val data = buffer.copyOf(len)
//                        mAsr.write(data)
//                    }
//                }
//
//                audioRecord.stop()
//                audioRecord.release()
//                Log.i(TAG, "[VoiceRecognizer][speedtotext] 🛑 停止录音")
////                LogOutputHelper.print("🛑 停止录音")
//
//                mAsr.stop(true)
//                Log.i(TAG, "[VoiceRecognizer][speedtotext] ⏹️ 停止识别")
////                LogOutputHelper.print("⏹️ 停止识别")
//
//
//            } catch (e: SecurityException) {
//                Log.e(TAG, "[VoiceRecognizer][speedtotext] ❌ 缺少录音权限", e)
//            } catch (e: Exception) {
//                Log.e(TAG, "[VoiceRecognizer][speedtotext] ❌ 录音异常", e)
//            }
//        }.start()
//    }
//}
