package com.cloudmelody.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.cloudmelody.databinding.FragmentHomeBinding
import com.cloudmelody.model.Song
import com.cloudmelody.service.MusicService
import com.cloudmelody.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 首页 Fragment — 推荐歌单 + 搜索
 * 点击歌单展开歌曲列表，点击歌曲开始播放
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    // Song list adapter for inline display
    private val songAdapter = SongAdapter { song, index, list ->
        playSong(song, index, list)
    }

    private val playlistAdapter = PlaylistAdapter { playlist ->
        viewModel.loadPlaylistSongs(playlist.id)
    }

    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Playlist RecyclerView
        binding.rvPlaylists.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPlaylists.adapter = playlistAdapter

        // Songs RecyclerView (initially empty)
        binding.rvSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSongs.adapter = songAdapter

        // Search input
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            val query = binding.etSearch.text?.toString().orEmpty()
            if (query.isNotBlank()) {
                viewModel.search(query)
            }
            true
        }

        // Search debounce: trigger search 500ms after user stops typing
        binding.etSearch.setOnFocusChangeListener { _, _ ->
            val query = binding.etSearch.text?.toString().orEmpty()
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(500L)
                viewModel.search(query)
            }
        }

        // Observe state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.playlists.collect { list ->
                        playlistAdapter.submitList(list)
                    }
                }
                launch {
                    viewModel.songs.collect { list ->
                        if (list.isNotEmpty()) {
                            songAdapter.submitList(list)
                            binding.rvSongs.visibility = View.VISIBLE
                            binding.tvSectionSongs.text = "歌单 (${list.size}首)"
                            binding.tvSectionSongs.visibility = View.VISIBLE
                        }
                    }
                }
                launch {
                    viewModel.searchResults.collect { list ->
                        if (list.isNotEmpty() || binding.etSearch.text?.isNotBlank() == true) {
                            songAdapter.submitList(list)
                            binding.rvSongs.visibility = View.VISIBLE
                            binding.tvSectionSongs.text = "搜索结果 (${list.size}首)"
                            binding.tvSectionSongs.visibility = View.VISIBLE
                        }
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.error.collect { msg ->
                        if (!msg.isNullOrBlank()) {
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                            viewModel.clearError()
                        }
                    }
                }
            }
        }

        viewModel.loadRecommend()
    }

    /**
     * Play a song: get MusicService from MainActivity and start playing
     */
    private fun playSong(song: Song, index: Int, list: List<Song>) {
        val service = (requireActivity() as? MainActivity)?.musicService
        if (service == null) {
            Toast.makeText(requireContext(), "播放服务未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        service.setPlaylist(list, index)
        // Navigate to player screen
        startActivity(android.content.Intent(requireContext(), com.cloudmelody.ui.player.PlayerActivity::class.java))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
