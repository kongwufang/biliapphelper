package com.bilidebug.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle

/**
 * 所有页面的基类：统一日志、结果广播、极验与短信交互。
 * 子类只需关注业务；过程中产生的日志会进二级日志页，结果会 Toast 提示。
 */
abstract class BaseActivity : AppCompatActivity(), LoginListener {

    private var pendingGeetest: ((String?) -> Unit)? = null
    private var pendingSms: ((String?) -> Unit)? = null

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            // 只有当前可见（RESUMED）的页面才响应，避免返回栈里的页面重复处理同一条广播
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            val msg = intent.getStringExtra(LogBus.EXTRA_MSG) ?: return
            val wantToast = intent.getBooleanExtra(LogBus.EXTRA_TOAST, false)
            val ok = intent.getBooleanExtra(LogBus.EXTRA_OK, true)
            // 全局去重：同一条结果只提示一次
            if (wantToast && LogBus.shouldToast(msg)) {
                Toast.makeText(this@BaseActivity, msg, Toast.LENGTH_SHORT).show()
            }
            onLogEvent(msg, ok)
        }
    }

    override fun onStart() {
        super.onStart()
        // 只在页面可见期间接收广播：返回栈里多个 Activity 同时注册会导致同一条消息被提示多次
        ContextCompat.registerReceiver(
            this, logReceiver, IntentFilter(LogBus.ACTION), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        try {
            unregisterReceiver(logReceiver)
        } catch (_: Exception) {
        }
        super.onStop()
    }

    /** 子类可覆盖：收到结果事件后刷新界面 */
    protected open fun onLogEvent(msg: String, ok: Boolean) {}

    /** 统一的结果提示：写日志 + 广播（前台页面 Toast） */
    protected fun result(msg: String, ok: Boolean) = LogBus.notify(this, msg, ok)

    // ---------- LoginListener ----------

    override fun log(msg: String) = LogBus.log(this, msg)

    override fun onGeetestRequired(gt: String, challenge: String, onResult: (String?) -> Unit) {
        pendingGeetest = onResult
        startActivityForResult(
            Intent(this, GeetestActivity::class.java)
                .putExtra("gt", gt)
                .putExtra("challenge", challenge),
            REQ_GEETEST
        )
    }

    override fun onSmsCodeRequired(hint: String, onResult: (String?) -> Unit) {
        pendingSms = onResult
        val input = EditText(this)
        input.hint = "6 位短信验证码"
        input.inputType = InputType.TYPE_CLASS_NUMBER
        AlertDialog.Builder(this)
            .setTitle("短信验证")
            .setMessage(hint)
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("确定") { _, _ ->
                pendingSms?.invoke(input.text.toString().trim())
                pendingSms = null
            }
            .setNegativeButton("取消") { _, _ ->
                pendingSms?.invoke(null)
                pendingSms = null
            }
            .show()
    }

    override fun onLoginSuccess(cred: Credentials) {}

    override fun onLoginFailed(msg: String) {}

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_GEETEST) {
            val validate = if (resultCode == RESULT_OK) data?.getStringExtra("validate") else null
            pendingGeetest?.invoke(validate)
            pendingGeetest = null
            if (validate == null) log("极验已取消")
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        const val REQ_GEETEST = 1
        const val REQ_SCAN = 2
    }
}
