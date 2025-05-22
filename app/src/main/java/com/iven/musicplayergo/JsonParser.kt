package com.iven.musicplayergo


import org.json.JSONArray
import org.json.JSONObject

/**
 * 简单解析讯飞 JSON 格式识别结果
 */
object JsonParser {
    fun parseIatResult(json: String): String {
        val result = StringBuilder()
        try {
            val joResult = JSONObject(json)
            val words = joResult.getJSONArray("ws")
            for (i in 0 until words.length()) {
                val ws = words.getJSONObject(i)
                val cwArray: JSONArray = ws.getJSONArray("cw")
                val cw = cwArray.getJSONObject(0)
                result.append(cw.getString("w"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result.toString()
    }
}
