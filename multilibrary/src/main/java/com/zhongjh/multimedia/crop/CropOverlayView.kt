package com.zhongjh.multimedia.crop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.zhongjh.multimedia.R
import kotlin.math.abs

/**
 * 只负责绘制固定比例的裁剪区域，不支持移动或缩放
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var ratioX = 1f
        private set

    var ratioY = 1f
        private set

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.crop_mask)
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val cropRect = RectF()
    private var maskImage: Drawable? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setRatio(x: Float, y: Float) {
        require(x > 0f && y > 0f) { "裁剪比例必须大于0" }
        ratioX = x
        ratioY = y
        maskImage?.let(::validateMaskImage)
        requestLayout()
        invalidate()
    }

    /**
     * 设置自定义遮罩图像，其宽高比例必须与裁剪比例一致
     */
    fun setMaskImageResource(resourceId: Int) {
        maskImage = if (resourceId == 0) {
            null
        } else {
            ContextCompat.getDrawable(context, resourceId)?.also(::validateMaskImage)
        }
        invalidate()
    }

    fun getCropRect(): RectF {
        return RectF(cropRect)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        calculateCropRect(width.toFloat(), height.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cropRect.isEmpty) {
            return
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
        canvas.drawRect(cropRect, clearPaint)
        maskImage?.let { image ->
            image.setBounds(
                cropRect.left.toInt(),
                cropRect.top.toInt(),
                cropRect.right.toInt(),
                cropRect.bottom.toInt()
            )
            image.draw(canvas)
        }
    }

    private fun calculateCropRect(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) {
            cropRect.setEmpty()
            return
        }
        val ratio = ratioX / ratioY
        if (ratio >= width / height) {
            val cropHeight = width / ratio
            val top = (height - cropHeight) / 2f
            cropRect.set(0f, top, width, top + cropHeight)
        } else {
            val cropWidth = height * ratio
            val left = (width - cropWidth) / 2f
            cropRect.set(left, 0f, left + cropWidth, height)
        }
    }

    private fun validateMaskImage(image: Drawable) {
        val maskWidth = image.intrinsicWidth
        val maskHeight = image.intrinsicHeight
        require(maskWidth > 0 && maskHeight > 0) { "遮罩图像必须具有有效尺寸" }
        val imageRatio = maskWidth.toFloat() / maskHeight.toFloat()
        val cropRatio = ratioX / ratioY
        require(abs(imageRatio - cropRatio) <= RATIO_TOLERANCE) { "遮罩图像比例必须与裁剪比例一致" }
    }

    private companion object {
        const val RATIO_TOLERANCE = 0.01f
    }
}
