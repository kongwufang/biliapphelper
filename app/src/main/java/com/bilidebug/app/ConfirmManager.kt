package com.bilidebug.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.URLEncoder

/** 扫码授权：用已登录 App 凭证确认 Web / TV 的登录二维码。 */
class ConfirmManager(private val listener: LoginListener) {

    private val ui = Handler(Looper.getMainLooper())

    fun buildAccessKey(cred: Credentials): String {
        return if (cred.accessToken.length > 32) {
            cred.accessToken
        } else {
            val parts = cred.sessdata.split(",")
            if (parts.size != 3) throw RuntimeException("SESSDATA 应为 a,b,c 三段")
            cred.accessToken + parts[2].substring(8)
        }
    }

    private fun appCookie(cred: Credentials): String {
        val enc = URLEncoder.encode(cred.sessdata, "UTF-8")
        return "SESSDATA=$enc; bili_jct=${cred.biliJct}"
    }

    private fun appHeaders(cred: Credentials): Map<String, String> = mapOf(
        "User-Agent" to Const.BILI_UA,
        "Referer" to "https://account.bilibili.com/h5/account-h5/auth/scan-web",
        "Accept" to "application/json, text/plain, */*",
        "app-key" to "android64",
        "env" to "prod",
        "Cookie" to appCookie(cred),
    )

    // ---------- Web 扫码确认 ----------
    fun confirmWeb(cred: Credentials, qrcodeKey: String, onDone: (String) -> Unit) {
        Thread {
            try {
                val accessKey = buildAccessKey(cred)
                val csrf = cred.biliJct
                val headers = appHeaders(cred)
                val common = commonWebParams(accessKey, csrf, qrcodeKey)

                // check
                val checkP = common.toMutableMap()
                BiliSign.signAndAdd(checkP, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
                val check = BiliClient.getQuery(Const.WEB_CHECK, checkP, headers)
                log("[web check] $check")

                // scene
                val sceneP = common.toMutableMap()
                BiliSign.signAndAdd(sceneP, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
                val scene = BiliClient.getQuery(Const.WEB_SCENE, sceneP, headers)
                log("[web scene] $scene")
                val sd = JSONObject(scene).optJSONObject("data")
                val transient = if (sd?.optBoolean("transient", false) == true) "true" else "false"

                // confirm（表单 POST）
                val confirmP = common.toMutableMap()
                confirmP["env_key"] = ""
                confirmP["transient"] = transient
                confirmP["verify_code"] = ""
                confirmP["verify_key"] = ""
                confirmP["verify_type"] = "verify_tel"
                BiliSign.signAndAdd(confirmP, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
                val confirm = BiliClient.postForm(Const.WEB_CONFIRM, confirmP, headers)
                log("[web confirm] $confirm")
                ui.post { onDone(confirm) }
            } catch (e: Exception) {
                e.printStackTrace()
                ui.post { onDone("{\"code\":-1,\"message\":\"${e.message}\"}") }
            }
        }.start()
    }

    private fun commonWebParams(accessKey: String, csrf: String, qrcodeKey: String): Map<String, String> = mapOf(
        "access_key" to accessKey,
        "build" to "9020300",
        "csrf" to csrf,
        "disable_rcmd" to "0",
        "mobi_app" to "android",
        "platform" to "android",
        "qrcode_key" to qrcodeKey,
        "statistics" to Const.VERIFY_STAT,
        "ts" to Const.ts(),
    )

    // ---------- TV 扫码确认 ----------
    fun confirmTv(cred: Credentials, authCode: String, onDone: (String) -> Unit) {
        Thread {
            try {
                val accessKey = buildAccessKey(cred)
                val p = mutableMapOf(
                    "access_key" to accessKey,
                    "auth_code" to authCode,
                    "csrf" to cred.biliJct,
                    "mobi_app" to "android",
                    "platform" to "android",
                    "build" to "9020300",
                    "disable_rcmd" to "0",
                    "statistics" to Const.VERIFY_STAT,
                    "ts" to Const.ts(),
                )
                BiliSign.signAndAdd(p, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
                val confirm = BiliClient.postQuery(Const.TV_CONFIRM, p, appHeaders(cred))
                log("[tv confirm] $confirm")
                ui.post { onDone(confirm) }
            } catch (e: Exception) {
                e.printStackTrace()
                ui.post { onDone("{\"code\":-1,\"message\":\"${e.message}\"}") }
            }
        }.start()
    }

    private fun log(msg: String) = ui.post { listener.log(msg) }

    companion object {
        /** 从二维码内容（URL 或裸 key）解析出 qrcode_key / auth_code */
        fun extractKey(raw: String): String {
            val s = raw.trim()
            for (key in listOf("qrcode_key", "auth_code")) {
                val idx = s.indexOf("$key=")
                if (idx >= 0) {
                    val v = s.substring(idx + key.length + 1)
                        .substringBefore('&').substringBefore('#')
                    return java.net.URLDecoder.decode(v, "UTF-8")
                }
            }
            // 裸 key：直接返回
            return s
        }
    }
}
