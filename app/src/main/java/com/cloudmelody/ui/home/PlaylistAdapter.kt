package com.cloudmelody.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cloudmelody.databinding.ItemPlaylistBinding
import com.cloudmelody.model.Playlist

class PlaylistAdapter(
    private val onItemClick: ((Playlist) -> Unit)? = null
) : ListAdapter<Playlist, PlaylistAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Playlist>() {
            override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist) =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist) =
                oldItem == newItem
        }
    }

    inner class ViewHolder(private val binding: ItemPlaylistBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(playlist: Playlist) {
            binding.tvName.text  = playlist.name
            binding.tvCount.text = playlist.trackCount.toString()
            binding.root.setOnClickListener { onItemClick?.invoke(playlist) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
