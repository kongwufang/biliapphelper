package com.bilidebug.app

import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * 空间隐私设置页：进入自动查询并回填开关（静默），提交时全量下发。
 */
class PrivacyActivity : BaseActivity() {

    private lateinit var llPrivacy: LinearLayout
    private lateinit var btnQueryPrivacy: MaterialButton
    private lateinit var btnApplyPrivacy: MaterialButton
    private lateinit var tvPrivacyCount: TextView

    private val spaceManager = SpaceManager(this)
    private var cred: Credentials? = null

    /** 可改字段的中文标签（顺序即 UI 顺序） */
    private val labels = linkedMapOf(
        "bangumi" to "追番",
        "charge_video" to "充电专属视频",
        "close_space_medal" to "关闭空间勋章",
        "coins_video" to "投币视频",
        "comic" to "追漫",
        "disable_following" to "隐藏关注列表",
        "disable_show_fans" to "隐藏粉丝列表",
        "disable_show_school" to "隐藏学校信息",
        "dress_up" to "装扮",
        "fav_video" to "收藏的视频",
        "groups" to "我的分组",
        "lesson_video" to "课程视频",
        "likes_video" to "点赞的视频",
        "live_playback" to "直播回放",
        "only_show_wearing" to "仅展示佩戴勋章",
        "played_game" to "玩过的游戏",
        "submited_video" to "投稿视频",
        "tags" to "标签",
    )

    private val checks = LinkedHashMap<String, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)

        llPrivacy = findViewById(R.id.llPrivacy)
        btnQueryPrivacy = findViewById(R.id.btnQueryPrivacy)
        btnApplyPrivacy = findViewById(R.id.btnApplyPrivacy)
        tvPrivacyCount = findViewById(R.id.tvPrivacyCount)

        cred = CredentialStore.load(this)
        buildChecks()

        btnQueryPrivacy.setOnClickListener { query() }
        btnApplyPrivacy.setOnClickListener { apply() }

        if (cred != null) {
            query(silent = true)
        } else {
            tvPrivacyCount.text = "未登录"
            result("未登录，请先到首页完成密码登录", false)
        }
    }

    private fun buildChecks() {
        llPrivacy.removeAllViews()
        checks.clear()
        for ((field, label) in labels) {
            val cb = CheckBox(this)
            cb.text = "$label（$field）"
            cb.textSize = 14f
            cb.setTextColor(getColor(R.color.text_primary))
            cb.setPadding(8, 8, 8, 8)
            llPrivacy.addView(cb)
            checks[field] = cb
        }
    }

    private fun query(silent: Boolean = false) {
        val c = cred ?: return
        btnQueryPrivacy.isEnabled = false
        if (!silent) tvPrivacyCount.text = "正在查询..."
        log("查询空间隐私设置...")
        spaceManager.queryPrivacy(c) { map ->
            btnQueryPrivacy.isEnabled = true
            if (map == null) {
                tvPrivacyCount.text = "查询失败"
                if (silent) log("空间隐私查询失败") else result("空间隐私查询失败", false)
                return@queryPrivacy
            }
            tvPrivacyCount.text = "共 ${map.size} 项，已回填"
            for ((k, cb) in checks) map[k]?.let { cb.isChecked = it == "1" }
            if (silent) log("空间隐私查询成功，共 ${map.size} 项")
            else result("空间隐私查询成功，共 ${map.size} 项", true)
        }
    }

    private fun apply() {
        val c = cred ?: return
        val changes = checks.mapValues { if (it.value.isChecked) "1" else "0" }
        btnApplyPrivacy.isEnabled = false
        log("提交空间隐私修改（${changes.size} 项，全量）...")
        spaceManager.modifyPrivacy(c, changes) { ok, resp ->
            btnApplyPrivacy.isEnabled = true
            if (ok) {
                result("空间隐私修改成功", true)
                query(silent = true)
            } else {
                result("空间隐私修改失败：$resp", false)
            }
        }
    }
}
