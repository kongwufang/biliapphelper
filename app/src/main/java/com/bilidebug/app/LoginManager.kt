package com.bilidebug.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class Credentials(
    val mid: String,
    val accessToken: String,
    val refreshToken: String,
    val sessdata: String,
    val biliJct: String,
)

interface LoginListener {
    fun log(msg: String)
    fun onGeetestRequired(gt: String, challenge: String, onResult: (String?) -> Unit)
    fun onSmsCodeRequired(hint: String, onResult: (String?) -> Unit)
    fun onLoginSuccess(cred: Credentials)
    fun onLoginFailed(msg: String)
}

/** 密码登录 + status=2/3 风控/设备验证完整状态机。后台线程执行，交互步骤用 latch 阻塞等待。 */
class LoginManager(private val listener: LoginListener) {

    private val ui = Handler(Looper.getMainLooper())
    private var fp: Map<String, String> = emptyMap()

    fun login(username: String, password: String, useNewDevice: Boolean) {
        fp = if (useNewDevice) DeviceFingerprint.newDevice() else DeviceFingerprint.real()
        Thread {
            try {
                doLogin(username, password)
            } catch (e: Exception) {
                e.printStackTrace()
                listener.onLoginFailed(e.message ?: e.javaClass.simpleName)
            }
        }.start()
    }

    private fun log(msg: String) = ui.post { listener.log(msg) }

    // ---------- 密码登录 ----------
    private fun doLogin(username: String, password: String) {
        log("获取 RSA 公钥...")
        val keyResp = BiliClient.get(Const.KEY_URL, Const.loginHeaders())
        val keyObj = JSONObject(keyResp)
        if (keyObj.optInt("code", -1) != 0) throw RuntimeException("web/key 失败: $keyResp")
        val hash = keyObj.getJSONObject("data").optString("hash")
        val pubPem = keyObj.getJSONObject("data").optString("key")

        val encPwd = Crypto.rsaEncryptPkcs1(pubPem, hash + password)
        log("密码已 RSA 加密（${encPwd.length} 字节）")

        val params = mutableMapOf(
            "actionKey" to "appkey",
            "build" to "9020300",
            "device" to "phone",
            "mobi_app" to "android",
            "platform" to "android",
            "statistics" to Const.LOGIN_STAT,
            "ts" to Const.ts(),
            "username" to username,
            "password" to encPwd,
        )
        for ((k, v) in fp) if (!params.containsKey(k)) params[k] = v
        BiliSign.signAndAdd(params, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)

        log("提交 oauth2/login ...")
        val loginResp = BiliClient.postForm(Const.LOGIN_URL, params, Const.loginHeaders())
        val loginObj = JSONObject(loginResp)
        if (loginObj.optInt("code", -1) != 0) {
            throw RuntimeException("登录失败 code=${loginObj.optInt("code")} msg=${loginObj.optString("message")}")
        }
        val data = loginObj.getJSONObject("data")
        val status = data.optInt("status", -1)
        log("login 返回 status=$status")

        when (status) {
            0 -> { saveAndFinish(data) }
            2, 3 -> doVerify(status, data)
            else -> throw RuntimeException("未知 status=$status: $loginResp")
        }
    }

