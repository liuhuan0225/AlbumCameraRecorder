package com.zhongjh.multimedia.album.ui.manager

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zhongjh.common.entity.LocalMedia
import com.zhongjh.multimedia.R
import com.zhongjh.multimedia.databinding.ItemSelectedMediaBarZjhBinding
import com.zhongjh.multimedia.settings.GlobalSpec.imageEngine

class SelectedMediaBarAdapter(
    private val placeholder: Drawable,
    private val onRemoveClick: (LocalMedia) -> Unit
) : ListAdapter<LocalMedia, SelectedMediaBarAdapter.SelectedMediaViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedMediaViewHolder {
        val binding = ItemSelectedMediaBarZjhBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SelectedMediaViewHolder(binding, parent.context)
    }

    override fun onBindViewHolder(holder: SelectedMediaViewHolder, position: Int) {
        holder.bind(getItem(position), placeholder)
    }

    inner class SelectedMediaViewHolder(
        private val binding: ItemSelectedMediaBarZjhBinding,
        private val context: Context
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(media: LocalMedia, placeholder: Drawable) {
            imageEngine.loadThumbnail(
                context,
                context.resources.getDimensionPixelSize(R.dimen.z_selected_media_bar_size),
                placeholder,
                binding.ivSelectedMedia,
                media.uri
            )
            val isVideo = media.isVideo()
            binding.flVideoDuration.isVisible = isVideo
            if (isVideo) {
                binding.tvSelectedVideoDuration.text = DateUtils.formatElapsedTime(media.duration / 1000)
            }
            binding.ivRemoveSelectedMedia.setOnClickListener {
                onRemoveClick(media)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LocalMedia>() {
            override fun areItemsTheSame(oldItem: LocalMedia, newItem: LocalMedia) =
                oldItem.fileId == newItem.fileId

            override fun areContentsTheSame(oldItem: LocalMedia, newItem: LocalMedia) =
                oldItem.uri == newItem.uri && oldItem.duration == newItem.duration
        }
    }
}
