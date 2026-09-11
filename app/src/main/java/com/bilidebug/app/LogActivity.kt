package com.bilidebug.app

import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/** 二级页面：运行日志。所有接口调用的过程日志集中在这里查看。 */
class LogActivity : BaseActivity() {

    private lateinit var tvLog: TextView
    private lateinit var tvLogCount: TextView
    private lateinit var svLog: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        tvLog = findViewById(R.id.tvLog)
        tvLogCount = findViewById(R.id.tvLogCount)
        svLog = findViewById(R.id.svLog)

        findViewById<MaterialButton>(R.id.btnClearLog).setOnClickListener {
            LogBus.clear()
            render()
            result("日志已清空", true)
        }

        render()
    }

    override fun onLogEvent(msg: String, ok: Boolean) {
        render()
    }

    private fun render() {
        val list = LogBus.all()
        tvLogCount.text = "共 ${list.size} 条"
        tvLog.text = if (list.isEmpty()) "（暂无日志）" else list.joinToString("\n")
        svLog.post { svLog.fullScroll(View.FOCUS_DOWN) }
    }
}
