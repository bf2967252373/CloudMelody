package com.cloudmelody.api

import com.cloudmelody.model.Playlist
import com.cloudmelody.model.Song
import com.cloudmelody.model.LyricResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Lightweight NetEase Cloud Music unofficial API client.
 * Uses OkHttp directly (no Retrofit) to minimize APK size.
 *
 * API base: https://music.163.com
 * The public endpoints below are widely documented and used by
 * community reverse-engineering projects (NeteaseCloudMusicApi etc.).
 */
object NeteaseApi {

    private const val BASE = "https://music.163.com"
    // NeteaseCloudMusicApi proxy – deploy your own instance or use public one
    private const val PROXY_BASE = "https://netease-cloud-music-api-ten-eta.vercel.app"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent",
                        "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/109.0")
                    .header("Referer", "https://music.163.com/")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    // ──────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────

    /** Search songs by keyword. Returns up to [limit] results. */
    suspend fun searchSongs(keyword: String, limit: Int = 30): List<Song> =
        withContext(Dispatchers.IO) {
            val encoded = java.net.URLEncoder.encode(keyword, "UTF-8")
            val url = "$PROXY_BASE/search?keywords=$encoded&limit=$limit&type=1"
            val json = get(url)
            parseSongs(json)
        }

    /** Get recommended/personalized playlist for anonymous user. */
    suspend fun getTopPlaylist(limit: Int = 20): List<Playlist> =
        withContext(Dispatchers.IO) {
            val url = "$PROXY_BASE/top/playlist?limit=$limit&order=hot"
            val json = get(url)
            parsePlaylists(json)
        }

    /** Get songs in a playlist by id. */
    suspend fun getPlaylistDetail(id: Long): List<Song> =
        withContext(Dispatchers.IO) {
            val url = "$PROXY_BASE/playlist/track/all?id=$id&limit=50"
            val json = get(url)
            parseSongs(json)
        }

    /** Get streaming URL for a song. Returns null if not available. */
    suspend fun getSongUrl(id: Long, br: Int = 128000): String? =
        withContext(Dispatchers.IO) {
            val url = "$PROXY_BASE/song/url?id=$id&br=$br"
            val json = get(url)
            runCatching {
                JSONObject(json)
                    .getJSONArray("data")
                    .getJSONObject(0)
                    .getString("url")
                    .takeIf { it != "null" && it.isNotBlank() }
            }.getOrNull()
        }

    /** Fetch LRC lyrics + translated lyrics. */
    suspend fun getLyrics(id: Long): LyricResult =
        withContext(Dispatchers.IO) {
            val url = "$PROXY_BASE/lyric?id=$id"
            val json = get(url)
            parseLyrics(json)
        }

    /** Daily recommend songs (requires login; returns empty list if anonymous). */
    suspend fun getDailyRecommend(): List<Song> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = get("$PROXY_BASE/recommend/songs")
                val data = JSONObject(json).getJSONObject("data").getJSONArray("dailySongs")
                parseSongArray(data)
            }.getOrDefault(emptyList())
        }

    // ──────────────────────────────────────────────
    // Parsing helpers
    // ──────────────────────────────────────────────

    private fun parseSongs(json: String): List<Song> = runCatching {
        val root = JSONObject(json)
        // /search response
        if (root.has("result")) {
            val arr = root.getJSONObject("result").optJSONArray("songs") ?: return emptyList()
            return parseSongArray(arr)
        }
        // /playlist/track/all response
        if (root.has("songs")) {
            return parseSongArray(root.getJSONArray("songs"))
        }
        emptyList()
    }.getOrDefault(emptyList())

    private fun parseSongArray(arr: JSONArray): List<Song> {
        val list = mutableListOf<Song>()
        for (i in 0 until arr.length()) {
            runCatching {
                val o = arr.getJSONObject(i)
                val artists = buildString {
                    val ar = o.optJSONArray("ar") ?: o.optJSONArray("artists")
                    if (ar != null) {
                        for (j in 0 until ar.length()) {
                            if (j > 0) append(", ")
                            append(ar.getJSONObject(j).getString("name"))
                        }
                    }
                }
                val album = o.optJSONObject("al") ?: o.optJSONObject("album")
                val picUrl = album?.optString("picUrl") ?: ""
                val albumName = album?.optString("name") ?: ""
                list.add(
                    Song(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        artist = artists,
                        album = albumName,
                        coverUrl = if (picUrl.isNullOrBlank()) null else picUrl,
                        duration = o.optLong("dt", 0L)
                    )
                )
            }
        }
        return list
    }

    private fun parsePlaylists(json: String): List<Playlist> = runCatching {
        val arr = JSONObject(json).getJSONArray("playlists")
        val list = mutableListOf<Playlist>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Playlist(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    coverUrl = o.optString("coverImgUrl"),
                    trackCount = o.optInt("trackCount", 0),
                    playCount = o.optLong("playCount", 0L)
                )
            )
        }
        list
    }.getOrDefault(emptyList())

    private fun parseLyrics(json: String): LyricResult = runCatching {
        val root = JSONObject(json)
        val lrc = root.optJSONObject("lrc")?.optString("lyric") ?: ""
        val tlyric = root.optJSONObject("tlyric")?.optString("lyric") ?: ""
        LyricResult(original = lrc, translated = tlyric)
    }.getOrDefault(LyricResult("", ""))

    // ──────────────────────────────────────────────
    // HTTP helper
    // ──────────────────────────────────────────────

    private fun get(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return response.body?.string() ?: ""
        }
    }
}
