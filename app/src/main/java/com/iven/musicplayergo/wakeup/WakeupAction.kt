package com.iven.musicplayergo.wakeup

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.EditText
import com.hjq.permissions.XXPermissions
import com.iflytek.sparkchain.core.SparkChain
import com.iflytek.sparkchain.core.SparkChainConfig
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.MusicViewModel
import java.util.Locale
import com.iven.musicplayergo.VoiceRecognizer
import com.iven.musicplayergo.models.ChatMessage
import com.iven.musicplayergo.player.MediaPlayerHolder
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException


object WakeupAction {
    private var alreadyHandled = false
    private const val TAG = "WakeupLog"
    private fun logI(msg: String) = Log.i(TAG, msg)
    private var tts: TextToSpeech? = null
    private var isTTSReady = false
    private var pendingSpeak: String? = null
    val sessionId = "abc123"
    fun init(context: Context) {
        val config = SparkChainConfig.builder()
            .appID("ee0dcf57")
            .apiKey("c11f2888aae37e6059b4de777907fffd")
            .apiSecret("NzhmYTljNjNmMGU5OTA0YmM2ZDg2YjZm")
        val sdkRet = SparkChain.getInst().init(context.applicationContext, config)
        if (sdkRet != 0) {
            LogOutputHelper.print("❌ SparkChain SDK 初始化失败，错误码：$sdkRet", ChatMessage.Sender.BOT)
            Log.e(TAG, "[VoiceRecognizer][init] ❌ SparkChain SDK 初始化失败，错误码：$sdkRet")
            return
        } else {
            Log.i(TAG, "✅ SparkChain SDK 初始化成功")
//            LogOutputHelper.print("✅ SparkChain SDK 初始化成功", ChatMessage.Sender.BOT)
        }
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            isTTSReady = (status == TextToSpeech.SUCCESS)
            logI("[TTS] 初始化成功 isTTSReady=$isTTSReady")

            if (isTTSReady) {
                tts?.language = Locale.CHINA
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        logI("[TTS] 开始播报 utteranceId=$utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        logI("[TTS] 播报完成 utteranceId=$utteranceId")
                    }

                    override fun onError(utteranceId: String?) {
                        logI("[TTS] 播报出错 utteranceId=$utteranceId")
                    }
                })

                pendingSpeak?.let {
                    logI("[TTS] 发音请求(延迟): $it")
                    tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "wakeup_response")
                    pendingSpeak = null
                }
            }
        }
    }

