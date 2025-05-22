package com.iven.musicplayergo

import android.content.Context
import java.io.File

class AssetCopier {
    object AssetCopier {
        fun copyIfNeeded(ctx: Context, dstDir: String) {
            val dst = File(dstDir)
            if (dst.exists()) return      // 已复制过，跳过

            dst.mkdirs()
            ctx.assets.list("resource")!!.forEach { name ->
                ctx.assets.open("resource/$name").use { input ->
                    File(dst, name).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

}
