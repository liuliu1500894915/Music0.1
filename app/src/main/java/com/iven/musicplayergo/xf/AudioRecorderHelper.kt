package com.iven.musicplayergo.xf

import android.content.Context
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AudioRecorderHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFilePath: String? = null

    fun startRecording(): String {
        val outputDir = context.getExternalFilesDir("recordings")
        if (outputDir != null && !outputDir.exists()) {
            outputDir.mkdirs()
        }

        val fileName = "record_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
        val outputFile = File(outputDir, fileName)

        outputFilePath = outputFile.absolutePath

        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)

                // ✅ 使用兼容性更高的 AAC + MPEG_4 格式
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96000)
                setAudioSamplingRate(16000)

                setOutputFile(outputFilePath)
                prepare()

                // ✅ 延迟启动，避免权限刚授予时失败
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        start()
                        Log.i("AudioRecorderHelper", "开始录音: $outputFilePath")
                    } catch (e: Exception) {
                        Log.e("AudioRecorderHelper", "start()失败: ${e.message}")
                        throw RuntimeException("录音无法启动: ${e.message}")
                    }
                }, 300)
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "录音初始化失败: ${e.message}")
            throw RuntimeException("无法开始录音: ${e.message}")
        }

        return outputFilePath!!
    }

    fun stopRecording(): String? {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "录音停止失败: ${e.message}")
        }
        mediaRecorder = null
        return outputFilePath
    }
}
