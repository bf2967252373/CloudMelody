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
 * Bug 修复：
 * 1. 原代码调用 NeteaseApi.recommend() 风格不一致（有时写 NeteaseApi.get().recommend()）
 * 2. 原代码没有 isLoading / error 状态，UI 无法感知请求状态
 * 3. 统一使用 StateFlow 替代 LiveData，配合 repeatOnLifecycle 使用
 */
class HomeViewModel : ViewModel() {

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadRecommend() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            NeteaseApi.recommend()
                .onSuccess { _playlists.value = it }
                .onFailure { _error.value = it.message ?: "Unknown error" }
            _isLoading.value = false
        }
    }

    fun loadPlaylistSongs(playlistId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            NeteaseApi.playlistDetail(playlistId)
                .onSuccess { _songs.value = it }
                .onFailure { _error.value = it.message ?: "Unknown error" }
            _isLoading.value = false
        }
    }
}
