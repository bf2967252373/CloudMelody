package com.cloudmelody.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudmelody.api.NeteaseApi
import com.cloudmelody.model.Playlist
import com.cloudmelody.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主页 ViewModel — 统一管理推荐歌单、搜索、歌曲列表状态
 */
class HomeViewModel : ViewModel() {

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 加载推荐歌单 */
    fun loadRecommend() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            NeteaseApi.recommend()
                .onSuccess { _playlists.value = it }
                .onFailure { _error.value = it.message ?: "未知错误" }
            _isLoading.value = false
        }
    }

    /** 加载歌单中的歌曲 */
    fun loadPlaylistSongs(playlistId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            NeteaseApi.playlistDetail(playlistId)
                .onSuccess { _songs.value = it }
                .onFailure { _error.value = it.message ?: "未知错误" }
            _isLoading.value = false
        }
    }

    /** 搜索歌曲 */
    fun search(keyword: String) {
        if (keyword.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            NeteaseApi.searchSongs(keyword)
                .onSuccess { _searchResults.value = it }
                .onFailure { _error.value = it.message ?: "未知错误" }
            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}
