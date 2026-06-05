package com.cloudmelody.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cloudmelody.databinding.ItemSongBinding
import com.cloudmelody.model.Song
import com.cloudmelody.util.TimeUtils

class SongAdapter(
    private val onItemClick: ((Song) -> Unit)? = null
) : ListAdapter<Song, SongAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(oldItem: Song, newItem: Song) =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Song, newItem: Song) =
                oldItem == newItem
        }
    }

    inner class ViewHolder(private val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song) {
            binding.tvTitle.text    = song.name
            binding.tvArtist.text   = song.artist
            binding.tvDuration.text = TimeUtils.formatMs(song.duration)
            binding.ivCover.setImageResource(com.cloudmelody.R.drawable.placeholder_cover)
            binding.root.setOnClickListener { onItemClick?.invoke(song) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
