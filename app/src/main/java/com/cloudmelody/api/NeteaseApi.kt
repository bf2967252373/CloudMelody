package com.cloudmelody.api

import com.cloudmelody.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NeteaseApi {

    private const val BASE = "https://music.163.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun get(): NeteaseApi = this

    /**
     * Fetch personalised / recommended playlists.
     * Falls back to an empty list on any error so the UI can display gracefully.
     */
    suspend fun recommend(): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE/api/personalized?limit=20")
                .header("User-Agent", USER_AGENT)
                .header("Referer", BASE)
                .header("Cookie", "os=pc")
                .build()
            val body = client.newCall(req).execute().use { it.body?.string() ?: "" }
            parseRecommend(body)
        }
    }

    private fun parseRecommend(json: String): List<Playlist> {
        val arr = JSONObject(json).optJSONArray("result") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Playlist(
                id = obj.optLong("id"),
                name = obj.optString("name"),
                coverImgUrl = obj.optString("picUrl").takeIf { it.isNotBlank() },
                trackCount = obj.optInt("trackCount"),
                description = obj.optString("copywriter").takeIf { it.isNotBlank() }
            )
        }
    }
}
