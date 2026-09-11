package com.bilidebug.app

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局日志 + 事件广播。
 *
 * - 所有日志集中存在内存里（上限 500 条），只在二级页面「运行日志」里展示；
 * - 每次操作的**结果**用 [notify] 发送应用内广播，当前前台页面收到后弹 Toast，
 *   做到「界面不堆日志、结果即时可见」。
 */
object LogBus {

    const val ACTION = "com.bilidebug.app.LOG_EVENT"
    const val EXTRA_MSG = "msg"
    const val EXTRA_TOAST = "toast"
    const val EXTRA_OK = "ok"

    private const val MAX = 500

    private val entries = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var lastToastMsg: String? = null
    private var lastToastAt = 0L

    /**
     * 是否应展示这条结果提示。多个页面可能同时收到同一条广播（返回栈未销毁的页面），
     * 这里做全局去重，保证同一条结果只弹一次。
     */
    @Synchronized
    fun shouldToast(msg: String): Boolean {
        val now = System.currentTimeMillis()
        if (msg == lastToastMsg && now - lastToastAt < 800) return false
        lastToastMsg = msg
        lastToastAt = now
        return true
    }

    /** 只记日志（过程信息） */
    fun log(context: Context, msg: String) = record(context, msg, toast = false, ok = true)

    /** 记日志 + 广播提示（成功/失败结果） */
    fun notify(context: Context, msg: String, ok: Boolean) = record(context, msg, toast = true, ok = ok)

    @Synchronized
    fun all(): List<String> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()

    private fun record(context: Context, msg: String, toast: Boolean, ok: Boolean) {
        synchronized(this) {
            entries.addLast("${fmt.format(Date())}  $msg")
            while (entries.size > MAX) entries.removeFirst()
        }
        val i = Intent(ACTION)
            .setPackage(context.packageName)
            .putExtra(EXTRA_MSG, msg)
            .putExtra(EXTRA_TOAST, toast)
            .putExtra(EXTRA_OK, ok)
        context.sendBroadcast(i)
    }
}