    // ---------- 验证流程 ----------
    private fun doVerify(status: Int, loginData: JSONObject) {
        val isRisk = status == 2
        log(if (isRisk) "需风险验证(status=2)" else "需设备验证(status=3)")

        // 从 risk url 解析 tmp_token / request_id
        val url = loginData.optString("url", "")
        val tmpToken = queryParam(url, "tmp_token")
        val requestId = queryParam(url, "request_id").ifEmpty { queryParam(url, "requestId") }
        log("tmp_token=${tmpToken.take(16)}... request_id=${requestId.take(16)}...")

        // status=2 先查需验证手机号
        if (isRisk) {
            val infoParams = commonParams(mutableMapOf("tmp_code" to tmpToken))
            BiliSign.signAndAdd(infoParams, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
            val infoResp = BiliClient.getQuery(Const.USER_INFO, infoParams, Const.verifyHeaders())
            val infoObj = JSONObject(infoResp)
            val hideTel = infoObj.optJSONObject("data")?.optJSONObject("account_info")?.optString("hide_tel") ?: ""
            log("需验证手机号: ${hideTel.ifEmpty { "(未知)" }}")
        }

        // 极验准备
        val cp = commonParams(mutableMapOf())
        BiliSign.signAndAdd(cp, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
        val cpResp = BiliClient.postForm(Const.CAPTCHA_PRE, cp, Const.verifyHeaders())
        val cpObj = JSONObject(cpResp)
        if (cpObj.optInt("code", -1) != 0) throw RuntimeException("captcha/pre 失败: $cpResp")
        val cpData = cpObj.getJSONObject("data")
        val gt = cpData.optString("gee_gt")
        val challenge = cpData.optString("gee_challenge")
        val recaptchaToken = cpData.optString("recaptcha_token")
        log("极验准备完成 gt=${gt.take(8)}... challenge=${challenge.take(8)}...")

        // 极验（阻塞等待用户完成）
        val validate = requestGeetest(gt, challenge) ?: throw RuntimeException("极验未完成")
        log("极验 validate=${validate.take(16)}...")

        // 发短信
        val smsType = if (isRisk) "loginTelCheck" else "deviceVerify"
        val smsParams = commonParams(mutableMapOf(
            "gee_challenge" to challenge,
            "gee_validate" to validate,
            "gee_seccode" to (validate + "|jordan"),
            "recaptcha_token" to recaptchaToken,
            "sms_type" to smsType,
            "tmp_code" to tmpToken,
        ))
        BiliSign.signAndAdd(smsParams, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
        val smsResp = BiliClient.postForm(Const.SMS_SEND, smsParams, Const.verifyHeaders())
        val smsObj = JSONObject(smsResp)
        if (smsObj.optInt("code", -1) != 0) throw RuntimeException("sms/send 失败: $smsResp")
        val captchaKey = smsObj.getJSONObject("data").optString("captcha_key")
        log("短信已发送，captcha_key=${captchaKey.take(16)}...")

        // 输入短信验证码
        val smsCode = requestSmsCode() ?: throw RuntimeException("未输入短信验证码")
        log("收到短信验证码 ${smsCode.length} 位")

        // 提交验证码
        val newCode: String
        if (isRisk) {
            val tvParams = commonParams(mutableMapOf(
                "type" to "loginTelCheck",
                "code" to smsCode,
                "tmp_code" to tmpToken,
                "request_id" to requestId,
                "captcha_key" to captchaKey,
            ))
            BiliSign.signAndAdd(tvParams, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
            val tvResp = BiliClient.postForm(Const.TEL_VERIFY, tvParams, Const.verifyHeaders())
            val tvObj = JSONObject(tvResp)
            if (tvObj.optInt("code", -1) != 0) throw RuntimeException("login/tel/verify 失败: $tvResp")
            newCode = tvObj.getJSONObject("data").optString("code")
        } else {
            val dvParams = commonParams(mutableMapOf(
                "captcha_key" to captchaKey,
                "code" to smsCode,
                "sms_type" to "deviceVerify",
                "tmp_code" to tmpToken,
            ))
            BiliSign.signAndAdd(dvParams, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
            val dvResp = BiliClient.postForm(Const.DEVICE_VERIFY, dvParams, Const.verifyHeaders())
            val dvObj = JSONObject(dvResp)
            if (dvObj.optInt("code", -1) != 0) throw RuntimeException("user_device/verify 失败: $dvResp")
            newCode = dvObj.getJSONObject("data").optString("code")
        }
        log("验证通过，获取授权码 ${newCode.take(16)}...")

        // 换 token
        val atParams = commonParams(mutableMapOf(
            "grant_type" to "authorization_code",
            "code" to newCode,
            "c_locale" to "zh-Hans_CN",
            "s_locale" to "zh-Hans_CN",
            "channel" to "oppo_tv.danmaku.bili_20200623",
        ))
        for (k in listOf("bili_local_id", "buvid", "device", "device_id", "device_name", "device_platform", "local_id")) {
            fp[k]?.let { atParams[k] = it }
        }
        BiliSign.signAndAdd(atParams, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
        val atResp = BiliClient.postForm(Const.ACCESS_TOKEN_URL, atParams, Const.verifyHeaders())
        val atObj = JSONObject(atResp)
        if (atObj.optInt("code", -1) != 0) throw RuntimeException("access_token 失败: $atResp")
        val tokenData = atObj.getJSONObject("data")
        if (tokenData.optInt("status", -1) != 0) throw RuntimeException("access_token status!=0: $atResp")
        log("获取凭证成功")
        saveAndFinish(tokenData)
    }

    private fun commonParams(extra: MutableMap<String, String>): MutableMap<String, String> {
        val p = mutableMapOf(
            "appkey" to BiliSign.APP_APPKEY,
            "build" to "9020300",
            "disable_rcmd" to "0",
            "mobi_app" to "android",
            "platform" to "android",
            "statistics" to Const.VERIFY_STAT,
            "ts" to Const.ts(),
        )
        p.putAll(extra)
        return p
    }

    private fun saveAndFinish(data: JSONObject) {
        val tokenInfo = data.optJSONObject("token_info") ?: JSONObject()
        val cookies = data.optJSONObject("cookie_info")?.optJSONArray("cookies")
        var sessdata = ""
        var biliJct = ""
        if (cookies != null) {
            for (i in 0 until cookies.length()) {
                val c = cookies.getJSONObject(i)
                when (c.optString("name")) {
                    "SESSDATA" -> sessdata = c.optString("value")
                    "bili_jct" -> biliJct = c.optString("value")
                }
            }
        }
        // SESSDATA %2C 还原为逗号
        sessdata = java.net.URLDecoder.decode(sessdata, "UTF-8")
        val cred = Credentials(
            mid = tokenInfo.optString("mid"),
            accessToken = tokenInfo.optString("access_token"),
            refreshToken = tokenInfo.optString("refresh_token"),
            sessdata = sessdata,
            biliJct = biliJct,
        )
        if (cred.accessToken.isEmpty()) throw RuntimeException("响应缺少 access_token")
        ui.post { listener.onLoginSuccess(cred) }
    }

    // ---------- 交互（latch 阻塞） ----------
    private fun requestGeetest(gt: String, challenge: String): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        ui.post { listener.onGeetestRequired(gt, challenge) { r -> result = r; latch.countDown() } }
        latch.await(180, TimeUnit.SECONDS)
        return result
    }

    private fun requestSmsCode(): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        ui.post { listener.onSmsCodeRequired("请输入手机收到的 6 位短信验证码") { r -> result = r; latch.countDown() } }
        latch.await(180, TimeUnit.SECONDS)
        return result
    }

    private fun queryParam(url: String, key: String): String {
        return try {
            val q = url.substringAfter('?', "")
            q.split('&').firstOrNull { it.substringBefore('=') == key }
                ?.substringAfter('=', "")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
        } catch (_: Exception) { "" }
    }
}
