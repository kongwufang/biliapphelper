package com.bilidebug.app

import java.security.MessageDigest

object BiliSign {
    const val APP_APPKEY = "783bbb7264451d82"
    const val APP_APPSEC = "2653583c8873dea268ab9386918b1d65"
    const val TV_APPKEY = "dfca71928277209b"
    const val TV_APPSEC = "b5475a8825547a4fc26c7d518eaaa02e"

    // 空间隐私设置接口的 appkey/appsec（HAR 实测验证通过）
    const val SPACE_APPKEY = "1d8b6e7d45233436"
    const val SPACE_APPSEC = "560c52ccd288fed045859ed18bffd973"

    fun md5(s: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            sb.append("0123456789abcdef"[v shr 4])
            sb.append("0123456789abcdef"[v and 0xf])
        }
        return sb.toString()
    }

    /** 等价于 Python urllib.parse.quote_plus */
    fun quote(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xff
            val ch = c.toChar()
            if ((ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9')
                || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                sb.append(ch)
            } else if (ch == ' ') {
                sb.append('+')
            } else {
                sb.append('%')
                sb.append("0123456789ABCDEF"[c shr 4])
                sb.append("0123456789ABCDEF"[c and 0xf])
            }
        }
        return sb.toString()
    }

    /** sign = md5(urlencode(sorted(params)) + appsec)，params 需已含 appkey */
    fun sign(params: Map<String, String>, appsec: String): String {
        val sorted = params.toSortedMap()
        val sb = StringBuilder()
        for ((k, v) in sorted) {
            if (sb.isNotEmpty()) sb.append('&')
            sb.append(quote(k)).append('=').append(quote(v))
        }
        sb.append(appsec)
        return md5(sb.toString())
    }

    /** 原地写入 appkey 并计算 sign，再把 sign 写回（对应 Python calc_sign 的副作用 + p["sign"]=...） */
    fun signAndAdd(params: MutableMap<String, String>, appkey: String, appsec: String) {
        params["appkey"] = appkey
        params["sign"] = sign(params, appsec)
    }
}
