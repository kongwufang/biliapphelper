package com.bilidebug.app

import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/** 密码登录页（含 status=2/3 风控验证，由 LoginManager 驱动）。 */
class LoginActivity : BaseActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var cbNewDevice: CheckBox
    private lateinit var btnDoLogin: MaterialButton
    private lateinit var tvLoginResult: TextView

    private val loginManager = LoginManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        cbNewDevice = findViewById(R.id.cbNewDevice)
        btnDoLogin = findViewById(R.id.btnDoLogin)
        tvLoginResult = findViewById(R.id.tvLoginResult)

        btnDoLogin.setOnClickListener {
            val u = etUsername.text.toString().trim()
            val p = etPassword.text.toString()
            if (u.isEmpty() || p.isEmpty()) {
                result("请输入账号和密码", false)
                return@setOnClickListener
            }
            btnDoLogin.isEnabled = false
            tvLoginResult.text = "登录中..."
            log("开始密码登录（${if (cbNewDevice.isChecked) "陌生设备" else "真实设备"}指纹）")
            loginManager.login(u, p, cbNewDevice.isChecked)
        }
    }

    override fun onLoginSuccess(cred: Credentials) {
        CredentialStore.save(this, cred)
        btnDoLogin.isEnabled = true
        tvLoginResult.text = "登录成功\nmid=${cred.mid}\n凭据已保存到本地"
        result("登录成功：mid=${cred.mid}", true)
    }

    override fun onLoginFailed(msg: String) {
        btnDoLogin.isEnabled = true
        tvLoginResult.text = "登录失败：$msg"
        result("登录失败：$msg", false)
    }
}
