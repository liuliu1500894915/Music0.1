
package com.iven.musicplayergo.utils

import android.content.*
import android.media.AudioManager
import android.media.MediaRecorder
import com.iven.musicplayergo.models.ChatMessage
import com.iven.musicplayergo.wakeup.LogOutputHelper

object BluetoothScoHelper {
    private var isBtMicConnected = false

    fun init(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val state = intent?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                when (state) {
                    AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                        isBtMicConnected = true
                        LogOutputHelper.print("🎧 蓝牙麦克风已连接", ChatMessage.Sender.BOT)
                    }
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                        isBtMicConnected = false
                        LogOutputHelper.print("📴 蓝牙麦克风已断开", ChatMessage.Sender.BOT)
                    }
                }
            }
        }, filter)

        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.startBluetoothSco()
        am.isBluetoothScoOn = true
        LogOutputHelper.print("🔊 启动 Bluetooth SCO", ChatMessage.Sender.BOT)
    }

    fun getCurrentAudioSource(): Int {
        return if (isBtMicConnected) {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        } else {
            MediaRecorder.AudioSource.MIC
        }
    }
}
