package com.iven.musicplayergo.xf

import java.io.*

object WavFileUtil {

    fun convertPcmToWav(pcmFile: File, wavFile: File, sampleRate: Int = 16000, channels: Int = 1, bitsPerSample: Int = 16) {
        val pcmSize = pcmFile.length().toInt()
        val header = createWavHeader(pcmSize, sampleRate, channels, bitsPerSample)

        val outputStream = BufferedOutputStream(FileOutputStream(wavFile))
        outputStream.write(header)

        val buffer = ByteArray(1024)
        val inputStream = BufferedInputStream(FileInputStream(pcmFile))
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }

        inputStream.close()
        outputStream.flush()
        outputStream.close()
    }

    private fun createWavHeader(pcmSize: Int, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val totalDataLen = pcmSize + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        return ByteArray(44).apply {
            writeBytes("RIFF".toByteArray(), 0)
            writeIntLE(totalDataLen, 4)
            writeBytes("WAVE".toByteArray(), 8)
            writeBytes("fmt ".toByteArray(), 12)
            writeIntLE(16, 16)
            writeShortLE(1, 20) // PCM
            writeShortLE(channels.toShort(), 22)
            writeIntLE(sampleRate, 24)
            writeIntLE(byteRate, 28)
            writeShortLE((channels * bitsPerSample / 8).toShort(), 32)
            writeShortLE(bitsPerSample.toShort(), 34)
            writeBytes("data".toByteArray(), 36)
            writeIntLE(pcmSize, 40)
        }
    }

    private fun ByteArray.writeBytes(data: ByteArray, offset: Int) {
        System.arraycopy(data, 0, this, offset, data.size)
    }

    private fun ByteArray.writeIntLE(value: Int, offset: Int) {
        this[offset] = (value and 0xff).toByte()
        this[offset + 1] = ((value shr 8) and 0xff).toByte()
        this[offset + 2] = ((value shr 16) and 0xff).toByte()
        this[offset + 3] = ((value shr 24) and 0xff).toByte()
    }

    private fun ByteArray.writeShortLE(value: Short, offset: Int) {
        this[offset] = (value.toInt() and 0xff).toByte()
        this[offset + 1] = ((value.toInt() shr 8) and 0xff).toByte()
    }
}
