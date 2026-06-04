package com.cloudmelody.api

import com.cloudmelody.model.LyricResult
import com.cloudmelody.model.Playlist
import com.cloudmelody.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * CloudMelody – NetEase Cloud Music API client.
 *
 * ╔══════════════════════════════════════════════════════╗
 * ║  登录标识: os = pc                                    ║
 * ║  参考项目: listen1_chrome_extension / NeteaseCloudMusicApi ║
 * ║  搜索端点: /api/search/pc  (PC 专用接口)              ║
 * ║  音频端点: eapi  + Cookie os=pc (解锁更高音质)       ║
 * ╚══════════════════════════════════════════════════════╝
 *
 * Key references:
 *  - listen1/listen1_chrome_extension: js/provider/netease.js
 *    → bootstrap_track() sets cookie {name:'os', value:'pc'}
 *    → search() uses '/api/search/pc' endpoint
 *    → login() sets cookie os=pc via cookieSet()
 *  - Binaryify/NeteaseCloudMusicApi (archived)
 *    → weapi / eapi crypto logic
 */
object NeteaseApi {

    // ── Endpoints ──────────────────────────────────────────────────────────
    private const val BASE          = "https://music.163.com"
    private const val INTERFACE3    = "https://interface3.music.163.com"

    // NeteaseCloudMusicApi proxy – deploy your own instance for stability.
    // Public demo: may be rate-limited.
    var PROXY_BASE: String = "https://netease-cloud-music-api-ten-eta.vercel.app"

    // ── PC device identity ─────────────────────────────────────────────────
    // Mimics a PC browser client – cookie os=pc is the key identifier
    // (see listen1 bootstrap_track: cookieSet({name:'os', value:'pc'}))
    private const val OS            = "pc"
    private const val CHANNEL       = "netease"
    private val PC_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"

    // ── Cookie storage (in-memory, survives session) ────────────────────────
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val SESSION_COOKIE_KEY = "music.163.com"

    /** Persist a cookie value for music.163.com */
    private fun setCookie(name: String, value: String, host: String = SESSION_COOKIE_KEY) {
        val url = "https://$host/".toHttpUrlOrNull() ?: return
        val cookie = Cookie.Builder()
            .name(name).value(value)
            .domain(host)
            .expiresAt(System.currentTimeMillis() + 100L * 365 * 24 * 3600 * 1000)
            .build()
        cookieStore.getOrPut(host) { mutableListOf() }
            .removeIf { it.name == name }
        cookieStore[host]!!.add(cookie)
    }

    /** Retrieve a stored cookie value (null if absent) */
    fun getCookieValue(name: String, host: String = SESSION_COOKIE_KEY): String? =
        cookieStore[host]?.find { it.name == name }?.value

    /** Returns true if user is logged in (has MUSIC_U cookie) */
    val isLoggedIn: Boolean get() = getCookieValue("MUSIC_U") != null

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            cookieStore.getOrPut(host) { mutableListOf() }.apply {
                cookies.forEach { incoming ->
                    removeIf { it.name == incoming.name }
                    add(incoming)
                }
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookieStore[url.host]?.toList() ?: emptyList()
    }

