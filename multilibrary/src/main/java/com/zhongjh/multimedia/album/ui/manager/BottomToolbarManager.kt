package com.zhongjh.multimedia.album.ui.manager

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zhongjh.common.entity.LocalMedia
import com.zhongjh.multimedia.R
import com.zhongjh.multimedia.databinding.FragmentAlbumZjhBinding
import com.zhongjh.multimedia.model.OriginalManage
import com.zhongjh.multimedia.settings.AlbumSpec

class BottomToolbarManager(
    private val binding: FragmentAlbumZjhBinding, private val albumSpec: AlbumSpec,
    private val originalManage: OriginalManage,
    private val placeholder: Drawable,
    private val selectedMediaProvider: () -> List<LocalMedia>,
    private val onPreviewMedia: (Int) -> Unit,
    private val onRemoveMedia: (LocalMedia) -> Unit
) {

    private val selectedMediaAdapter = SelectedMediaBarAdapter(placeholder, onPreviewMedia, onRemoveMedia)

    init {
        binding.rvSelectedMedia.layoutManager = LinearLayoutManager(
            binding.root.context, LinearLayoutManager.HORIZONTAL, false
        )
        binding.rvSelectedMedia.adapter = selectedMediaAdapter

    }

    /**
     * 更新选中数量与按钮状态
     */
    fun updateSelectedState(selectedCount: Int) {
        binding.buttonPreview.isEnabled = selectedCount > 0
        binding.rvSelectedMedia.isVisible = selectedCount > 0
        binding.buttonApply.isEnabled = selectedCount > 0
        selectedMediaAdapter.submitList(selectedMediaProvider())
        binding.buttonApply.text = if (selectedCount == 0) {
            binding.root.context.getString(R.string.z_multi_library_button_sure_default)
        } else {
            binding.root.context.getString(R.string.z_multi_library_button_next, selectedCount)
        }
    }

    /**
     * 更新原图控件状态
     */
    fun updateOriginalState(enabled: Boolean) {
        binding.original.setChecked(enabled)
        binding.groupOriginal.visibility = if (albumSpec.originalEnable) View.VISIBLE else View.INVISIBLE
    }

}
