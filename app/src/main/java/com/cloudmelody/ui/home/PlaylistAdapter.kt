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

/**
 * Bug 修复：
 * 1. 原代码 ListAdapter 缺少泛型参数 <Playlist, VH>，导致编译失败
 * 2. 引用 coverImgUrl 字段，但 Playlist 模型统一为 coverUrl，字段不存在
 * 3. 缺少 placeholder / error 占位图保护
 */
class PlaylistAdapter(
    private val onClick: (Playlist) -> Unit
) : ListAdapter<Playlist, PlaylistAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemPlaylistBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.tvName.text = item.name
        holder.binding.tvCount.text = item.trackCount.toString()
        // Bug 修复：使用统一字段名 coverUrl（原代码用 coverImgUrl 导致 Unresolved reference）
        holder.binding.ivCover.load(item.coverUrl) {
            crossfade(true)
            placeholder(R.drawable.placeholder_cover)
            error(R.drawable.placeholder_cover)
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Playlist>() {
            override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist) =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist) =
                oldItem == newItem
        }
    }
}
