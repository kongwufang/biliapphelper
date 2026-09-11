package com.bilidebug.app

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.net.CookieManager
import java.util.concurrent.TimeUnit

/** HTTP 封装：统一管理 Cookie，提供 GET / 表单POST / 查询串POST 三种请求。 */
object BiliClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .cookieJar(JavaNetCookieJar(CookieManager()))
        .build()

    private fun execute(url: String, method: String, body: RequestBody?, headers: Map<String, String>): String {
        val rb = Request.Builder().url(url)
        when (method) {
            "GET" -> rb.get()
            "POST" -> rb.post(body ?: RequestBody.create(null, ByteArray(0)))
        }
        for ((k, v) in headers) rb.header(k, v)
        client.newCall(rb.build()).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            return text
        }
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        execute(url, "GET", null, headers)

    /** 表单 POST：参数进 body（application/x-www-form-urlencoded） */
    fun postForm(url: String, params: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
        val fb = FormBody.Builder()
        for ((k, v) in params) fb.add(k, v)
        return execute(url, "POST", fb.build(), headers)
    }

    /** 查询串 POST：参数进 URL query，body 为空（B 站 TV 接口 / web check/scene 风格） */
    fun postQuery(url: String, params: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
        val u = url.toHttpUrlOrNull() ?: return "{\"code\":-1,\"message\":\"bad url\"}"
        val builder = u.newBuilder()
        for ((k, v) in params) builder.addQueryParameter(k, v)
        return execute(builder.build().toString(), "POST", null, headers)
    }

    /** GET 带 query 参数 */
    fun getQuery(url: String, params: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
        val u = url.toHttpUrlOrNull() ?: return "{\"code\":-1,\"message\":\"bad url\"}"
        val builder = u.newBuilder()
        for ((k, v) in params) builder.addQueryParameter(k, v)
        return execute(builder.build().toString(), "GET", null, headers)
    }
}
