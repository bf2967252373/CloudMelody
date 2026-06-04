package com.cloudmelody.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cloudmelody.api.NeteaseApi
import com.cloudmelody.databinding.FragmentHomeBinding
import com.cloudmelody.model.Playlist
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val adapter = PlaylistAdapter { playlist ->
        // TODO: open playlist detail
        Toast.makeText(requireContext(), playlist.name, Toast.LENGTH_SHORT).show()
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
        binding.rvPlaylists.adapter = adapter
        loadPlaylists()
    }

    private fun loadPlaylists() {
        lifecycleScope.launch {
            try {
                val result = NeteaseApi.get().recommend()
                if (result.isSuccess) {
                    adapter.submitList(result.getOrNull())
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(com.cloudmelody.R.string.error_generic),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
