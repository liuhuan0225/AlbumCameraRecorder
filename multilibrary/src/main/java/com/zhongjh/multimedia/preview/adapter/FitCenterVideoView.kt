package com.zhongjh.multimedia.preview.adapter

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

/**
 * 按视频原始比例撑满宽或高，另一侧使用容器黑色背景填充
 */
class FitCenterVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VideoView(context, attrs, defStyleAttr) {

    var videoWidth = 0
        set(value) {
            field = value
            requestLayout()
        }

    var videoHeight = 0
        set(value) {
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        if (videoWidth <= 0 || videoHeight <= 0 || availableWidth <= 0 || availableHeight <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val scale = minOf(
            availableWidth.toDouble() / videoWidth,
            availableHeight.toDouble() / videoHeight
        )
        setMeasuredDimension((videoWidth * scale).toInt(), (videoHeight * scale).toInt())
    }
}
