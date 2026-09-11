package com.bilidebug.app

import android.content.Context
import org.json.JSONObject
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设备指纹。持久标识硬编码，硬件指纹（device_meta/dt）从 assets/device_fp.json 读取。
 *
 * 关键结论（来自逆向）：
 *  - 硬件指纹 device_meta(约40KB hex) / dt(base64) 不可随机化，否则服务端直接拒绝。
 *  - 随机换持久标识（buvid/device_id/local_id/login_session_id）会触发 deviceVerify(status=3)。
 */
object DeviceFingerprint {
    private val rng = SecureRandom()

    private var meta: String = ""
    private var dt: String = ""

    fun init(context: Context) {
        try {
            val text = context.assets.open("device_fp.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(text)
            meta = obj.optString("device_meta", "")
            dt = obj.optString("dt", "")
        } catch (_: Exception) {
            meta = ""
            dt = ""
        }
    }

    private fun randomHex(nBytes: Int): String {
        val bytes = ByteArray(nBytes)
        rng.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** 真实设备指纹（默认，登录通常直接成功 status=0） */
    fun real(): Map<String, String> = mapOf(
        "buvid" to "XX49BED415CFE6C75952C223631B8B788EF31",
        "local_id" to "XX49BED415CFE6C75952C223631B8B788EF31",
        "device_id" to "3cde2f599af14cd71b262217a8c69bb620260905010729d5d411d26e13ee717a",
        "bili_local_id" to "3cde2f599af14cd71b262217a8c69bb620260905010729d5d411d26e13ee717a",
        "login_session_id" to "b0011e88b65ff1a9ca3ee254f6bc6d31",
        "channel" to "oppo",
        "device" to "phone",
        "device_name" to "OnePlusPJZ110",
        "device_platform" to "Android16OnePlusPJZ110",
        "spm_id" to "",
        "disable_rcmd" to "0",
        "device_meta" to meta,
        "dt" to dt,
    )

    /** 陌生设备指纹：随机换持久标识，硬件指纹保持不动（用于触发风控验证） */
    fun newDevice(): Map<String, String> {
        val base = real().toMutableMap()
        val buvid = "XX" + randomHex(16).uppercase(Locale.US)          // XX + 32hex
        val uid = randomHex(16)                                       // 32hex
        val stamp = SimpleDateFormat("yyyyMMddHH", Locale.US).format(Date()) // 10位
        val deviceId = uid + stamp + randomHex(11)                    // 64hex
        base["buvid"] = buvid
        base["local_id"] = buvid
        base["device_id"] = deviceId
        base["bili_local_id"] = deviceId
        base["login_session_id"] = randomHex(16)
        return base
    }
}
