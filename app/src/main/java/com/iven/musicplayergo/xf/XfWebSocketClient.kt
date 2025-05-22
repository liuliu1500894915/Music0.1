package com.iven.musicplayergo.xf

import android.util.Base64
import android.util.Log
import okhttp3.*
import okio.ByteString
import java.net.URLEncoder
import java.security.SignatureException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.fixedRateTimer
import java.io.File
import java.io.FileInputStream

class XfWebSocketClient(
    private val appId: String,
    private val apiKey: String,
    private val apiSecret: String,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var ws: WebSocket? = null
    private var timer: Timer? = null
    private val TAG = "XfWebSocketClient"

    fun startStreaming(pcmFile: File) {
        val url = createUrl()
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val initJson = """
                    {
                      "common": {"app_id": "$appId"},
                      "business": {
                        "domain": "iat",
                        "language": "zh_cn",
                        "accent": "mandarin",
                        "vad_eos": 5000
                      },
                      "data": {
                        "status": 0,
                        "format": "audio/L16;rate=16000",
                        "encoding": "raw",
                        "audio": ""
                      }
                    }
                """.trimIndent()
                webSocket.send(initJson)

                // send data
                val input = FileInputStream(pcmFile)
                val buffer = ByteArray(1280)
                timer = fixedRateTimer("pcm-sender", false, 100, 40) {
                    val len = input.read(buffer)
                    if (len > 0) {
                        val audio = Base64.encodeToString(buffer.copyOf(len), Base64.NO_WRAP)
                        val msg = """{"data":{"status":1,"format":"audio/L16;rate=16000","encoding":"raw","audio":"$audio"}}"""
                        webSocket.send(msg)
                    } else {
                        val stopMsg = """{"data":{"status":2,"audio":"","format":"audio/L16;rate=16000","encoding":"raw"}}"""
                        webSocket.send(stopMsg)
                        input.close()
                        this.cancel()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val regex = """"w":"(.*?)"""".toRegex()
                val words = regex.findAll(text).joinToString("") { it.groupValues[1] }
                if (words.isNotEmpty()) onResult(words)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onError("WebSocket失败: ${t.message}")
            }
        })
    }

    private fun createUrl(): String {
        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.format(Date())

        val signatureOrigin = "host: iat-api.xfyun.cn\ndate: $date\nGET /v2/iat HTTP/1.1"
        val signatureSha = hmacSha256(signatureOrigin, apiSecret)
        val signatureBase64 = Base64.encodeToString(signatureSha, Base64.NO_WRAP)

        val auth = """api_key="$apiKey", algorithm="hmac-sha256", headers="host date request-line", signature="$signatureBase64""""
        val authBase64 = Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)

        return "wss://iat-api.xfyun.cn/v2/iat?authorization=${URLEncoder.encode(authBase64, "UTF-8")}&date=${URLEncoder.encode(date, "UTF-8")}&host=iat-api.xfyun.cn"
    }

    private fun hmacSha256(data: String, key: String): ByteArray {
        val secretKey = SecretKeySpec(key.toByteArray(), "HmacSHA256")
        return Mac.getInstance("HmacSHA256").apply {
            init(secretKey)
        }.doFinal(data.toByteArray())
    }
}
