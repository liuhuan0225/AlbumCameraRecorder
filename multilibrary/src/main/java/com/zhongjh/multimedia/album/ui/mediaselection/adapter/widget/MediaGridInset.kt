package com.zhongjh.multimedia.album.ui.mediaselection.adapter.widget

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

/**
 * 网格间距
 * @author zhongjh
 */
class MediaGridInset(private val mSpacing: Int) : RecyclerView.ItemDecoration() {

    companion object {

        /**
         * 如果间距小于列间隙数量，整数偏移会导致部分列之间没有分割线，因此保证每个间隙至少有1px
         */
        fun calculateSpacing(spacing: Int, spanCount: Int): Int {
            return max(spacing, spanCount - 1)
        }
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        // 四边平均分配间距，相邻 item 的间距相加后横纵均为 mSpacing
        val start = mSpacing / 2
        val end = mSpacing - start
        outRect.set(start, start, end, end)
    }

}
