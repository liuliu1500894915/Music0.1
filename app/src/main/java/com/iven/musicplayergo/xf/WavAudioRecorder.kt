package com.iven.musicplayergo.xf

import android.content.Context
import android.media.*
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import java.io.File
import java.io.FileOutputStream

class WavAudioRecorder(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var pcmFile: File? = null

    private val sampleRate = 16000
    private val channel = AudioFormat.CHANNEL_IN_MONO
    private val format = AudioFormat.ENCODING_PCM_16BIT

    fun start(): File {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("录音权限未授予")
        }

        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channel, format)
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channel, format, minBuffer)

        val pcmDir = File(context.getExternalFilesDir("pcm"), "")
        if (!pcmDir.exists()) pcmDir.mkdirs()
        pcmFile = File(pcmDir, "rec_${System.currentTimeMillis()}.pcm")
        val out = FileOutputStream(pcmFile)

        isRecording = true
        audioRecord?.startRecording()

        Thread {
            val buffer = ByteArray(minBuffer)
            while (isRecording) {
                val len = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (len > 0) out.write(buffer, 0, len)
            }
            out.flush(); out.close()
        }.start()

        return pcmFile!!
    }

    fun stop() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("WavAudioRecorder", "stop error: ${e.message}")
        }
    }

    fun getFile(): File? = pcmFile
}
