package com.iven.musicplayergo.xf

import android.util.Base64
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class XfIatHttpRecognizer(
    private val appId: String,
    private val apiKey: String,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private val TAG = "XfIatHttpRecognizer"
    private val url = "https://api.xfyun.cn/v1/service/v1/iat"

    fun recognize(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            onError("音频文件不存在")
            return
        }

        val fileBytes = FileInputStream(file).readBytes()
        val audioBase64 = Base64.encodeToString(fileBytes, Base64.NO_WRAP)

        val curTime = (System.currentTimeMillis() / 1000).toString()

        val paramJson = JSONObject(
            mapOf(
                "engine_type" to "sms16k",
                "aue" to "raw"
            )
        ).toString()

        val paramBase64 = Base64.encodeToString(paramJson.toByteArray(), Base64.NO_WRAP)
        val checkSum = md5(apiKey + curTime + paramBase64)

        val request = Request.Builder()
            .url(url)
            .addHeader("X-Appid", appId)
            .addHeader("X-CurTime", curTime)
            .addHeader("X-Param", paramBase64)
            .addHeader("X-CheckSum", checkSum)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(
                FormBody.Builder()
                    .add("audio", audioBase64)
                    .build()
            )
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                onError("请求失败: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string() ?: return onError("无响应内容")
                Log.d(TAG, "响应结果: $result")

                try {
                    val json = JSONObject(result)
                    val code = json.optInt("code", -1)
                    val desc = json.optString("desc", "未知错误")

                    if (code == 0) {
                        val data = json.optJSONObject("data")
                        val text = data?.optString("result") ?: "(无识别结果)"
                        onResult(text)
                    } else {
                        onError("识别失败（$code）: $desc")
                    }
                } catch (e: Exception) {
                    onError("响应解析失败: ${e.message}")
                }
            }

        })
    }

    private fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
