package com.bilidebug.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** 登录凭据持久化：SharedPreferences 存储，App 退出后凭据不丢失。 */
object CredentialStore {
    private const val PREFS = "bili_credentials"
    private const val KEY_CRED = "cred_json"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(ctx: Context, cred: Credentials) {
        val obj = JSONObject().apply {
            put("mid", cred.mid)
            put("access_token", cred.accessToken)
            put("refresh_token", cred.refreshToken)
            put("sessdata", cred.sessdata)
            put("bili_jct", cred.biliJct)
        }
        prefs(ctx).edit().putString(KEY_CRED, obj.toString()).apply()
    }

    fun load(ctx: Context): Credentials? {
        val raw = prefs(ctx).getString(KEY_CRED, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val cred = Credentials(
                mid = obj.optString("mid"),
                accessToken = obj.optString("access_token"),
                refreshToken = obj.optString("refresh_token"),
                sessdata = obj.optString("sessdata"),
                biliJct = obj.optString("bili_jct"),
            )
            if (cred.accessToken.isEmpty()) null else cred
        } catch (_: Exception) {
            null
        }
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_CRED).apply()
    }
}
