package com.iven.musicplayergo

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.iflytek.aikit.core.*
import java.io.File
import android.content.Context
import com.iven.musicplayergo.models.ChatMessage
import com.iven.musicplayergo.utils.BluetoothScoHelper
import com.iven.musicplayergo.wakeup.LogOutputHelper
import java.util.*
import com.iven.musicplayergo.wakeup.WakeupAction
import kotlin.math.log10

private const val TAG = "WakeupLog"
private fun logD(msg: String) = Log.d(TAG, msg)
private fun logI(msg: String) = Log.i(TAG, msg)
private fun logE(msg: String) = Log.e(TAG, msg)
private var pendingKeyword: String? = null
// -----------------------------------------------------------------------------
// 1. WakeupManager – 单例封装
// -----------------------------------------------------------------------------
object WakeupIntegration {
    private lateinit var viewModel: MusicViewModel

    fun setViewModel(vm: MusicViewModel) {
        viewModel = vm
    }
    private const val ABILITY_IVW_ID = "e867a88f2"
    const val ACTION_WAKEUP_DETECTED = "com.iven.musicplayergo.ACTION_WAKEUP_DETECTED"
    private const val ACTION_WAKEUP_READY = "com.iven.musicplayergo.wakeup.READY"
    const val EXTRA_KEYWORD = "keyword"

    @Volatile private var initialized = false
    private var aiHandle: AiHandle? = null
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null

    // ---------------- 初始化 ----------------
    @MainThread
    fun ensureInitialized(
        ctx: Context,
        appId: String,
        apiKey: String,
        apiSecret: String,
        workDir: String
    ) {
        if (initialized) return

        logD("[init] ▶ START workDir=$workDir")
//        LogOutputHelper.print("[init] ▶ START workDir=$workDir")
        val dir = File(workDir)
        logD("[init] dir.exists=${dir.exists()} canRead=${dir.canRead()} canWrite=${dir.canWrite()}")
//        LogOutputHelper.print("[init] dir.exists=${dir.exists()} canRead=${dir.canRead()} canWrite=${dir.canWrite()}")
        /* ① 基础初始化 */
        val params = BaseLibrary.Params.builder()
            .appId(appId).apiKey(apiKey).apiSecret(apiSecret)
            .workDir(workDir).build()
        val helper = AiHelper.getInst()
        helper.initEntry(ctx.applicationContext, params)
        helper.registerListener(coreListener)

        /* ② 必须先“申请能力”——兼容不同版本命名 */
        var reqRet = -999
        var reqCalled = false
        runCatching {
            helper.javaClass.getMethod("requestAbility", Array<String>::class.java).also {
                reqRet = it.invoke(helper, arrayOf(ABILITY_IVW_ID)) as Int
                reqCalled = true
                logD("[init] ▶ requestAbility ret=$reqRet")
            }
        }.recoverCatching {
            helper.javaClass.getMethod("requireAbility", Array<String>::class.java).also {
                reqRet = it.invoke(helper, arrayOf(ABILITY_IVW_ID)) as Int
                reqCalled = true
                logD("[init] ▶ requireAbility ret=$reqRet")
            }
        }.recoverCatching {
            helper.javaClass.getMethod("applyAbility", Array<String>::class.java).also {
                reqRet = it.invoke(helper, arrayOf(ABILITY_IVW_ID)) as Int
                reqCalled = true
                logD("[init] ▶ applyAbility ret=$reqRet")
            }
        }

        if (!reqCalled) {
            logD("[init] ▶ 老版 SDK，无申请能力接口；默认已就绪")
            initialized = true

            AiHelper.getInst().registerListener(ABILITY_IVW_ID, ivwListener)

            /* 若先前已调用 startWakeup()，此处马上补触发 */
            pendingKeyword?.let { kw ->
                pendingKeyword = null
                logD("[init] ▶ 立即 re-startWakeup with $kw")
                startWakeup(ctx.applicationContext, kw)
            }
            return                                         // ★ 其余保持不动
        }

        /* ③ requestAbility 同步返回 0 ⇒ 直接认为鉴权成功 */
        if (reqRet == 0) {
            initialized = true
            logD("[init] ✅ AUTH 同步成功，pending=$pendingKeyword")

            helper.registerListener(ABILITY_IVW_ID, ivwListener)

            pendingKeyword?.let { kw ->
                pendingKeyword = null
                logD("[init] ▶ 立即 re-startWakeup with $kw")
                startWakeup(ctx.applicationContext, kw)
            }
        } else {
            logD("[init] ▶ 已发起能力申请，等待 CoreListener AUTH 回调 (ret=$reqRet)")
            // 大多数旧版会异步回调，这里什么也不做
        }
    }



