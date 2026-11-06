package com.zhongjh.multimedia.crop

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

/**
 * 裁剪图片视图，保证图片始终填满指定比例裁剪区域
 */
class CropGestureImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private val displayMatrix = Matrix()
    private val sourceRect = RectF()
    private val imageRect = RectF()
    private val gestureDetector = GestureDetector(context, GestureListener())
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private var cropRect = RectF(0f, 0f, 0f, 0f)
    private var baseScale = 1f
    private var isBitmapReady = false

    init {
        scaleType = ScaleType.MATRIX
    }

    fun setCropRect(rect: RectF) {
        cropRect.set(rect)
        if (isBitmapReady) {
            resetTransform()
        }
    }

    fun getImageTransform(): Matrix {
        return Matrix(displayMatrix)
    }

    override fun setImageBitmap(bitmap: Bitmap?) {
        super.setImageBitmap(bitmap)
        isBitmapReady = bitmap != null
        if (bitmap != null) {
            sourceRect.set(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            resetTransform()
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (isBitmapReady) {
            resetTransform()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scaleHandled = scaleGestureDetector.onTouchEvent(event)
        val gestureHandled = if (!scaleGestureDetector.isInProgress) {
            gestureDetector.onTouchEvent(event)
        } else {
            false
        }
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            limitTransform()
        }
        return scaleHandled || gestureHandled || super.onTouchEvent(event)
    }

    private fun resetTransform() {
        if (width <= 0 || height <= 0 || cropRect.isEmpty) {
            return
        }
        baseScale = max(
            cropRect.width() / sourceRect.width(),
            cropRect.height() / sourceRect.height()
        )
        displayMatrix.setScale(baseScale, baseScale)
        displayMatrix.postTranslate(
            cropRect.centerX() - sourceRect.width() * baseScale / 2f,
            cropRect.centerY() - sourceRect.height() * baseScale / 2f
        )
        imageMatrix = displayMatrix
        invalidate()
    }

    private fun limitTransform() {
        val scale = currentScale()
        val minScale = baseScale
        val maxScale = MAX_SCALE
        if (scale < minScale || scale > maxScale) {
            val limitedScale = scale.coerceIn(minScale, maxScale)
            displayMatrix.postScale(
                limitedScale / scale,
                limitedScale / scale,
                cropRect.centerX(),
                cropRect.centerY()
            )
        }
        displayMatrix.mapRect(imageRect, sourceRect)
        val translatedX = when {
            imageRect.left > cropRect.left -> cropRect.left - imageRect.left
            imageRect.right < cropRect.right -> cropRect.right - imageRect.right
            else -> 0f
        }
        val translatedY = when {
            imageRect.top > cropRect.top -> cropRect.top - imageRect.top
            imageRect.bottom < cropRect.bottom -> cropRect.bottom - imageRect.bottom
            else -> 0f
        }
        if (translatedX != 0f || translatedY != 0f) {
            displayMatrix.postTranslate(translatedX, translatedY)
        }
        imageMatrix = displayMatrix
        invalidate()
    }

    fun getVisibleSourceRect(screenRect: RectF): RectF {
        val resultRect = RectF()
        val inverseMatrix = Matrix()
        if (displayMatrix.invert(inverseMatrix)) {
            inverseMatrix.mapRect(resultRect, screenRect)
        }
        return resultRect
    }

    private fun currentScale(): Float {
        val matrixValues = FloatArray(MATRIX_VALUE_COUNT)
        displayMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean {
            return true
        }

        override fun onScroll(
            downEvent: MotionEvent?,
            moveEvent: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            displayMatrix.postTranslate(-distanceX, -distanceY)
            limitTransform()
            return true
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scale = currentScale()
            if (scale > MAX_SCALE && detector.scaleFactor > 1f) {
                return true
            }
            displayMatrix.postScale(
                detector.scaleFactor,
                detector.scaleFactor,
                detector.focusX,
                detector.focusY
            )
            limitTransform()
            return true
        }
    }

    private companion object {
        const val MAX_SCALE = 20f
        const val MATRIX_VALUE_COUNT = 9
    }
}
