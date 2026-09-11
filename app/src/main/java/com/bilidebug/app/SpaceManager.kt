package com.bilidebug.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject

/**
 * 空间隐私设置：查询（x/space/setting/app）+ 批量修改（x/space/privacy/batch/modify）。
 *
 * 抓包实测（2026-09-11，`data.bilibili.com_*.har`）：
 *  - appkey=1d8b6e7d45233436，appsec=560c52ccd288fed045859ed18bffd973（sign 已离线比对 MATCH）
 *  - batch/modify 为「全量提交」：body(form) 必须带上所有可改字段的当前值，只把目标字段改成新值，
 *    缺字段会导致其它开关被服务端重置/请求失败，因此这里先查全量现值再合并提交。
 *  - 与 account.bilibili.com 的 safecenter 接口不同，这两个接口用 access_key 鉴权、不依赖 Cookie。
 */
class SpaceManager(private val listener: LoginListener) {

    private val ui = Handler(Looper.getMainLooper())

    /** batch/modify 全量提交的字段集合（HAR 抓包全集，顺序无关） */
    val modifyFields = listOf(
        "bangumi", "charge_video", "close_space_medal", "coins_video", "comic",
        "disable_following", "disable_show_fans", "disable_show_school", "dress_up",
        "fav_video", "groups", "lesson_video", "likes_video", "live_playback",
        "only_show_wearing", "played_game", "submited_video", "tags",
    )

    /** GET 返回里存在、但 batch/modify 不接受的只读字段（不可改，仅展示） */
    val readOnlyFields = listOf("bbq", "channel", "my_game_public", "user_info")

    /** 查询空间隐私设置，主线程回调 data.privacy（键 -> "0"/"1"） */
    fun queryPrivacy(cred: Credentials, onResult: (Map<String, String>?) -> Unit) {
        Thread {
            val map = queryPrivacySync(cred)
            ui.post {
                if (map == null) listener.log("查询空间隐私失败（见上）")
                onResult(map)
            }
        }.start()
    }

    /**
     * 修改若干字段：先拉当前全量现值 -> 合并 changes -> 全量提交。
     * 主线程回调 (是否成功, 原始响应)。
     */
    fun modifyPrivacy(cred: Credentials, changes: Map<String, String>, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val cur = queryPrivacySync(cred)
                if (cur == null) {
                    ui.post { onResult(false, "查询当前值失败，已中止（避免误清空其它开关）") }
                    return@Thread
                }
                val p = commonParams(cred, withMid = false)
                for (f in modifyFields) {
                    p[f] = changes[f] ?: cur[f] ?: "0"
                }
                BiliSign.signAndAdd(p, BiliSign.SPACE_APPKEY, BiliSign.SPACE_APPSEC)
                val resp = BiliClient.postForm(Const.SPACE_PRIVACY_MODIFY_URL, p, headers(cred))
                val obj = JSONObject(resp)
                val code = obj.optInt("code", -1)
                if (code == 0) {
                    val changed = changes.entries.joinToString(", ") { "${it.key}=${it.value}" }
                    ui.post { listener.log("空间隐私修改成功: $changed"); onResult(true, resp) }
                } else {
                    ui.post { listener.log("空间隐私修改失败(code=$code): $resp"); onResult(false, resp) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ui.post {
                    listener.log("空间隐私修改异常: ${e.message}")
                    onResult(false, e.message ?: "")
                }
            }
        }.start()
    }

    // ---------- 内部：同步实现（须在工作线程调用） ----------

    private fun queryPrivacySync(cred: Credentials): Map<String, String>? {
        return try {
            val p = commonParams(cred, withMid = true)
            BiliSign.signAndAdd(p, BiliSign.SPACE_APPKEY, BiliSign.SPACE_APPSEC)
            val resp = BiliClient.getQuery(Const.SPACE_SETTING_URL, p, headers(cred))
            val obj = JSONObject(resp)
            if (obj.optInt("code", -1) != 0) {
                listener.log("查询空间隐私失败: $resp")
                return null
            }
            val privacy = obj.optJSONObject("data")?.optJSONObject("privacy")
            if (privacy == null) {
                listener.log("查询空间隐私返回无 privacy 字段: $resp")
                return null
            }
            val map = LinkedHashMap<String, String>()
            val keys = privacy.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = privacy.optString(k)
            }
            map
        } catch (e: Exception) {
            e.printStackTrace()
            listener.log("查询空间隐私异常: ${e.message}")
            null
        }
    }

    private fun commonParams(cred: Credentials, withMid: Boolean): MutableMap<String, String> {
        val p = mutableMapOf(
            "access_key" to accessKeyOf(cred),
            "build" to "9020300",
            "c_locale" to "zh-Hans_CN",
            "channel" to "oppo_tv.danmaku.bili_20200623",
            "disable_rcmd" to "0",
            "mobi_app" to "android",
            "platform" to "android",
            "s_locale" to "zh-Hans_CN",
            "statistics" to Const.VERIFY_STAT,
            "ts" to Const.ts(),
        )
        if (withMid) p["mid"] = cred.mid
        return p
    }

    private fun headers(cred: Credentials): Map<String, String> {
        val fp = DeviceFingerprint.real()
        val h = mutableMapOf(
            "User-Agent" to Const.BILI_UA,
            "Referer" to "https://www.bilibili.com/",
            "Accept" to "*/*",
            "app-key" to "android64",
            "env" to "prod",
        )
        fp["buvid"]?.let { h["buvid"] = it }
        fp["bili_local_id"]?.let { h["fp_local"] = it }
        fp["device_id"]?.let { h["fp_remote"] = it }
        if (cred.mid.isNotEmpty()) h["x-bili-mid"] = cred.mid
        return h
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
