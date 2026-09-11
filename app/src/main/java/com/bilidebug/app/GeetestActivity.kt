package com.bilidebug.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/** 极验人机验证：WebView 加载 gt.js，成功后通过 JS 桥自动回传 validate 并结束。 */
class GeetestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_geetest)

        val gt = intent.getStringExtra("gt") ?: ""
        val challenge = intent.getStringExtra("challenge") ?: ""

        val webview: WebView = findViewById(R.id.webview)
        setupWebView(webview)

        val bridge = JsBridge { validate ->
            runOnUiThread {
                val data = Intent().putExtra("validate", validate)
                setResult(RESULT_OK, data)
                finish()
            }
        }
        webview.addJavascriptInterface(bridge, "AndroidBridge")

        val html = geetestHtml(gt, challenge)
        // baseURL 用 https 域名，保证极验脚本在 https 环境下初始化（file:// 会失效）
        webview.loadDataWithBaseURL("https://static.geetest.com/", html, "text/html", "UTF-8", null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(w: WebView) {
        val s: WebSettings = w.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.setSupportZoom(false)
        s.javaScriptCanOpenWindowsAutomatically = true
        w.webViewClient = WebViewClient()
        w.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.d("GeetestWeb", "${cm.messageLevel()}: ${cm.message()}")
                return true
            }
        }
    }

    class JsBridge(private val callback: (String) -> Unit) {
        @JavascriptInterface
        fun onValidate(validate: String?) {
            if (!validate.isNullOrEmpty()) callback(validate)
        }
    }

    private fun geetestHtml(gt: String, challenge: String): String {
        val g = gt.replace("\\", "\\\\").replace("'", "\\'")
        val c = challenge.replace("\\", "\\\\").replace("'", "\\'")
        return """<!DOCTYPE html>
<html><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<title>极验人机验证</title>
<script src="https://static.geetest.com/static/tools/gt.js"></script>
</head><body style="font-family:sans-serif;padding:16px">
<h3>极验人机验证</h3>
<button id="btn" onclick="start()" style="padding:10px 20px;font-size:16px">开始验证</button>
<div id="captcha"></div>
<div id="result" style="margin-top:12px;color:red;word-break:break-all"></div>
<script>
var GT = '$g', CH = '$c';
function start(){
  document.getElementById('btn').disabled = true;
  initGeetest({gt:GT, challenge:CH, offline:false, new_captcha:true,
               https:true, product:"embed", lang:"zh-cn", width:"100%"},
    function(captchaObj){
      captchaObj.appendTo("#captcha");
      captchaObj.onReady(function(){ captchaObj.verify(); });
      captchaObj.onSuccess(function(){
        var v = captchaObj.getValidate();
        console.log("[geetest] onSuccess validate=" + (v ? v.geetest_validate : "null"));
        document.getElementById('result').innerHTML = "验证成功，正在返回...";
        if (window.AndroidBridge) {
          window.AndroidBridge.onValidate(v.geetest_validate);
        } else {
          document.getElementById('result').innerHTML = "错误：JS 桥未注入";
        }
      });
      captchaObj.onError(function(e){
        document.getElementById('result').innerHTML = "验证出错: " + JSON.stringify(e);
        document.getElementById('btn').disabled = false;
      });
    });
}
</script></body></html>"""
    }
}
