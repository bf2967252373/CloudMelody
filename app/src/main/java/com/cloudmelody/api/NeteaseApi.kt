package com.cloudmelody.api

import com.cloudmelody.model.Playlist
import com.cloudmelody.model.Song
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

    /** 推荐歌单 */
    suspend fun recommend(): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE/api/personalized/playlist?limit=20")
                .header("User-Agent", USER_AGENT)
                .header("Referer", BASE)
                .header("Cookie", "os=pc")
                .build()
            val body = client.newCall(req).execute().use { it.body?.string().orEmpty() }
            parseRecommend(body)
        }
    }

    /** 歌单详情中的歌曲列表 */
    suspend fun playlistDetail(id: Long): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE/api/v6/playlist/detail?id=$id")
                .header("User-Agent", USER_AGENT)
                .header("Referer", BASE)
                .header("Cookie", "os=pc")
                .build()
            val body = client.newCall(req).execute().use { it.body?.string().orEmpty() }
            parsePlaylistSongs(body)
        }
    }

    /** 获取真实播放 URL */
    suspend fun songUrl(id: Long): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE/song/media/outer/url?id=$id.mp3")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                // 302 重定向后的 url 即为真实地址
                resp.request.url.toString().takeIf { it.contains(".mp3") || it.contains("music") }
            }
        }
    }

    // ─── 解析 ──────────────────────────────────────────

    private fun parseRecommend(json: String): List<Playlist> {
        if (json.isBlank()) return emptyList()
        val root = JSONObject(json)
        val arr = root.optJSONArray("result") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Playlist(
                id = obj.optLong("id"),
                name = obj.optString("name"),
                coverUrl = obj.optString("picUrl").takeIf { it.isNotBlank() },
                trackCount = obj.optInt("trackCount"),
                playCount = obj.optLong("playCount"),
                description = obj.optString("copywriter").takeIf { it.isNotBlank() }
            )
        }
    }

    private fun parsePlaylistSongs(json: String): List<Song> {
        if (json.isBlank()) return emptyList()
        val root = JSONObject(json)
        val playlistObj = root.optJSONObject("playlist") ?: root.optJSONObject("result")
        ?: return emptyList()
        val tracks = playlistObj.optJSONArray("tracks") ?: return emptyList()
        return (0 until tracks.length()).map { i ->
            val t = tracks.getJSONObject(i)
            val artistsArr = t.optJSONArray("ar") ?: t.optJSONArray("artists")
            val artist = if (artistsArr != null && artistsArr.length() > 0) {
                (0 until artistsArr.length()).joinToString("/") {
                    artistsArr.getJSONObject(it).optString("name")
                }
            } else ""
            val albumObj = t.optJSONObject("al") ?: t.optJSONObject("album")
            Song(
                id = t.optLong("id"),
                name = t.optString("name"),
                artist = artist,
                album = albumObj?.optString("name").orEmpty(),
                coverUrl = albumObj?.optString("picUrl"),
                duration = t.optLong("dt", t.optLong("duration"))
            )
        }
    }
}
