package com.cloudmelody.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cloudmelody.R
import com.cloudmelody.databinding.ItemPlaylistBinding
import com.cloudmelody.model.Playlist

class PlaylistAdapter(
    private val onClick: (Playlist) -> Unit
) : ListAdapter<Playlist, PlaylistAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemPlaylistBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = getItem(position)
        holder.binding.apply {
            tvName.text = p.name
            tvTrackCount.text = "${p.trackCount} 首"
            ivCover.load(p.coverUrl) {
                crossfade(true)
                placeholder(R.drawable.placeholder_cover)
                error(R.drawable.placeholder_cover)
            }
            root.setOnClickListener { onClick(p) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Playlist>() {
            override fun areItemsTheSame(a: Playlist, b: Playlist) = a.id == b.id
            override fun areContentsTheSame(a: Playlist, b: Playlist) = a == b
        }
    }
}
