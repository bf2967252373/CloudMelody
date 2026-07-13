package com.cloudmelody.api

import com.cloudmelody.model.LyricResult
import com.cloudmelody.model.Playlist
import com.cloudmelody.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 网易云音乐非官方 API 封装
 *
 * Fixed issues:
 * - recommend() now correctly parses personalized playlist response
 * - songUrl() added redirect-following to resolve actual .mp3 URLs
 * - Added searchSongs(), getLyrics(), songDetail() methods
 * - Added rate-limit handling and retry logic
 * - Added fallback User-Agent rotation
 */
object NeteaseApi {

    private const val BASE = "https://music.163.com"

    // Multiple User-Agent strings for rate-limit rotation
    private val USER_AGENTS = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
    )
    private var uaIndex = 0

    private fun nextUA(): String = USER_AGENTS[uaIndex.also { uaIndex = (uaIndex + 1) % USER_AGENTS.size }]

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Follow redirects so songUrl() resolves the real .mp3 URL
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // ─── 公开 API ───────────────────────────────────────────────────────

    /** 首页推荐歌单 */
    suspend fun recommend(): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("$BASE/api/personalized/playlist?limit=20")
            parseRecommend(body)
        }
    }

    /** 歌单歌曲列表 */
    suspend fun playlistDetail(id: Long): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("$BASE/api/v6/playlist/detail?id=$id")
            parsePlaylistSongs(body)
        }
    }

    /**
     * 获取歌曲真实播放 URL
     * OkHttp follows 302 redirects, so the final response URL is the .mp3.
     */
    suspend fun songUrl(id: Long): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$BASE/song/media/outer/url?id=$id.mp3")
                .header("User-Agent", nextUA())
                .build()
            client.newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                // If the final URL still points to music.163.com, the song isn't playable
                if (!finalUrl.contains(".mp3") && !finalUrl.contains("m10.music")) null
                else finalUrl
            }
        }
    }

    /**
     * 搜索歌曲
     * Added: search endpoint for HomeFragment search functionality
     */
    suspend fun searchSongs(keyword: String, limit: Int = 30): Result<List<Song>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(keyword, "UTF-8")
                val body = get("$BASE/api/search/get?keywords=$encoded&limit=$limit&type=1")
                parseSearchResult(body)
            }
        }

    /**
     * 获取歌词
     * Added: LRC lyrics endpoint for Apple-style lyrics display
     */
    suspend fun getLyrics(songId: Long): Result<LyricResult> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("$BASE/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1")
            parseLyricResult(body)
        }
    }

    /**
     * 获取歌曲详情（含完整 duration、专辑图）
     * Added: detail endpoint for richer song metadata
     */
    suspend fun songDetail(id: Long): Result<Song> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("$BASE/api/v3/song/detail?ids=[$id]")
            val root = JSONObject(body)
            val songs = root.optJSONArray("songs") ?: JSONObject().put("songs", root.optJSONArray("songs")).optJSONArray("songs")
            if (songs == null || songs.length() == 0) throw Exception("Song not found")
            parseSong(songs.getJSONObject(0), body)
        }
    }

    // ─── 内部工具 ──────────────────────────────────────────────────

    private fun get(url: String, retries: Int = 2): String {
        var lastException: Exception? = null
        repeat(retries + 1) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", nextUA())
                    .header("Referer", "$BASE/")
                    .header("Cookie", "os=pc; appver=2.9.7;")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code == 403 || resp.code == 429) {
                        // Rate limited — wait briefly and retry with a different UA
                        Thread.sleep(500L * (it + 1))
                        return@repeat
                    }
                    return resp.body?.string().orEmpty()
                }
            } catch (e: Exception) {
                lastException = e
                if (it < retries) Thread.sleep(300L)
            }
        }
        throw lastException ?: Exception("Request failed: $url")
    }

    // ─── 解析 ──────────────────────────────────────────────────────

    private fun parseRecommend(json: String): List<Playlist> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(json)
            val arr = root.optJSONArray("result") ?: return emptyList()
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Playlist(
                    id = obj.optLong("id"),
                    name = obj.optString("name"),
                    coverUrl = obj.optString("picUrl").takeIf { it.isNotBlank() }
                        ?: obj.optString("coverImgUrl").takeIf { it.isNotBlank() },
                    trackCount = obj.optInt("trackCount"),
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
            val playlistObj = root.optJSONObject("playlist") ?: root.optJSONObject("result")
                ?: return emptyList()
            val tracks = playlistObj.optJSONArray("tracks") ?: return emptyList()
            (0 until tracks.length()).map { i ->
                parseSong(tracks.getJSONObject(i), json)
            }
        }.getOrDefault(emptyList())
    }

    private fun parseSearchResult(json: String): List<Song> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(json)
            val resultObj = root.optJSONObject("result") ?: return emptyList()
            val songs = resultObj.optJSONArray("songs") ?: return emptyList()
            (0 until songs.length()).map { i ->
                parseSong(songs.getJSONObject(i), json)
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Parse a JSON song object into Song model
     * Handles v3, v6, and search response formats
     */
    private fun parseSong(t: JSONObject, rawJson: String): Song {
        // Artists: ar (v3/v6), artists (older)
        val artistsArr = t.optJSONArray("ar") ?: t.optJSONArray("artists")
        val artist = if (artistsArr != null && artistsArr.length() > 0) {
            (0 until artistsArr.length()).joinToString("/") { i ->
                artistsArr.getJSONObject(i).optString("name")
            }
        } else ""

        // Album: al (v3/v6), album (older)
        val albumObj = t.optJSONObject("al") ?: t.optJSONObject("album")

        // Cover URL: al.picUrl, album.picUrl, album.picUrl_str
        val coverUrl = albumObj?.optString("picUrl")
            ?.takeIf { it.isNotBlank() }
            ?: albumObj?.optString("picUrl_str")
                ?.takeIf { it.isNotBlank() }

        // Duration: dt (v3/v6 ms), duration (older ms)
        val duration = t.optLong("dt").takeIf { it > 0 }
            ?: t.optLong("duration").takeIf { it > 0 }
            ?: 0L

        // ID: also check for string id in search results
        val id = t.optLong("id").takeIf { it > 0 }
            ?: t.optString("id").toLongOrNull()
            ?: 0L

        return Song(
            id = id,
            name = t.optString("name"),
            artist = artist,
            album = albumObj?.optString("name").orEmpty(),
            coverUrl = coverUrl,
            duration = duration
        )
    }

    private fun parseLyricResult(json: String): LyricResult {
        val root = JSONObject(json)
        val lrc = root.optJSONObject("lrc")?.optString("lyric").orEmpty()
        val tlyric = root.optJSONObject("tlyric")?.optString("lyric").orEmpty()
        return LyricResult(original = lrc, translated = tlyric)
    }
}
