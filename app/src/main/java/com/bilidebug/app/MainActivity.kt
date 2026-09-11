package com.bilidebug.app

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/** 首页：登录状态 + 功能菜单。日志收进二级页面，操作结果通过广播 Toast 提示。 */
class MainActivity : BaseActivity() {

    private lateinit var tvState: TextView
    private lateinit var tvStatusDetail: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        DeviceFingerprint.init(this)

        tvState = findViewById(R.id.tvState)
        tvStatusDetail = findViewById(R.id.tvStatusDetail)

        findViewById<MaterialButton>(R.id.btnLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            if (CredentialStore.load(this) == null) {
                result("当前未登录", false)
                return@setOnClickListener
            }
            CredentialStore.clear(this)
            result("已退出登录，本地凭证已清除", true)
            refreshState()
        }

        findViewById<MaterialCardView>(R.id.cardToken).setOnClickListener {
            startActivity(Intent(this, CredentialActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardQr).setOnClickListener {
            startActivity(Intent(this, QrLoginActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardPrivacy).setOnClickListener {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardSecurity).setOnClickListener {
            startActivity(Intent(this, SecurityActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardLog).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        val cred = CredentialStore.load(this)
        if (cred == null) {
            tvState.text = "未登录"
            tvStatusDetail.text = "尚未登录，请先完成密码登录"
        } else {
            tvState.text = "已登录"
            tvStatusDetail.text = "mid=${cred.mid}\n" +
                    "access_token=${cred.accessToken.take(24)}...\n" +
                    "refresh_token=${if (cred.refreshToken.isNotEmpty()) "有" else "无"}"
        }
    }
}
