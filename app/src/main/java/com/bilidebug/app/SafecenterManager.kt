package com.bilidebug.app

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 账号安全中心：登录保护（二次验证）/ 登录设备管理 / 一键退登。
 *
 * 抓包实测（2026-09-11，`content-autofill...har`）：
 *  - 统一走 App 端 appkey=783bbb7264451d82 + appsec=2653583c8873dea268ab9386918b1d65（5 条 sign 全部离线 MATCH）
 *  - 鉴权 = access_key(长 token) + csrf(=bili_jct)，设备类接口额外带 buvid / local_id / device_name / device_platform
 *  - 需要短信验证码的三个动作（先 captcha/pre -> 极验 -> common/sms/send 拿 captcha_key -> 用户输入 code -> 调目标接口）：
 *      user_setting/disable            ：sms_type=deviceVerify
 *      user_device/delete/v2           ：sms_type=device_verify
 *      web_cookie/del                  ：sms_type=delWebCookie
 *  - 注意不对称：**开启**登录保护 `user_setting/enable` 不需要任何验证码，**关闭**才需要。
 */
class SafecenterManager(private val listener: LoginListener) {

    private val ui = Handler(Looper.getMainLooper())

    // ---------- 查询 ----------

    /** 查询登录保护（二次验证）是否开启；失败回调 null */
    fun queryDeviceVerify(cred: Credentials, onResult: (Boolean?) -> Unit) {
        Thread {
            val r = try {
                val p = base(cred)
                BiliSign.signAndAdd(p, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
                val resp = BiliClient.getQuery(Const.SAFE_USER_SETTING, p, Const.verifyHeaders())
                val obj = JSONObject(resp)
                if (obj.optInt("code", -1) != 0) {
                    listener.log("查询登录保护失败: $resp")
                    null
                } else {
                    obj.getJSONObject("data").optBoolean("enable_device_verify")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                listener.log("查询登录保护异常: ${e.message}")
                null
            }
            ui.post { onResult(r) }
        }.start()
    }

    /** 查询登录设备列表，回调 data.devices */
    fun queryDevices(cred: Credentials, onResult: (JSONArray?) -> Unit) {
        Thread {
            val r = try {
                val p = base(cred, deviceExtra())
                BiliSign.signAndAdd(p, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
                val resp = BiliClient.getQuery(Const.SAFE_LOGIN_DEVICES, p, Const.verifyHeaders())
                val obj = JSONObject(resp)
                if (obj.optInt("code", -1) != 0) {
                    listener.log("查询登录设备失败: $resp")
                    null
                } else {
                    obj.getJSONObject("data").optJSONArray("devices")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                listener.log("查询登录设备异常: ${e.message}")
                null
            }
            ui.post { onResult(r) }
        }.start()
    }

    // ---------- 修改 ----------

    /** 开启/关闭登录保护。开启无需验证码；关闭需要短信验证码 */
    fun setDeviceVerify(cred: Credentials, enable: Boolean, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                if (enable) {
                    val p = base(cred, deviceExtra())
                    p["setting"] = "device_verify"
                    BiliSign.signAndAdd(p, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
                    val resp = BiliClient.postForm(Const.SAFE_SETTING_ENABLE, p, Const.verifyHeaders())
                    finish("开启登录保护", resp, onResult)
                } else {
                    val ck = smsFlow(cred, "deviceVerify") ?: run {
                        ui.post { onResult(false, "短信前置流程失败") }
                        return@Thread
                    }
                    val code = requestSmsCode() ?: run {
                        ui.post { onResult(false, "未输入短信验证码") }
                        return@Thread
                    }
                    val p = base(cred, deviceExtra())
                    p["setting"] = "device_verify"
                    p["captcha_key"] = ck
                    p["code"] = code
                    BiliSign.signAndAdd(p, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
                    val resp = BiliClient.postForm(Const.SAFE_SETTING_DISABLE, p, Const.verifyHeaders())
                    finish("关闭登录保护", resp, onResult)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ui.post { listener.log("设置登录保护异常: ${e.message}"); onResult(false, e.message ?: "") }
            }
        }.start()
    }

    /** 一键退登所有网页浏览器和 PC 客户端（需短信） */
    fun delWebCookie(cred: Credentials, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val ck = smsFlow(cred, "delWebCookie") ?: run {
                    ui.post { onResult(false, "短信前置流程失败") }
                    return@Thread
                }
                val code = requestSmsCode() ?: run {
                    ui.post { onResult(false, "未输入短信验证码") }
                    return@Thread
                }
                val p = base(cred)
                p["captcha_key"] = ck
                p["code"] = code
                BiliSign.signAndAdd(p, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
                val resp = BiliClient.getQuery(Const.SAFE_WEB_COOKIE_DEL, p, Const.verifyHeaders())
                finish("一键退登 Web/PC", resp, onResult)
            } catch (e: Exception) {
                e.printStackTrace()
                ui.post { listener.log("一键退登异常: ${e.message}"); onResult(false, e.message ?: "") }
            }
        }.start()
    }

    /** 删除指定登录设备（需短信）。localId/source 取自设备列表项 */
    fun deleteDevice(cred: Credentials, localId: String, source: Int, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val ck = smsFlow(cred, "deviceVerify") ?: run {
                    ui.post { onResult(false, "短信前置流程失败") }
                    return@Thread
                }
                val code = requestSmsCode() ?: run {
                    ui.post { onResult(false, "未输入短信验证码") }
                    return@Thread
                }
                val target = JSONObject().put("target_devices", JSONArray().put(
                    JSONObject().put("target_local_id", localId).put("target_source", source)
                ))
                val p = base(cred, deviceExtra())
                p["captcha_key"] = ck
                p["code"] = code
                p["sms_type"] = "device_verify"
                p["target_devices"] = target.toString()
                BiliSign.signAndAdd(p, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
                val resp = BiliClient.postForm(Const.SAFE_DEVICE_DELETE, p, Const.verifyHeaders())
                finish("删除登录设备", resp, onResult)
            } catch (e: Exception) {
                e.printStackTrace()
                ui.post { listener.log("删除登录设备异常: ${e.message}"); onResult(false, e.message ?: "") }
            }
        }.start()
    }

    // ---------- 内部 ----------

    private fun finish(action: String, resp: String, onResult: (Boolean, String) -> Unit) {
        val code = try { JSONObject(resp).optInt("code", -1) } catch (_: Exception) { -1 }
        if (code == 0) {
            listener.log("$action 成功")
            ui.post { onResult(true, resp) }
        } else {
            listener.log("$action 失败: $resp")
            ui.post { onResult(false, resp) }
        }
    }

    /** 极验 + 发短信，返回 captcha_key；任一步失败返回 null */
    private fun smsFlow(cred: Credentials, smsType: String): String? {
        val cp = base(cred)
        BiliSign.signAndAdd(cp, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
        val cpResp = BiliClient.postForm(Const.CAPTCHA_PRE, cp, Const.verifyHeaders())
        val cpObj = JSONObject(cpResp)
        if (cpObj.optInt("code", -1) != 0) {
            listener.log("captcha/pre 失败: $cpResp")
            return null
        }
        val cpData = cpObj.getJSONObject("data")
        val gt = cpData.optString("gee_gt")
        val challenge = cpData.optString("gee_challenge")
        val recaptchaToken = cpData.optString("recaptcha_token")
        listener.log("极验准备完成（$smsType）")

        val validate = requestGeetest(gt, challenge) ?: run {
            listener.log("极验未完成，已取消")
            return null
        }

        val sms = base(cred, mapOf(
            "gee_challenge" to challenge,
            "gee_validate" to validate,
            "gee_seccode" to "$validate|jordan",
            "recaptcha_token" to recaptchaToken,
            "sms_type" to smsType,
        ))
        BiliSign.signAndAdd(sms, BiliSign.APP_APPKEY, BiliSign.APP_APPSEC)
        val smsResp = BiliClient.postForm(Const.SMS_SEND, sms, Const.verifyHeaders())
        val smsObj = JSONObject(smsResp)
        if (smsObj.optInt("code", -1) != 0) {
            listener.log("sms/send 失败: $smsResp")
            return null
        }
        val ck = smsObj.getJSONObject("data").optString("captcha_key")
        listener.log("短信已发送，captcha_key=${ck.take(16)}...")
        return ck
    }

    private fun base(cred: Credentials, extra: Map<String, String> = emptyMap()): MutableMap<String, String> {
        val p = mutableMapOf(
            "access_key" to accessKeyOf(cred),
            "appkey" to BiliSign.APP_APPKEY,
            "build" to "9020300",
            "csrf" to cred.biliJct,
            "disable_rcmd" to "0",
            "mobi_app" to "android",
            "platform" to "android",
            "statistics" to Const.VERIFY_STAT,
            "ts" to Const.ts(),
        )
        p.putAll(extra)
        return p
    }

    private fun deviceExtra(): Map<String, String> {
        val fp = DeviceFingerprint.real()
        val buvid = fp["buvid"] ?: ""
        return mapOf(
            "buvid" to buvid,
            "local_id" to buvid,
            "device_name" to (fp["device_name"] ?: ""),
            "device_platform" to (fp["device_platform"] ?: ""),
        )
    }

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
        ui.post {
            listener.onSmsCodeRequired("请输入手机收到的 6 位短信验证码") { r -> result = r; latch.countDown() }
        }
        latch.await(180, TimeUnit.SECONDS)
        return result
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
}
