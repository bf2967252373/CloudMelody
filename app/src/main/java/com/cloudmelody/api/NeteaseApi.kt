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

    // ─── 公开 API ───────────────────────────────────────────────────────────────

    /** 推荐歌单（首页使用） */
    suspend fun recommend(): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("$BASE/api/personalized/playlist?limit=20")
            parseRecommend(body)
        }
    }

    /** 歌单详情中的歌曲列表 */
    suspend fun playlistDetail(id: Long): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("$BASE/api/v6/playlist/detail?id=$id")
            parsePlaylistSongs(body)
        }
    }

    /**
     * 获取真实播放 URL（网易云外链方案）
     * 注意：该接口受版权限制，部分歌曲可能返回 null。
     */
    suspend fun songUrl(id: Long): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE/song/media/outer/url?id=$id.mp3")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                finalUrl.takeIf { it.contains(".mp3") || it.contains("music") }
            }
        }
    }

    // ─── 内部工具 ────────────────────────────────────────────────────────────────

    private fun get(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", BASE)
            .header("Cookie", "os=pc")
            .build()
        return client.newCall(req).execute().use { it.body?.string().orEmpty() }
    }

    // ─── 解析 ─────────────────────────────────────────────────────────────────────

    private fun parseRecommend(json: String): List<Playlist> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(json)
            val arr = root.optJSONArray("result") ?: return emptyList()
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Playlist(
                    id        = obj.optLong("id"),
                    name      = obj.optString("name"),
                    // Bug 修复：原代码用 coverImgUrl，但接口实际返回 picUrl
                    coverUrl  = obj.optString("picUrl").takeIf { it.isNotBlank() },
                    trackCount = obj.optInt("trackCount"),
                    // Bug 修复：原代码未解析 playCount
                    playCount = obj.optLong("playCount"),
                    description = obj.optString("copywriter").takeIf { it.isNotBlank() }
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun parsePlaylistSongs(json: String): List<Song> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(json)
            // v6 接口返回 playlist.tracks，兼容 result.tracks
            val playlistObj = root.optJSONObject("playlist") ?: root.optJSONObject("result")
            ?: return emptyList()
            val tracks = playlistObj.optJSONArray("tracks") ?: return emptyList()
            (0 until tracks.length()).map { i ->
                val t = tracks.getJSONObject(i)
                // ar = artists array (v6), artists (v3)
                val artistsArr = t.optJSONArray("ar") ?: t.optJSONArray("artists")
                val artist = if (artistsArr != null && artistsArr.length() > 0) {
                    (0 until artistsArr.length()).joinToString("/") {
                        artistsArr.getJSONObject(it).optString("name")
                    }
                } else ""
                // al = album (v6), album (v3)
                val albumObj = t.optJSONObject("al") ?: t.optJSONObject("album")
                Song(
                    id       = t.optLong("id"),
                    name     = t.optString("name"),
                    artist   = artist,
                    album    = albumObj?.optString("name").orEmpty(),
                    // Bug 修复：统一使用 coverUrl，从 picUrl 字段读取
                    coverUrl = albumObj?.optString("picUrl"),
                    // Bug 修复：dt = 时长(ms) in v6, duration in v3
                    duration = t.optLong("dt", t.optLong("duration"))
                )
            }
        }.getOrDefault(emptyList())
    }
}