    val isReady: Boolean
        get() = initialized

    // ---------------- 开启唤醒 ----------------
    @MainThread
    fun startWakeup(ctx: Context, keywordTxt: String) {
        logD("[startWakeup] ▶ called, initialized=$initialized, pending=$pendingKeyword")
        if (!initialized) {               // ← 还没准备好
            pendingKeyword = keywordTxt   // 先记下来
            logD("[startWakeup] ⏸ SDK 未就绪，缓存 pendingKeyword=$keywordTxt")
            return                        // 不再抛异常
        }
        logD("[start] ▶ 加载关键词资源 $keywordTxt")
        loadKeywordData(keywordTxt)

        // 构造与官方 Demo 一致的参数，重要：gramLoad 必须 true，否则 100007
        val paramReq = AiRequest.builder()
            .param("wdec_param_nCmThreshold", "0 0:800")
            .param("gramLoad", true)
            .build()

        val retHandle = AiHelper.getInst().start(ABILITY_IVW_ID, paramReq, null)
        logD("[start] ▶ start() handleCode=${retHandle.code}")
        if (retHandle.code != 0) {
            logE("[start] ❌ 会话创建失败 code=${retHandle.code}")
            return
        }
        aiHandle = retHandle
        logD("[start] ▶ 会话创建 handle=71")
        createRecorder()
    }

    // ---------------- 停止唤醒 ----------------
    @MainThread
    fun stopWakeup() {
        logD("[stop] ▶ 停止唤醒监听")
        recordThread?.interrupt(); recordThread = null
        audioRecord?.let { it.stop(); it.release() }
        audioRecord = null
        aiHandle?.let {
            AiHelper.getInst().write(
                AiRequest.builder().payload(AiAudio.get("wav").status(AiStatus.END).valid()).build(), it)
            AiHelper.getInst().end(it)
            aiHandle = null
        }
    }

    // ---------------- 资源检测 & 加载 ----------------
    private fun loadKeywordData(path: String) {
        val base = File(path).parentFile ?: return
        val files = listOf("keyword.txt", "IVW_KEYWORD_1", "IVW_GRAM_1", "IVW_FILLER_1")
        val expectedSize = mapOf(
            "IVW_FILLER_1" to 54886L,
            "IVW_KEYWORD_1" to 684L
        )
        files.forEach { name ->
            val f = File(base, name)
            logD("[resource] $name exist=${f.exists()} size=${if (f.exists()) f.length() else 0}")
            expectedSize[name]?.let { exp ->
                if (f.exists() && f.length() != exp) logE("[resource] ⚠ 大小异常：$name ${f.length()}B，期望 ${exp}B")
            }
        }
        val req = AiRequest.builder().customText("key_word", path, 0).build()
        val loadRet = AiHelper.getInst().loadData(ABILITY_IVW_ID, req)
        val specifyRet = AiHelper.getInst().specifyDataSet(ABILITY_IVW_ID, "key_word", intArrayOf(0))
        logD("[loadData] loadRet=$loadRet specifyRet=$specifyRet ✅ 完成")
    }

