package com.zhongjh.multimedia.album.ui.mediaselection.adapter.widget

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 线
 * @author zhongjh
 */
class MediaGridInset(private val mSpanCount: Int, private val mSpacing: Int, private val mIncludeEdge: Boolean) : RecyclerView.ItemDecoration() {

    companion object {

        /**
         * 如果间距小于列间隙数量，整数偏移会导致部分列之间没有分割线，因此保证每个间隙至少有1px
         */
        fun calculateSpacing(spacing: Int, spanCount: Int): Int {
            return max(spacing, spanCount - 1)
        }
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        // item position
        val position = parent.getChildAdapterPosition(view)
        // item column
        val column = position % mSpanCount
        val spacing = calculateSpacing(mSpacing, mSpanCount)
        if (mIncludeEdge) {
            // spacing - column * ((1f / spanCount) * spacing)
            outRect.left = spacing - (spacing.toFloat() * column / mSpanCount).roundToInt()
            // (column + 1) * ((1f / spanCount) * spacing)
            outRect.right = (spacing.toFloat() * (column + 1) / mSpanCount).roundToInt()

            // top edge
            if (position < mSpanCount) {
                outRect.top = spacing
            }
            // item bottom
            outRect.bottom = spacing
        } else {
            // 将间距均匀分配到左右两侧，保证相邻两项之间的实际间距始终一致
            outRect.left = (spacing.toFloat() * column / mSpanCount).roundToInt()
            outRect.right = (spacing.toFloat() * (mSpanCount - column - 1) / mSpanCount).roundToInt()
            if (position >= mSpanCount) {
                // item top
                outRect.top = spacing
            }
        }
    }

}
