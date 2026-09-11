package com.bilidebug.app

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.RadioGroup
import com.google.android.material.button.MaterialButton

/** 扫码登录：用本机 App 凭证授权 Web / TV 端扫码登录。 */
class QrLoginActivity : BaseActivity() {

    private lateinit var etQrCode: EditText
    private lateinit var rgTarget: RadioGroup
    private lateinit var btnScan: MaterialButton
    private lateinit var btnConfirm: MaterialButton

    private val confirmManager = ConfirmManager(this)
    private var cred: Credentials? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrlogin)

        etQrCode = findViewById(R.id.etQrCode)
        rgTarget = findViewById(R.id.rgTarget)
        btnScan = findViewById(R.id.btnScan)
        btnConfirm = findViewById(R.id.btnConfirm)

        cred = CredentialStore.load(this)

        btnConfirm.setOnClickListener {
            val raw = etQrCode.text.toString().trim()
            if (raw.isEmpty()) {
                result("请输入二维码内容", false)
                return@setOnClickListener
            }
            doConfirm(raw)
        }

        btnScan.setOnClickListener {
            if (cred == null) {
                result("请先到首页完成登录", false)
                return@setOnClickListener
            }
            startActivityForResult(Intent(this, QrScanActivity::class.java), REQ_SCAN)
        }

        if (cred == null) result("未登录，请先到首页完成密码登录", false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SCAN && resultCode == RESULT_OK) {
            val raw = data?.getStringExtra("result") ?: ""
            if (raw.isNotEmpty()) {
                log("扫码结果: $raw")
                doConfirm(raw)
            }
        }
    }

    private fun doConfirm(raw: String) {
        val c = cred
        if (c == null) {
            result("请先到首页完成登录", false)
            return
        }
        val key = ConfirmManager.extractKey(raw)
        if (key.isEmpty()) {
            result("无法解析二维码内容", false)
            return
        }
        log("解析到 key: $key")
        if (rgTarget.checkedRadioButtonId == R.id.rbTv) {
            log("TV 扫码确认中...")
            confirmManager.confirmTv(c, key) { r -> result("TV 确认结果: $r", okOf(r)) }
        } else {
            log("Web 扫码确认中...")
            confirmManager.confirmWeb(c, key) { r -> result("Web 确认结果: $r", okOf(r)) }
        }
    }

    private fun okOf(r: String) = r.contains("code=0") || r.contains("\"code\":0")
}
