package com.cloudmelody.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cloudmelody.R
import com.cloudmelody.databinding.ItemSongBinding
import com.cloudmelody.model.Song
import com.cloudmelody.util.TimeUtils

/**
 * Bug 修复：
 * 1. 原代码 ListAdapter 泛型参数丢失，导致编译错误
 * 2. onClick lambda 泛型参数不匹配
 */
class SongAdapter(
    private val onClick: (Song, Int, List<Song>) -> Unit
) : ListAdapter<Song, SongAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val song = getItem(position)
        holder.binding.apply {
            tvTitle.text  = song.name
            tvArtist.text = song.artist
            tvDuration.text = TimeUtils.formatMs(song.duration)
            ivCover.load(song.coverUrl) {
                crossfade(true)
                placeholder(R.drawable.placeholder_cover)
                error(R.drawable.placeholder_cover)
            }
            root.setOnClickListener { onClick(song, position, currentList) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(a: Song, b: Song) = a.id == b.id
            override fun areContentsTheSame(a: Song, b: Song) = a == b
        }
    }
}