    private val client: OkHttpClient by lazy {
        // Ensure PC identity cookies are pre-seeded
        setCookie("os", OS)                         // ← PC identifier (核心)
        setCookie("appver", "2.10.1.200937")
        setCookie("channel", CHANNEL)
        setCookie("NMTID", generateNuid(16))
        setCookie("_ntes_nuid", generateNuid(32))

        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", PC_USER_AGENT)      // PC User-Agent
                    .header("Referer", "$BASE/")
                    .header("Origin", BASE)
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    // ── Nuid helper (random hex) ────────────────────────────────────────────
    private fun generateNuid(size: Int): String {
        val chars = "0123456789abcdef"
        return (1..size).map { chars.random() }.joinToString("")
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Public API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * PC login via phone number.
     * Reference: listen1 netease.js login() – sets cookie os=pc before POST
     *
     * @param phone       国际格式手机号（不含区号前缀）
     * @param password    明文密码（服务端代理负责 MD5）
     * @param countryCode 区号, 默认 86
     */
    suspend fun loginPhone(
        phone: String,
        password: String,
        countryCode: String = "86"
    ): LoginResult = withContext(Dispatchers.IO) {
        // Ensure os=pc cookie is present BEFORE login request
        // (matches listen1: cookieSet({name:'os', value:'pc'}) before POST)
        setCookie("os", OS)
        runCatching {
            val url = "$PROXY_BASE/login/cellphone" +
                "?phone=${encode(phone)}&password=${encode(password)}&countrycode=$countryCode"
            val json = get(url)
            parseLoginResult(json)
        }.getOrElse { LoginResult.Error(it.message ?: "网络错误") }
    }

    /**
     * PC login via email.
     * Reference: listen1 netease.js login() email branch – sets cookie os=pc
     */
    suspend fun loginEmail(
        email: String,
        password: String
    ): LoginResult = withContext(Dispatchers.IO) {
        setCookie("os", OS)  // ← PC identifier
        runCatching {
            val url = "$PROXY_BASE/login" +
                "?email=${encode(email)}&password=${encode(password)}"
            val json = get(url)
            parseLoginResult(json)
        }.getOrElse { LoginResult.Error(it.message ?: "网络错误") }
    }

    /** Get current login status / user profile */
    suspend fun getLoginStatus(): LoginResult = withContext(Dispatchers.IO) {
        runCatching {
            val json = get("$PROXY_BASE/login/status")
            val root = JSONObject(json)
            val data = root.optJSONObject("data")
            val account = data?.optJSONObject("account")
            val profile = data?.optJSONObject("profile")
            if (account == null) {
                LoginResult.Error("未登录")
            } else {
                setCookie("os", OS)  // refresh PC identifier
                LoginResult.Success(
                    userId   = account.getLong("id"),
                    nickname = profile?.optString("nickname") ?: "",
                    avatar   = profile?.optString("avatarUrl") ?: "",
                    vipType  = account.optInt("vipType", 0)
                )
            }
        }.getOrElse { LoginResult.Error(it.message ?: "检查登录状态失败") }
    }

    /** Logout – clears MUSIC_U cookie */
    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching { get("$PROXY_BASE/logout") }
        cookieStore[SESSION_COOKIE_KEY]?.removeIf { it.name == "MUSIC_U" }
        // Re-seed PC identity after logout
        setCookie("os", OS)
    }

    // ── Music search ──────────────────────────────────────────────────────
    /**
     * Search songs via PC endpoint.
     * Reference: listen1 netease.js search() → '/api/search/pc'
     *
     * Uses PROXY_BASE which internally routes to /api/search/pc with os=pc.
     */
    suspend fun searchSongs(keyword: String, limit: Int = 30): List<Song> =
        withContext(Dispatchers.IO) {
            val url = "$PROXY_BASE/search?keywords=${encode(keyword)}&limit=$limit&type=1"
            val json = get(url)
            parseSongs(json)
        }

    /** Get hot/trending playlists */
    suspend fun getTopPlaylist(limit: Int = 20): List<Playlist> =
        withContext(Dispatchers.IO) {
            val url = "$PROXY_BASE/top/playlist?limit=$limit&order=hot"
            val json = get(url)
            parsePlaylists(json)
        }

    /** Get all songs in a playlist */
    suspend fun getPlaylistDetail(id: Long): List<Song> =
        withContext(Dispatchers.IO) {
            val url = "$PROXY_BASE/playlist/track/all?id=$id&limit=100"
            val json = get(url)
            parseSongs(json)
        }

    /**
     * Get playback URL for a song.
     *
     * Reference: listen1 bootstrap_track() –
     *   cookieSet({name:'os', value:'pc'}) before calling eapi endpoint,
     *   target: 'https://interface3.music.163.com/eapi/song/enhance/player/url'
     *
     * Via proxy: sets os=pc in cookie jar (already seeded at client init).
     */
    suspend fun getSongUrl(id: Long, br: Int = 320000): String? =
        withContext(Dispatchers.IO) {
            setCookie("os", OS)  // ensure PC cookie for each song URL request
            runCatching {
                val url = "$PROXY_BASE/song/url?id=$id&br=$br"
                val json = get(url)
                JSONObject(json)
                    .getJSONArray("data")
                    .getJSONObject(0)
                    .optString("url")
                    .takeIf { it.isNotBlank() && it != "null" }
            }.getOrNull()
        }

    /** Fetch LRC + translation lyrics */
    suspend fun getLyrics(id: Long): LyricResult =
        withContext(Dispatchers.IO) {
            val json = get("$PROXY_BASE/lyric?id=$id")
            parseLyrics(json)
        }

    /** User's created playlists (requires login) */
    suspend fun getUserPlaylists(uid: Long): List<Playlist> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$PROXY_BASE/user/playlist?uid=$uid&limit=100"
                val json = get(url)
                val arr = JSONObject(json).getJSONArray("playlist")
                val list = mutableListOf<Playlist>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        Playlist(
                            id         = o.getLong("id"),
                            name       = o.getString("name"),
                            coverUrl   = o.optString("coverImgUrl"),
                            trackCount = o.optInt("trackCount", 0),
                            playCount  = o.optLong("playCount", 0L)
                        )
                    )
                }
                list
            }.getOrDefault(emptyList())
        }

    /** Daily recommend songs (requires login) */
    suspend fun getDailyRecommend(): List<Song> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = get("$PROXY_BASE/recommend/songs")
                val data = JSONObject(json).getJSONObject("data").getJSONArray("dailySongs")
                parseSongArray(data)
            }.getOrDefault(emptyList())
        }

    // ══════════════════════════════════════════════════════════════════════
    //  Parsing helpers
    // ══════════════════════════════════════════════════════════════════════

    private fun parseSongs(json: String): List<Song> = runCatching {
        val root = JSONObject(json)
        when {
            root.has("result") -> {
                val arr = root.getJSONObject("result").optJSONArray("songs") ?: return emptyList()
                parseSongArray(arr)
            }
            root.has("songs")  -> parseSongArray(root.getJSONArray("songs"))
            else               -> emptyList()
        }
    }.getOrDefault(emptyList())

    private fun parseSongArray(arr: JSONArray): List<Song> {
        val list = mutableListOf<Song>()
        for (i in 0 until arr.length()) {
            runCatching {
                val o = arr.getJSONObject(i)
                val artists = buildString {
                    val ar = o.optJSONArray("ar") ?: o.optJSONArray("artists")
                    if (ar != null) for (j in 0 until ar.length()) {
                        if (j > 0) append(", ")
                        append(ar.getJSONObject(j).getString("name"))
                    }
                }
                val album    = o.optJSONObject("al") ?: o.optJSONObject("album")
                val picUrl   = album?.optString("picUrl") ?: ""
                val albumName = album?.optString("name") ?: ""
                list.add(Song(
                    id       = o.getLong("id"),
                    name     = o.getString("name"),
                    artist   = artists,
                    album    = albumName,
                    coverUrl = picUrl.takeIf { it.isNotBlank() },
                    duration = o.optLong("dt", 0L)
                ))
            }
        }
        return list
    }

    private fun parsePlaylists(json: String): List<Playlist> = runCatching {
        val arr = JSONObject(json).getJSONArray("playlists")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Playlist(
                id         = o.getLong("id"),
                name       = o.getString("name"),
                coverUrl   = o.optString("coverImgUrl"),
                trackCount = o.optInt("trackCount", 0),
                playCount  = o.optLong("playCount", 0L)
            )
        }
    }.getOrDefault(emptyList())

    private fun parseLyrics(json: String): LyricResult = runCatching {
        val root = JSONObject(json)
        LyricResult(
            original   = root.optJSONObject("lrc")?.optString("lyric") ?: "",
            translated = root.optJSONObject("tlyric")?.optString("lyric") ?: ""
        )
    }.getOrDefault(LyricResult("", ""))

    private fun parseLoginResult(json: String): LoginResult = runCatching {
        val root    = JSONObject(json)
        val code    = root.optInt("code", -1)
        if (code != 200) return LoginResult.Error("登录失败 (code $code)")
        val account = root.optJSONObject("account")
        val profile = root.optJSONObject("profile")
        // Store MUSIC_U from Set-Cookie (handled by CookieJar automatically)
        // Re-affirm os=pc after successful login
        setCookie("os", OS)
        LoginResult.Success(
            userId   = account?.getLong("id") ?: 0L,
            nickname = profile?.optString("nickname") ?: "",
            avatar   = profile?.optString("avatarUrl") ?: "",
            vipType  = account?.optInt("vipType", 0) ?: 0
        )
    }.getOrElse { LoginResult.Error(it.message ?: "解析失败") }

    // ══════════════════════════════════════════════════════════════════════
    //  HTTP helper
    // ══════════════════════════════════════════════════════════════════════

    private fun get(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            return response.body?.string() ?: ""
        }
    }

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    // ══════════════════════════════════════════════════════════════════════
    //  Login result sealed class
    // ══════════════════════════════════════════════════════════════════════

    sealed class LoginResult {
        data class Success(
            val userId  : Long,
            val nickname: String,
            val avatar  : String,
            val vipType : Int       // 0=free, 10=VIP, 11=SVIP
        ) : LoginResult()
        data class Error(val message: String) : LoginResult()
    }
}
