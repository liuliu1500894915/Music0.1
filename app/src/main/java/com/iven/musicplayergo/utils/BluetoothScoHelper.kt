
package com.iven.musicplayergo.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.*
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.iven.musicplayergo.models.ChatMessage
import com.iven.musicplayergo.wakeup.LogOutputHelper

object BluetoothScoHelper {
    private var isBtMicConnected = false

    fun init(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val btFilter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
        context.registerReceiver(object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val device = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device?.bluetoothClass?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
                    || device?.bluetoothClass?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET) {

                    LogOutputHelper.print("📶 蓝牙耳机连接：${device.name}", ChatMessage.Sender.BOT)

                    // ✅ 检查当前是否已建立 SCO，否则启动
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    if (!am.isBluetoothScoOn) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            am.mode = AudioManager.MODE_IN_COMMUNICATION
                            am.startBluetoothSco()
                            am.isBluetoothScoOn = true
                            LogOutputHelper.print("🎙️ 启动 SCO 通道", ChatMessage.Sender.BOT)
                        }, 500)
                    }
                }
            }
        }, btFilter)

        am.mode = AudioManager.MODE_IN_COMMUNICATION

        LogOutputHelper.print("🔊 尝试建立 SCO 音频通道...", ChatMessage.Sender.BOT)
        Handler(Looper.getMainLooper()).postDelayed({
            am.startBluetoothSco()
            am.isBluetoothScoOn = true
        }, 500) // ⚠️ 加个延迟避免系统未准备好
    }


    fun getCurrentAudioSource(): Int {
        return if (isBtMicConnected) {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        } else {
            MediaRecorder.AudioSource.MIC
        }
    }
}
