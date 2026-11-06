package com.zhongjh.multimedia.sharedanimation

import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 用于在相册界面中的RecyclerView的item 过渡到 预览界面的ViewPager2的item 时，所需要的相关参数
 */
object RecycleItemViewParams {

    private val viewParams: MutableList<ViewParams?> = ArrayList()

    /**
     * 当前记录相册界面的可见第一个position
     */
    var firstPosition = 0

    /**
     * 当前记录相册界面可见最后一个position
     */
    var lastPosition = 0

    fun clear() {
        if (viewParams.size > 0) {
            viewParams.clear()
        }
    }

    fun getItemViewParams(position: Int): ViewParams? {
        return if (viewParams.size > position) viewParams[position] else null
    }

    fun getItem(position: Int): ViewParams? {
        return getItemViewParams(position)
    }

    /**
     * 添加 列表的每一个参数 到 viewParams
     */
    @JvmStatic
    fun add(viewGroup: ViewGroup, statusBarHeight: Int) {
        // 判断只支持 RecyclerView 和 ListView
        val childCount: Int = when (viewGroup) {
            is RecyclerView -> {
                viewGroup.childCount
            }

            is ListView -> {
                viewGroup.childCount
            }

            else -> {
                throw IllegalArgumentException(
                    viewGroup.javaClass.name
                            + " Must be " + RecyclerView::class.java + " or " + ListView::class.java
                )
            }
        }
        // 先确定数据总量，再按真实的 adapter position 记录每一个子 View
        val totalCount: Int
        val firstVisiblePosition: Int
        val lastVisiblePosition: Int
        if (viewGroup is RecyclerView) {
            val layoutManager = when (viewGroup.layoutManager) {
                is LinearLayoutManager -> viewGroup.layoutManager as LinearLayoutManager
                else -> viewGroup.layoutManager as GridLayoutManager
            }
            totalCount = layoutManager.itemCount
            firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
        } else {
            val listAdapter = (viewGroup as ListView).adapter ?: return
            totalCount = listAdapter.count
            firstVisiblePosition = viewGroup.firstVisiblePosition
            lastVisiblePosition = viewGroup.lastVisiblePosition
        }
        firstPosition = firstVisiblePosition
        lastPosition = lastVisiblePosition.coerceAtMost(totalCount - 1)
        viewParams.clear()
        repeat(totalCount) {
            viewParams.add(null)
        }

        for (childIndex in 0 until childCount) {
            val view = viewGroup.getChildAt(childIndex) ?: continue
            val position = when (viewGroup) {
                is RecyclerView -> viewGroup.getChildAdapterPosition(view)
                else -> firstVisiblePosition + childIndex
            }
            if (position !in viewParams.indices) {
                continue
            }
            viewParams[position] = createViewParams(view, statusBarHeight)
        }
    }

    private fun createViewParams(view: View, statusBarHeight: Int): ViewParams {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return ViewParams().apply {
            left = location[0]
            top = location[1] - statusBarHeight
            width = view.width
            height = view.height
        }
    }

}
