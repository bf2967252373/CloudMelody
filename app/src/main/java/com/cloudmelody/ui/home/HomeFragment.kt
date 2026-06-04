package com.cloudmelody.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.cloudmelody.databinding.FragmentHomeBinding
import com.cloudmelody.model.Song
import com.cloudmelody.ui.MainActivity
import com.cloudmelody.ui.player.PlayerActivity
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var songAdapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupSearch()
        observeState()
    }

    private fun setupRecyclerViews() {
        playlistAdapter = PlaylistAdapter { playlist ->
            // Open playlist detail – load songs and play
            val activity = requireActivity() as? MainActivity ?: return@PlaylistAdapter
            lifecycleScope.launch {
                val songs = com.cloudmelody.api.NeteaseApi.getPlaylistDetail(playlist.id)
                if (songs.isNotEmpty()) openPlayer(activity, songs, 0)
            }
        }
        binding.rvPlaylists.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = playlistAdapter
        }

        songAdapter = SongAdapter { song, index, songs ->
            val activity = requireActivity() as? MainActivity ?: return@SongAdapter
            openPlayer(activity, songs, index)
        }
        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = songAdapter
        }
    }

    private fun openPlayer(activity: MainActivity, songs: List<Song>, index: Int) {
        activity.musicService?.setPlaylist(songs, index)
        activity.musicService?.urlFetcher = { id ->
            com.cloudmelody.api.NeteaseApi.getSongUrl(id)
        }
        startActivity(Intent(activity, PlayerActivity::class.java))
    }

    private fun setupSearch() {
        binding.searchField.doAfterTextChanged { text ->
            val q = text?.toString()?.trim() ?: ""
            if (q.isEmpty()) viewModel.clearSearch()
        }
        binding.searchField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.search(binding.searchField.text.toString().trim())
                true
            } else false
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Show/hide sections based on search vs browse
                    val isSearch = state.searchResults.isNotEmpty()
                    binding.rvPlaylists.visibility = if (isSearch) View.GONE else View.VISIBLE
                    binding.labelHot.visibility = if (isSearch) View.GONE else View.VISIBLE
                    binding.rvSongs.visibility = if (isSearch) View.VISIBLE else View.GONE

                    binding.progressBar.visibility =
                        if (state.isLoading || state.isSearching) View.VISIBLE else View.GONE

                    if (isSearch) {
                        songAdapter.submitList(state.searchResults)
                    } else {
                        playlistAdapter.submitList(state.playlists)
                    }

                    state.errorMsg?.let {
                        binding.errorText.text = it
                        binding.errorText.visibility = View.VISIBLE
                    } ?: run {
                        binding.errorText.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
