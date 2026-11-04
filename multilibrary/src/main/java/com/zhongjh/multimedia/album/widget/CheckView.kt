package com.zhongjh.multimedia.album.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.zhongjh.common.utils.LogUtil
import com.zhongjh.multimedia.R

/**
 * @author zhongjh
 */
class CheckView : View {

    private var mCountable = false
    private var mSingleSelection = false
    private var mChecked = false
    private var mCheckedNum = 0
    private val mTextPaint by lazy {
        initTextPaint()
    }
    private var mEmptyCircleDrawable: Drawable? = null
    private var mSelectedCircleDrawable: Drawable? = null
    private var mDensity = 0f
    private var mEnabled = true

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // fixed size 48dp x 48dp
        val sizeSpec = MeasureSpec.makeMeasureSpec((SIZE * mDensity).toInt(), MeasureSpec.EXACTLY)
        super.onMeasure(sizeSpec, sizeSpec)
    }

    private fun init(context: Context) {
        mDensity = context.resources.displayMetrics.density
        mEmptyCircleDrawable = ResourcesCompat.getDrawable(
            context.resources,
            R.drawable.ic_check_circle_empty, context.theme
        )
        mSelectedCircleDrawable = ResourcesCompat.getDrawable(
            context.resources,
            R.drawable.ic_check_circle_selected, context.theme
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (!mEnabled) {
            return true
        }
        return super.onTouchEvent(event)
    }

    fun setChecked(checked: Boolean) {
        check(!mCountable) { "CheckView is countable, call setCheckedNum() instead." }
        mChecked = checked
        invalidate()
    }

    /**
     * 设置是否多选
     */
    fun setCountable(countable: Boolean) {
        mCountable = countable
        LogUtil.d("CheckView", "mCountable :$mCountable")
    }

    /**
     * 单选时不显示勾选圆圈，仅保留选择热区
     */
    fun setSingleSelection(singleSelection: Boolean) {
        mSingleSelection = singleSelection
        invalidate()
    }

    fun setCheckedNum(checkedNum: Int) {
        check(mCountable) { "CheckView is not countable, call setChecked() instead." }
        require(!(checkedNum != UNCHECKED && checkedNum <= 0)) { "checked num can't be negative." }
        mCheckedNum = checkedNum
        invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        if (mEnabled != enabled) {
            mEnabled = enabled
            invalidate()
        }
    }

    override fun isEnabled(): Boolean {
        return mEnabled
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (mSingleSelection) {
            // 单选不绘制勾选圆圈，保留透明热区让右上角可继续触发选择
            return
        }

        mEmptyCircleDrawable?.setBounds(0, 0, width, height)
        mEmptyCircleDrawable?.draw(canvas)

        val isSelected = if (mCountable) mCheckedNum != UNCHECKED else mChecked
        if (isSelected) {
            mSelectedCircleDrawable?.setBounds(0, 0, width, height)
            mSelectedCircleDrawable?.draw(canvas)
            if (mCountable) {
                initTextPaint()
                val text = mCheckedNum.toString()
                val baseX = (width - mTextPaint.measureText(text)).toInt() / 2
                val baseY = (height - mTextPaint.descent() - mTextPaint.ascent()).toInt() / 2
                canvas.drawText(text, baseX.toFloat(), baseY.toFloat(), mTextPaint)
            }
        }

        // enable hint
        alpha = if (mEnabled) 1.0f else 0.5f
    }

    private fun initTextPaint(): TextPaint {
        val textPaint = TextPaint()
        textPaint.isAntiAlias = true
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(
            Typeface.DEFAULT,
            Typeface.BOLD
        )
        textPaint.textSize = 12.0f * mDensity
        return textPaint
    }

    companion object {
        const val UNCHECKED = Int.MIN_VALUE

        /**
         * dp
         */
        private const val SIZE = 28f
    }
}
