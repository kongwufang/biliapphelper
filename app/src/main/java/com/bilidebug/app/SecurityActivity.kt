package com.bilidebug.app

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import org.json.JSONArray

/**
 * 账号安全 / 设备管理页：登录保护、一键退登、登录设备。
 * 进入页面自动查询状态（静默，不弹提示），用户主动操作才有结果提示。
 */
class SecurityActivity : BaseActivity() {

    private lateinit var tv2FA: TextView
    private lateinit var btnEnable2FA: MaterialButton
    private lateinit var btnDisable2FA: MaterialButton
    private lateinit var btnDelWebCookie: MaterialButton
    private lateinit var btnQueryDevices: MaterialButton
    private lateinit var llDevices: LinearLayout

    private val safe = SafecenterManager(this)
    private var cred: Credentials? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security)

        tv2FA = findViewById(R.id.tv2FA)
        btnEnable2FA = findViewById(R.id.btnEnable2FA)
        btnDisable2FA = findViewById(R.id.btnDisable2FA)
        btnDelWebCookie = findViewById(R.id.btnDelWebCookie)
        btnQueryDevices = findViewById(R.id.btnQueryDevices)
        llDevices = findViewById(R.id.llDevices)

        cred = CredentialStore.load(this)

        btnEnable2FA.setOnClickListener { set2FA(true) }
        btnDisable2FA.setOnClickListener { set2FA(false) }
        btnDelWebCookie.setOnClickListener { delWebCookie() }
        btnQueryDevices.setOnClickListener { queryDevices() }
        tv2FA.setOnClickListener { query2FA() }

        if (cred != null) {
            query2FA(silent = true)
            queryDevices(silent = true)
        } else {
            tv2FA.text = "未登录"
            result("未登录，请先到首页完成密码登录", false)
        }
    }

    // ---------- 登录保护 ----------

    private fun query2FA(silent: Boolean = false) {
        val c = cred ?: return
        if (!silent) tv2FA.text = "查询中"
        log("查询登录保护状态...")
        safe.queryDeviceVerify(c) { enabled ->
            if (enabled == null) {
                tv2FA.text = "查询失败"
                if (silent) log("登录保护状态查询失败") else result("登录保护状态查询失败", false)
            } else {
                val text = if (enabled) "已开启" else "已关闭"
                tv2FA.text = text
                if (silent) log("登录保护（二次验证）：$text")
                else result("登录保护（二次验证）：$text", true)
            }
        }
    }

    private fun set2FA(enable: Boolean) {
        val c = cred ?: return
        btnEnable2FA.isEnabled = false
        btnDisable2FA.isEnabled = false
        log(if (enable) "开启登录保护..." else "关闭登录保护（需短信验证码）...")
        safe.setDeviceVerify(c, enable) { ok, resp ->
            btnEnable2FA.isEnabled = true
            btnDisable2FA.isEnabled = true
            if (ok) {
                result(if (enable) "登录保护已开启" else "登录保护已关闭", true)
                query2FA(silent = true)
            } else {
                result("操作失败：$resp", false)
            }
        }
    }

    // ---------- 一键退登 ----------

    private fun delWebCookie() {
        val c = cred ?: return
        btnDelWebCookie.isEnabled = false
        log("一键退登所有 Web / PC ...")
        safe.delWebCookie(c) { ok, resp ->
            btnDelWebCookie.isEnabled = true
            result(if (ok) "已退登所有 Web / PC" else "一键退登失败：$resp", ok)
            // 退登后 Web / PC 会话会从设备列表消失，刷新一遍
            if (ok) queryDevices(silent = true)
        }
    }

    // ---------- 登录设备 ----------

    private fun queryDevices(silent: Boolean = false) {
        val c = cred ?: return
        btnQueryDevices.isEnabled = false
        log("查询登录设备列表...")
        safe.queryDevices(c) { devices ->
            btnQueryDevices.isEnabled = true
            if (devices == null) {
                if (silent) log("登录设备查询失败") else result("登录设备查询失败", false)
                return@queryDevices
            }
            renderDevices(devices)
            if (silent) log("登录设备共 ${devices.length()} 台")
            else result("登录设备共 ${devices.length()} 台", true)
        }
    }

    private fun renderDevices(devices: JSONArray) {
        llDevices.removeAllViews()
        for (i in 0 until devices.length()) {
            val d = devices.optJSONObject(i) ?: continue
            val name = d.optString("device_name").ifEmpty { "未知设备" }
            val platform = d.optString("device_platform")
            val source = d.optString("source")
            val time = d.optString("latest_login_at")
            val localId = d.optString("local_id")
            val origin = d.optInt("origin", 0)
            val isCurrent = d.optBoolean("is_current_device")

            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, 14, 0, 14)

            val tv = TextView(this)
            tv.text = (if (isCurrent) "【当前设备】" else "") + name +
                    (if (platform.isNotEmpty()) "\n$platform" else "") +
                    "\n$source · $time"
            tv.textSize = 12f
            tv.setTextColor(getColor(R.color.text_primary))
            tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(tv)

            if (!isCurrent) {
                val btn = MaterialButton(
                    this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
                )
                btn.text = "删除"
                btn.textSize = 12f
                btn.minWidth = 0
                btn.setPadding(12, 0, 12, 0)
                btn.setOnClickListener {
                    val c = cred
                    if (c == null) {
                        result("未登录", false)
                        return@setOnClickListener
                    }
                    btn.isEnabled = false
                    log("删除设备 $name ...")
                    safe.deleteDevice(c, localId, origin) { ok, resp ->
                        btn.isEnabled = true
                        if (ok) {
                            result("已删除设备：$name", true)
                            queryDevices(silent = true)
                        } else {
                            result("删除失败：$resp", false)
                        }
                    }
                }
                row.addView(btn)
            }
            llDevices.addView(row)

            if (i < devices.length() - 1) {
                val div = View(this)
                div.setBackgroundColor(getColor(R.color.divider))
                div.layoutParams =
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                llDevices.addView(div)
            }
        }
    }
}
