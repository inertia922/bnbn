package blbl.cat3399.core.api.video.app

import blbl.cat3399.BuildConfig
import blbl.cat3399.core.net.AppSigner
import blbl.cat3399.core.net.BiliClient
import org.json.JSONObject

internal interface AppVideoHttpTransport {
    suspend fun recommend(cursor: String?, isRefresh: Boolean): JSONObject
}

internal object BiliClientAppVideoHttpTransport : AppVideoHttpTransport {
    override suspend fun recommend(cursor: String?, isRefresh: Boolean): JSONObject {
        val session = BiliClient.prefs.appAuthSession ?: error("app_auth_session_missing")
        val params =
            mutableMapOf(
                "access_key" to session.accessKey,
                // pull=true on every page makes the server restart the feed; only pull when the
                // user asked for a refresh.
                "pull" to if (isRefresh) "true" else "false",
                "flush" to if (isRefresh) "1" else "0",
                "buvid" to BiliClient.prefs.deviceBuvid,
                "mobi_app" to "android",
                "platform" to "android",
                "network" to "wifi",
                "fnval" to "272",
                "fnver" to "0",
                "fourk" to "1",
                "s_locale" to "zh_CN",
                "login_event" to "0",
                "build" to BuildConfig.VERSION_CODE.toString(),
            )
        // The App feed paginates with the previous response's last item idx (a timestamp-like
        // cursor), not with a 0/1/2 page number.
        cursor?.trim()?.takeIf { it.isNotBlank() }?.let { params["idx"] = it }
        val signed = AppSigner.signQuery(params)
        val url = BiliClient.withQuery(FEED_INDEX_URL, signed)
        return BiliClient.getJson(url = url, headers = appHeaders(), noCookies = true)
    }

    private fun appHeaders(): Map<String, String> =
        mapOf(
            "User-Agent" to APP_USER_AGENT,
            "Referer" to "https://app.bilibili.com",
            "X-Blbl-Skip-Origin" to "1",
        )

    private const val FEED_INDEX_URL = "https://app.bilibili.com/x/v2/feed/index"
    private const val APP_USER_AGENT =
        "Mozilla/5.0 BiliDroid/2.2.0 (bbcallen@gmail.com) os/android model/2505DRP06G mobi_app/android_hd build/2020100 channel/yingyongbao innerVer/2020100 osVer/15 network/2"
}