private fun playSongByName(songName: String, viewModel: MusicViewModel) {
    Log.w("speedtotext", "✅ 根据歌曲名播放指定歌曲: $songName")
    LogOutputHelper.print("✅ 准备播放: $songName", ChatMessage.Sender.BOT)

    if (songName.isNullOrBlank()) {
        Log.w("WakeupLog", "⚠️ 无效的歌曲名：$songName")
//        LogOutputHelper.print("⚠️ 无效的歌曲名：$songName", ChatMessage.Sender.BOT)
        return
    }
    val matched = viewModel.deviceMusicFiltered?.firstOrNull {
        it.title?.contains(songName, ignoreCase = true) == true ||
                it.displayName?.contains(songName, ignoreCase = true) == true
    }

    if (matched != null) {
        val holder = MediaPlayerHolder.getInstance()
        holder.updateCurrentSong(matched, listOf(matched), GoConstants.ARTIST_VIEW)
        holder.initMediaPlayer(matched, forceReset = true)
        holder.resumeMediaPlayer()
        Log.i("speedtotext", "🎶 播放指定歌曲：${matched.title}")
        LogOutputHelper.print("🎶 播放：${matched.title}", ChatMessage.Sender.BOT)
    } else {
        Log.w("speedtotext", "⚠️ 未找到匹配歌曲: $songName")
        LogOutputHelper.print("⚠️ 未找到匹配歌曲: $songName", ChatMessage.Sender.BOT)
    }
}
    private fun handleIntent(intent: String, song: String?, viewModel: MusicViewModel) {
        val holder = MediaPlayerHolder.getInstance()
        when (intent) {
            "pause"    -> {
                holder.pauseMediaPlayer()
                LogOutputHelper.print("✅ 歌曲暂停", ChatMessage.Sender.BOT)
            }
            "play" -> {
                if (!song.isNullOrBlank()) {
                    playSongByName(song,viewModel)
                    Log.w("speedtotext", "✅ 准备播放指定歌曲: $song")
//                    LogOutputHelper.print("✅ 开始播放: $song", ChatMessage.Sender.BOT)

                } else {
                    holder.resumeMediaPlayer()
                }
            }
            "next"     -> {
                holder.skip(isNext = true)
                LogOutputHelper.print("✅ 下一首", ChatMessage.Sender.BOT)
            }
            "prev" -> {
                holder.skip(isNext = false)
                LogOutputHelper.print("✅ 上一首", ChatMessage.Sender.BOT)
            }
            else  -> LogOutputHelper.print(" ❓ 未识别指令: $intent")

        }
    }
    fun onWakeupDetected(context: Context, keyword: String, viewModel: MusicViewModel) {
        logI("[speedtotext] 唤醒成功，keyword=$keyword")
//        LogOutputHelper.print("🤖 唤醒成功")

//        if (alreadyHandled) return
//        alreadyHandled = true

        VoiceRecognizer.startRecognize(context) { text ->
            if (!alreadyHandled && text.endsWith("。") || text.endsWith(".") || text.endsWith("？")
                || text.endsWith("?") || text.endsWith("！") || text.endsWith("!")) {

                Log.i("speedtotext", "🎉 识别完成：$text")
                LogOutputHelper.print("🎉 $text", ChatMessage.Sender.USER)

                JsonPostHelper.postTextToServer(sessionId, text, onResult = { resp ->
                    // 这里是解耦后的逻辑层
                    Log.i("speedtotext", "🤖 解析结果: intent=${resp.intent}, song=${resp.song}, expectMore=${resp.expectMore}")
//                    LogOutputHelper.print("🤖 解析结果: intent=${resp.intent}, song=${resp.song}")

                    handleIntent(resp.intent, resp.song, viewModel)
                }, onError = {
                    Log.e("speedtotext", "网络或解析出错: ${it.message}")
                })

                // 可选：3 秒后解锁
                Handler(Looper.getMainLooper()).postDelayed({ alreadyHandled = false }, 3000)
            }
        }
    }

}


data class ResponseData(
        val status: String,
        val sessionId: String,
        val intent: String,
        val song: String?,
        val expectMore: Boolean
    )

    object JsonPostHelper {
        private val client = OkHttpClient()

        fun postTextToServer(
            sessionId: String,
            text: String,
            onResult: (ResponseData) -> Unit,
            onError: ((Throwable) -> Unit)? = null
        ) {
            val url = "http://114.132.160.64:8001/asr"
            Log.i("speedtotext", "✅ 开始请求FastAPI 文本=$text")
//            LogOutputHelper.print("✅ 开始请求FastAPI 文本=$text")

            val json = JSONObject().apply {
                put("session_id", sessionId)
                put("text", text)
            }
            val body = RequestBody.create(
                "application/json; charset=utf-8".toMediaTypeOrNull(),
                json.toString()
            )
            val request = Request.Builder().url(url).post(body).build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("speedtotext", "❌ 请求失败：${e.message}")
                    onError?.invoke(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    response.body?.string()?.let { rawJson ->
                        Log.i("speedtotext", "✅ 后端原始响应: $rawJson")
//                        LogOutputHelper.print("✅ 后端原始响应: $rawJson")
                        try {
                            val o = JSONObject(rawJson)
                            val data = ResponseData(
                                status    = o.getString("status"),
                                sessionId = o.getString("session_id"),
                                intent    = o.getString("intent"),
                                song      = if (o.isNull("song")) null else o.getString("song"),
                                expectMore= o.getBoolean("expect_more")
                            )
                            onResult(data)
                        } catch (ex: Exception) {
                            Log.e("speedtotext", "❌ JSON 解析失败：${ex.message}")
                            onError?.invoke(ex)
                        }
                    } ?: onError?.invoke(NullPointerException("Response body is null"))
                }
            })
        }
    }


