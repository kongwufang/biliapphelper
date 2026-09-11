package com.bilidebug.app

object Const {
    const val BASE = "https://passport.bilibili.com"

    // 密码登录
    const val KEY_URL = "$BASE/x/passport-login/web/key"
    const val LOGIN_URL = "$BASE/x/passport-login/oauth2/login"
    const val ACCESS_TOKEN_URL = "$BASE/x/passport-login/oauth2/access_token"

    // access_token 检查 / 刷新
    const val INFO_URL = "$BASE/x/passport-login/oauth2/info"
    const val REFRESH_URL = "$BASE/x/passport-login/oauth2/refresh_token"

    // Web 扫码
    const val WEB_GENERATE = "$BASE/x/passport-login/web/qrcode/generate"
    const val WEB_POLL = "$BASE/x/passport-login/web/qrcode/poll"
    const val WEB_CHECK = "$BASE/x/passport-login/web/qrcode/check"
    const val WEB_SCENE = "$BASE/x/passport-login/web/qrcode/scene"
    const val WEB_CONFIRM = "$BASE/x/passport-login/web/qrcode/confirm"

    // TV 扫码
    const val TV_AUTH_CODE = "$BASE/x/passport-tv-login/qrcode/auth_code"
    const val TV_CONFIRM = "$BASE/x/passport-tv-login/h5/qrcode/confirm"
    const val TV_POLL = "$BASE/x/passport-tv-login/qrcode/poll"

    // 空间隐私设置（HAR 实测 HTTP/2 authority = api.bilibili.com）
    const val SPACE_SETTING_URL = "https://api.bilibili.com/x/space/setting/app"
    const val SPACE_PRIVACY_MODIFY_URL = "https://api.bilibili.com/x/space/privacy/batch/modify"

    // 账号安全中心：登录保护(二次验证) / 登录设备 / 一键退登
    const val SAFE_USER_SETTING = "$BASE/x/safecenter/user_setting"
    const val SAFE_SETTING_ENABLE = "$BASE/x/safecenter/user_setting/enable"
    const val SAFE_SETTING_DISABLE = "$BASE/x/safecenter/user_setting/disable"
    const val SAFE_LOGIN_DEVICES = "$BASE/x/safecenter/user_login_devices"
    const val SAFE_DEVICE_DELETE = "$BASE/x/safecenter/user_device/delete/v2"
    const val SAFE_WEB_COOKIE_DEL = "$BASE/x/safecenter/web_cookie/del"

    // 风控 / 验证
    const val CAPTCHA_PRE = "$BASE/x/safecenter/captcha/pre"
    const val SMS_SEND = "$BASE/x/safecenter/common/sms/send"
    const val DEVICE_VERIFY = "$BASE/x/safecenter/user_device/verify"
    const val USER_INFO = "$BASE/x/safecenter/user/info"
    const val TEL_VERIFY = "$BASE/x/safecenter/login/tel/verify"

    // UA
    val DALVIK_UA = "Dalvik/2.1.0 (Linux; U; Android 16; PJZ110 Build/BP2A.250605.015) " +
            "9.2.0 os/android model/PJZ110 mobi_app/android build/9020300 " +
            "channel/oppo_tv.danmaku.bili_20200623 innerVer/9020310 osVer/16 " +
            "network/2 engine:ignet_http"
    val BILI_UA = "Mozilla/5.0 BiliDroid/9.2.0 (bbcallen@gmail.com) 9.2.0 " +
            "os/android model/PJZ110 mobi_app/android build/9020300 " +
            "channel/oppo_tv.danmaku.bili_20200623 innerVer/9020310 osVer/16 network/2"
    val WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // statistics JSON（登录无 abtest，验证/扫码有 abtest）
    const val LOGIN_STAT = """{"appId":1,"platform":3,"version":"9.2.0"}"""
    const val VERIFY_STAT = """{"appId":1,"platform":3,"version":"9.2.0","abtest":""}"""

    fun loginHeaders() = mapOf(
        "User-Agent" to DALVIK_UA,
        "Referer" to "https://www.bilibili.com/",
        "app-key" to "android64",
        "env" to "prod",
        "Content-Type" to "application/x-www-form-urlencoded",
    )

    fun verifyHeaders() = mapOf(
        "User-Agent" to BILI_UA,
        "Referer" to "https://www.bilibili.com/",
        "app-key" to "android64",
        "env" to "prod",
        "Content-Type" to "application/x-www-form-urlencoded",
    )

    fun ts() = (System.currentTimeMillis() / 1000).toString()
}
