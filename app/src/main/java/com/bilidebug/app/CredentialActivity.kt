package com.bilidebug.app

import android.os.Bundle
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * 刷新凭证：access_token 有效期查询与刷新。
 * 进入页面自动查询（静默），用户主动操作才有结果提示。
 */
class CredentialActivity : BaseActivity() {

    private lateinit var tvCred: TextView
    private lateinit var tvTokenInfo: TextView
    private lateinit var btnQueryToken: MaterialButton
    private lateinit var btnRefreshToken: MaterialButton

    private val tokenManager = TokenManager(this)
    private var cred: Credentials? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credential)

        tvCred = findViewById(R.id.tvCred)
        tvTokenInfo = findViewById(R.id.tvTokenInfo)
        btnQueryToken = findViewById(R.id.btnQueryToken)
        btnRefreshToken = findViewById(R.id.btnRefreshToken)

        cred = CredentialStore.load(this)
        renderCred()

        btnQueryToken.setOnClickListener { queryToken() }
        btnRefreshToken.setOnClickListener { refreshToken() }

        if (cred != null) queryToken(silent = true)
        else result("未登录，请先到首页完成密码登录", false)
    }

    private fun renderCred() {
        val c = cred
        if (c == null) {
            tvCred.text = "未登录"
            tvTokenInfo.text = "有效期：未知"
        } else {
            tvCred.text = "mid=${c.mid}\n" +
                    "access_token=${c.accessToken.take(32)}...\n" +
                    "SESSDATA=${c.sessdata.take(24)}...\n" +
                    "bili_jct=${c.biliJct.take(12)}..."
        }
    }

    private fun queryToken(silent: Boolean = false) {
        val c = cred
        if (c == null) {
            result("未登录", false)
            return
        }
        btnQueryToken.isEnabled = false
        log("查询 access_token 有效期...")
        tokenManager.queryInfo(c) { info ->
            btnQueryToken.isEnabled = true
            if (info == null) {
                if (silent) log("查询有效期失败") else result("查询有效期失败", false)
                return@queryInfo
            }
            val days = info.expiresIn / 86400
            tvTokenInfo.text = "mid=${info.mid}\n" +
                    "expires_in=${info.expiresIn}s（约 $days 天）\n" +
                    "需刷新 refresh=${info.refresh}"
            if (silent) log("access_token 剩余约 $days 天")
            else result("有效期剩余约 $days 天", true)
        }
    }

    private fun refreshToken() {
        val c = cred
        if (c == null) {
            result("未登录", false)
            return
        }
        if (c.refreshToken.isEmpty()) {
            result("缺少 refresh_token，无法刷新", false)
            return
        }
        btnRefreshToken.isEnabled = false
        log("刷新 access_token ...")
        tokenManager.refresh(c) { res ->
            btnRefreshToken.isEnabled = true
            if (res == null) {
                result("刷新失败，保留原 token", false)
                return@refresh
            }
            val newCred = c.copy(accessToken = res.accessToken, refreshToken = res.refreshToken)
            cred = newCred
            CredentialStore.save(this, newCred)
            renderCred()
            val days = res.expiresIn / 86400
            tvTokenInfo.text = "mid=${res.mid}\n" +
                    "expires_in=${res.expiresIn}s（约 $days 天）"
            result("刷新成功，新有效期约 $days 天", true)
        }
    }
}