    // ---------------- 录音 & 写入 ----------------
    @SuppressLint("MissingPermission")
    private fun createRecorder() {
        val sr = 16000
        val frame = 1280
        val bufferSize = frame * 2

        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )

        for (source in sources) {
            try {
                val ar = AudioRecord(source, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                if (ar.state == AudioRecord.STATE_INITIALIZED) {
                    LogOutputHelper.print("✅ 使用音源 $source 初始化 AudioRecord 成功", ChatMessage.Sender.BOT)
                    audioRecord = ar
                    break
                } else {
                    LogOutputHelper.print("❌ 音源 $source 初始化失败", ChatMessage.Sender.BOT)
                    ar.release()
                }
            } catch (e: Exception) {
                LogOutputHelper.print("⚠️ 音源 $source 异常: ${e.message}", ChatMessage.Sender.BOT)
            }
        }

        if (audioRecord == null) {
            LogOutputHelper.print("❌ 所有音源初始化失败，无法开始录音", ChatMessage.Sender.BOT)
            return
        }

        audioRecord?.startRecording()
        LogOutputHelper.print("▶ AudioRecord 开始录音", ChatMessage.Sender.BOT)
        logD("▶ AudioRecord 开始录音")

        recordThread = Thread {
            val buf = ByteArray(frame)
            var first = true
            while (!Thread.currentThread().isInterrupted) {
                val len = audioRecord?.read(buf, 0, buf.size) ?: 0
                if (len > 0 && aiHandle != null) {
//                    LogOutputHelper.print("📥 读取音频字节数: $len", ChatMessage.Sender.BOT)

                    val status = if (first) AiStatus.BEGIN.also { first = false } else AiStatus.CONTINUE
                    val audio = AiAudio.get("wav").data(buf.copyOf(len)).status(status).valid()
                    val ret = AiHelper.getInst().write(AiRequest.builder().payload(audio).build(), aiHandle)
                    if (ret != 0) LogOutputHelper.print("❌ AiHelper.write 错误: $ret", ChatMessage.Sender.BOT)

                    Thread.sleep(40)
                }
            }
        }.apply { start() }
    }

    private fun calculateVolume(data: ByteArray, len: Int): Any {
        var sum = 0.0
        var i = 0
        while (i < len - 1) {
            val value = (data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xff)
            sum += value * value
            i += 2
        }
        val mean = sum / (len / 2)
        return if (mean > 0) (10 * log10(mean)).toInt() else 0
    }

    // ① 先把旧 listener 删除或注释，再粘贴下面这一段
    private val coreListener = CoreListener { type, code ->
        // 总是打印
        logD("[core] ▶ callback type=${type.name} code=$code")

        when {
            type == ErrType.AUTH && code == 0 -> {          // ✅ 鉴权成功
                initialized = true
                logD("[core] ✅ AUTH ok, pending=$pendingKeyword")

                pendingKeyword?.let { kw ->
                    pendingKeyword = null
                    logD("[core] ▶ re-startWakeup with $kw")
                    startWakeup(AiHelper.getInst().context ?: return@CoreListener, kw)
                }

                // 必须在鉴权成功后再注册能力-级 listener
                AiHelper.getInst().registerListener(ABILITY_IVW_ID, ivwListener)
            }
            type == ErrType.AUTH -> {                       // ❌ 鉴权失败
                logE("[core] ❌ AUTH fail code=$code — 检查 AppId / 网络 / 证书")
            }
        }
    }


