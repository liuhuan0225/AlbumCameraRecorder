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
    private val onMediaClick: (Int) -> Unit,
    private val onRemoveClick: (LocalMedia) -> Unit,
    private val isMediaDeselected: (LocalMedia) -> Boolean = { false },
    private val isRemoveButtonVisible: (LocalMedia) -> Boolean = { true }
) : ListAdapter<LocalMedia, SelectedMediaBarAdapter.SelectedMediaViewHolder>(DIFF_CALLBACK) {

    var currentPreviewFileId: Long = Long.MIN_VALUE
        set(value) {
            if (field != value) {
                val previousFileId = field
                field = value
                currentList.forEachIndexed { index, media ->
                    if (media.fileId == previousFileId || media.fileId == value) {
                        notifyItemChanged(index, PAYLOAD_CURRENT_PREVIEW)
                    }
                }
            }
        }

    var deselectedMediaIds: Set<Long> = emptySet()
        set(value) {
            if (field != value) {
                field = value
                currentList.forEachIndexed { index, media ->
                    if ((media.fileId in field) != (media.fileId in value) ||
                        media.fileId == currentPreviewFileId
                    ) {
                        notifyItemChanged(index, PAYLOAD_PREVIEW_STATE)
                    }
                }
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedMediaViewHolder {
        val binding = ItemSelectedMediaBarZjhBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SelectedMediaViewHolder(binding, parent.context)
    }

    override fun onBindViewHolder(holder: SelectedMediaViewHolder, position: Int) {
        holder.bind(getItem(position), placeholder)
    }

    override fun onBindViewHolder(
        holder: SelectedMediaViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_CURRENT_PREVIEW)) {
            holder.updateCurrentPreview(getItem(position))
        } else if (payloads.contains(PAYLOAD_PREVIEW_STATE)) {
            holder.updatePreviewState(getItem(position))
        } else {
            holder.bind(getItem(position), placeholder)
        }
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
            binding.viewCurrentPreviewBorder.isVisible = media.fileId == currentPreviewFileId
            binding.viewDeselectedMask.isVisible = media.fileId in deselectedMediaIds
            binding.ivRemoveSelectedMedia.isVisible = isRemoveButtonVisible(media)
            if (isVideo) {
                binding.tvSelectedVideoDuration.text = DateUtils.formatElapsedTime(media.duration / 1000)
            }
            binding.ivSelectedMedia.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onMediaClick(position)
                }
            }
            binding.ivRemoveSelectedMedia.setOnClickListener {
                onRemoveClick(media)
            }
        }

        fun updateCurrentPreview(media: LocalMedia) {
            binding.viewCurrentPreviewBorder.isVisible = media.fileId == currentPreviewFileId
        }

        fun updatePreviewState(media: LocalMedia) {
            binding.viewCurrentPreviewBorder.isVisible = media.fileId == currentPreviewFileId
            binding.viewDeselectedMask.isVisible = media.fileId in deselectedMediaIds
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LocalMedia>() {
            override fun areItemsTheSame(oldItem: LocalMedia, newItem: LocalMedia) =
                oldItem.fileId == newItem.fileId

            override fun areContentsTheSame(oldItem: LocalMedia, newItem: LocalMedia) =
                oldItem.uri == newItem.uri && oldItem.duration == newItem.duration &&
                    oldItem.isChecked == newItem.isChecked
        }

        private const val PAYLOAD_CURRENT_PREVIEW = "payload_current_preview"
        private const val PAYLOAD_PREVIEW_STATE = "payload_preview_state"
    }
}
