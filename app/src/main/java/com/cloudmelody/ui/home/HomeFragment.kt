package com.cloudmelody.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.cloudmelody.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

/**
 * Bug 修复：
 * 1. 原代码直接在 Fragment 中调用 NeteaseApi，与 ViewModel 产生重复请求
 * 2. 使用 launchWhenStarted（已废弃）替换为 repeatOnLifecycle
 * 3. ViewBinding 未在 onDestroyView 中置空导致内存泄漏
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    private val playlistAdapter = PlaylistAdapter { playlist ->
        Toast.makeText(requireContext(), playlist.name, Toast.LENGTH_SHORT).show()
        viewModel.loadPlaylistSongs(playlist.id)
    }

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

        binding.rvPlaylists.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPlaylists.adapter = playlistAdapter

        // Bug 修复：使用 repeatOnLifecycle 替代废弃的 launchWhenStarted
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.playlists.collect { playlistAdapter.submitList(it) }
                }
                launch {
                    viewModel.error.collect { msg ->
                        if (!msg.isNullOrBlank()) {
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        viewModel.loadRecommend()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Bug 修复：释放 binding 引用，防止内存泄漏
        _binding = null
    }
}
