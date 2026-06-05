package com.cloudmelody.api

import com.cloudmelody.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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

    // ─── Login result sealed class ─────────────────────────────────────────

    sealed class LoginResult {
        data class Success(val nickname: String, val avatar: String) : LoginResult()
        data class Error(val message: String) : LoginResult()
    }

    // ─── Login: phone ──────────────────────────────────────────────────────

    suspend fun loginPhone(
        phone: String,
        password: String,
        countryCode: String = "86"
    ): LoginResult = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("phone", phone)
                .add("password", password)
                .add("countrycode", countryCode)
                .add("rememberLogin", "true")
                .build()
            val req = Request.Builder()
                .url("$BASE/weapi/login/cellphone")
                .post(body)
                .header("User-Agent", USER_AGENT)
                .header("Referer", BASE)
                .header("Cookie", "os=pc")
                .build()
            val json = client.newCall(req).execute().use { it.body?.string() ?: "" }
            parseLoginResponse(json)
        }.getOrElse { e -> LoginResult.Error(e.message ?: "Network error") }
    }

    // ─── Login: email ──────────────────────────────────────────────────────

    suspend fun loginEmail(
        email: String,
        password: String
    ): LoginResult = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("username", email)
                .add("password", password)
                .add("rememberLogin", "true")
                .build()
            val req = Request.Builder()
                .url("$BASE/weapi/login")
                .post(body)
                .header("User-Agent", USER_AGENT)
                .header("Referer", BASE)
                .header("Cookie", "os=pc")
                .build()
            val json = client.newCall(req).execute().use { it.body?.string() ?: "" }
            parseLoginResponse(json)
        }.getOrElse { e -> LoginResult.Error(e.message ?: "Network error") }
    }

    private fun parseLoginResponse(json: String): LoginResult {
        if (json.isBlank()) return LoginResult.Error("Empty response")
        val obj = JSONObject(json)
        val code = obj.optInt("code", -1)
        return if (code == 200) {
            val profile = obj.optJSONObject("profile")
            LoginResult.Success(
                nickname = profile?.optString("nickname") ?: "",
                avatar = profile?.optString("avatarUrl") ?: ""
            )
        } else {
            LoginResult.Error(obj.optString("message", "Login failed (code $code)"))
        }
    }

    // ─── Recommend playlists ───────────────────────────────────────────────

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