    // ---------------- SDK 回调 ----------------
    private val ivwListener = object : AiListener {
        override fun onResult(handleID: Int, outputData: List<AiResponse>?, usrContext: Any?) {
            outputData?.forEach { resp ->
                val key = resp.key
                val result = String(resp.value)
                logD("[onResult] key=$key :: $result")
                if (key == "func_wake_up" || key == "func_pre_wakeup") {
                    logI("[onResult] 🎉 关键词命中 -> $result")
                    LogOutputHelper.print("🎉 你好小迪.", ChatMessage.Sender.USER)
                    LogOutputHelper.print("🎉 在呢.", ChatMessage.Sender.BOT)
                    val ctx = AiHelper.getInst().context ?: return
                    WakeupAction.init(ctx) // ✅ 传入 Context
                    WakeupAction.onWakeupDetected(ctx, result, viewModel)
                    ctx.sendBroadcast(Intent(ACTION_WAKEUP_DETECTED).putExtra(EXTRA_KEYWORD, result))
                }
            }
        }
        override fun onEvent(handleID: Int, event: Int, list: List<AiResponse>?, usrContext: Any?) {}
        override fun onError(handleID: Int, errCode: Int, msg: String?, usrContext: Any?) {
            logE("[onError] id=$handleID code=$errCode msg=$msg")
        }
    }


}

// -----------------------------------------------------------------------------
// 2. TestAction
// -----------------------------------------------------------------------------
//object TestAction {
//    fun onWakeupDetected(keyword: String) {
//        logI("[test] ✓ 唤醒调用成功 keyword=$keyword")
//    }
//}
// -----------------------------------------------------------------------------
// 3. WakeupService —— 前台常驻 Service
// -----------------------------------------------------------------------------
class WakeupService : Service() {

    companion object {
        private const val CHANNEL_ID = "WakeupChannel"
        private const val NOTIF_ID = 10086
        fun newIntent(ctx: Context) = Intent(ctx, WakeupService::class.java)
    }

    /** 监听播放器扫描完成 */
    private val readyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            logD("[service] ▶ 收到 ACTION_PLAYER_READY")
            Thread {                        // 后台线程，避免主线程 I/O 卡顿
                val path = "${getString(R.string.workDir)}/ivw/keyword.txt"
                WakeupIntegration.startWakeup(applicationContext, path)
                logD("[service] ▶ Thread END 调用 startWakeup() 完成")
//                LogOutputHelper.print("🤖离线唤醒准备完成")
//                LogOutputHelper.print("🤖离线唤醒准备完成", ChatMessage.Sender.BOT)
            }.start()
            LocalBroadcastManager.getInstance(this@WakeupService)
                .unregisterReceiver(this)   // 只接一次
        }
    }

    override fun onCreate() {
        super.onCreate()
        logD("[service] ▶ onCreate")
        // ① 启用蓝牙 SCO 通道
        BluetoothScoHelper.init(this)
        // ✅ 判断当前输入是否为蓝牙
        val isBtMic = BluetoothScoHelper.getCurrentAudioSource() == MediaRecorder.AudioSource.VOICE_COMMUNICATION
        if (!isBtMic) {
            Log.w("WakeupFix", "⚠️ 蓝牙耳机未作为输入，可能影响唤醒识别")
            LogOutputHelper.print("⚠️ 蓝牙耳机未作为输入，可能影响唤醒识别", ChatMessage.Sender.BOT)
        }else{
            LogOutputHelper.print("⚠️ 蓝牙耳机作为输入, ChatMessage.Sender.BOT)
        }

        // ② 启动前台服务
        startForeground(NOTIF_ID, buildNotification())

        // ③ 初始化唤醒
        WakeupIntegration.ensureInitialized(
            this,
            getString(R.string.appId),
            getString(R.string.apiKey),
            getString(R.string.apiSecret),
            getString(R.string.workDir)
        )

        /* ③ 等播放器扫描完再启动唤醒，避免 I/O 竞争 */
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(readyReceiver, IntentFilter("ACTION_PLAYER_READY"))
    }

    override fun onDestroy() {
        WakeupIntegration.stopWakeup()          // 如需停止监听
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 简单通知，可按需美化 */
    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, "语音唤醒",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("语音唤醒运行中")
            .setContentText("后台监听关键词…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

}
