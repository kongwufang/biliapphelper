package com.bilidebug.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject

/** access_token 检查（oauth2/info）+ 刷新（oauth2/refresh_token），对标 app_token_refresh.py。 */
class TokenManager(private val listener: LoginListener) {

    private val ui = Handler(Looper.getMainLooper())

    data class TokenInfo(
        val mid: String,
        val expiresIn: Long,
        val refresh: Boolean,
        val accessToken: String,
    )

    data class RefreshResult(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long,
        val mid: String,
    )

    /** oauth2/info 查询 token 状态，onResult 在主线程回调。 */
    fun queryInfo(cred: Credentials, onResult: (TokenInfo?) -> Unit) {
        Thread {
            try {
                val fp = DeviceFingerprint.real()
                val accessKey = accessKeyOf(cred)
                val p = commonParams(accessKey, fp)
                BiliSign.signAndAdd(p, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
                val resp = BiliClient.getQuery(Const.INFO_URL, p, tokenHeaders(fp))
                val obj = JSONObject(resp)
                if (obj.optInt("code", -1) != 0) {
                    ui.post {
                        listener.log("oauth2/info 失败: $resp")
                        onResult(null)
                    }
                    return@Thread
                }
                val d = obj.optJSONObject("data") ?: JSONObject()
                val info = TokenInfo(
                    mid = d.optString("mid"),
                    expiresIn = d.optLong("expires_in", 0),
                    refresh = d.optBoolean("refresh", false),
                    accessToken = d.optString("access_token"),
                )
                ui.post { onResult(info) }
            } catch (e: Exception) {
                e.printStackTrace()
                ui.post {
                    listener.log("oauth2/info 异常: ${e.message}")
                    onResult(null)
                }
            }
        }.start()
    }

    /** oauth2/refresh_token 刷新，onResult 在主线程回调。 */
    fun refresh(cred: Credentials, onResult: (RefreshResult?) -> Unit) {
        Thread {
            try {
                if (cred.refreshToken.isEmpty()) {
                    ui.post {
                        listener.log("缺少 refresh_token，无法刷新")
                        onResult(null)
                    }
                    return@Thread
                }
                val fp = DeviceFingerprint.real()
                val accessKey = accessKeyOf(cred)
                val p = commonParams(accessKey, fp)
                p["grant_type"] = "refresh_token"
                p["refresh_token"] = cred.refreshToken
                p["access_token"] = accessKey
                BiliSign.signAndAdd(p, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
                val resp = BiliClient.postForm(Const.REFRESH_URL, p, tokenHeaders(fp))
                val obj = JSONObject(resp)
                if (obj.optInt("code", -1) != 0) {
                    ui.post {
                        listener.log("oauth2/refresh_token 失败: $resp")
                        onResult(null)
                    }
                    return@Thread
                }
                val ti = obj.optJSONObject("data")?.optJSONObject("token_info") ?: JSONObject()
                val result = RefreshResult(
                    accessToken = ti.optString("access_token"),
                    refreshToken = ti.optString("refresh_token"),
                    expiresIn = ti.optLong("expires_in", 0),
                    mid = ti.optString("mid"),
                )
                if (result.accessToken.isEmpty()) {
                    ui.post {
                        listener.log("刷新返回空 token: $resp")
                        onResult(null)
                    }
                    return@Thread
                }
                ui.post { onResult(result) }
            } catch (e: Exception) {
                e.printStackTrace()
                ui.post {
                    listener.log("refresh 异常: ${e.message}")
                    onResult(null)
                }
            }
        }.start()
    }

    private fun accessKeyOf(cred: Credentials): String {
        return if (cred.accessToken.length > 32) {
            cred.accessToken
        } else {
            val parts = cred.sessdata.split(",")
            if (parts.size != 3) throw RuntimeException("SESSDATA 应为 a,b,c 三段")
            cred.accessToken + parts[2].substring(8)
        }
    }

    private fun commonParams(accessKey: String, fp: Map<String, String>): MutableMap<String, String> {
        val p = mutableMapOf(
            "access_key" to accessKey,
            "from_access_key" to accessKey,
            "build" to "9020300",
            "c_locale" to "zh-Hans_CN",
            "channel" to "oppo_tv.danmaku.bili_20200623",
            "device" to "phone",
            "disable_rcmd" to "0",
            "mobi_app" to "android",
            "platform" to "android",
            "s_locale" to "zh-Hans_CN",
            "statistics" to Const.VERIFY_STAT,
            "ts" to Const.ts(),
        )
        for (k in listOf("buvid", "local_id", "device_id", "bili_local_id", "device_name", "device_platform")) {
            fp[k]?.let { p[k] = it }
        }
        return p
    }

    private fun tokenHeaders(fp: Map<String, String>): Map<String, String> {
        val h = mutableMapOf(
            "User-Agent" to Const.BILI_UA,
            "Referer" to "https://account.bilibili.com/",
            "Accept" to "application/json, text/plain, */*",
            "app-key" to "android64",
            "env" to "prod",
        )
        fp["buvid"]?.let { h["buvid"] = it }
        fp["bili_local_id"]?.let { h["fp_local"] = it }
        fp["device_id"]?.let { h["fp_remote"] = it }
        return h
    }
}
