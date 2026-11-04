package com.zhongjh.multimedia.album.widget.albumspinner

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zhongjh.multimedia.R
import com.zhongjh.multimedia.album.entity.Album
import com.zhongjh.multimedia.album.entity.AlbumSpinnerStyle

/**
 * 专辑选项控件
 *
 * @author zhongjh
 * @date 2022/9/21
 */
class AlbumSpinner constructor(private val context: Context, private val albumSpinnerStyle: AlbumSpinnerStyle) : PopupWindow() {
    private val window: View = LayoutInflater.from(context).inflate(R.layout.view_album_spinner_zjh, null, false)
    private val mRecyclerView by lazy {
        window.findViewById<RecyclerView>(R.id.folder_list)
    }
    private val adapter by lazy {
        AlbumSpinnerAdapter(albumSpinnerStyle)
    }
    private var ivArrowView: ImageView? = null
    private var tvAlbumTitle: TextView? = null

    init {
        this.contentView = window
        this.width = RelativeLayout.LayoutParams.MATCH_PARENT
        this.height = ViewGroup.LayoutParams.WRAP_CONTENT
        this.animationStyle = R.style.AlbumSpinnerThemeStyle
        this.isFocusable = true
        this.isOutsideTouchable = true
        initView()
    }

    fun initView() {
        mRecyclerView.setLayoutManager(LinearLayoutManager(context))
        mRecyclerView.setAdapter(adapter)
        window.setOnClickListener { dismiss() }
        mRecyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            private var isDownInBlank = false

            override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isDownInBlank = recyclerView.findChildViewUnder(event.x, event.y) == null
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDownInBlank) {
                            isDownInBlank = false
                            dismiss()
                            return true
                        }
                    }
                }
                return super.onInterceptTouchEvent(recyclerView, event)
            }
        })
    }

    fun bindFolder(albums: List<Album>) {
        adapter.bindAlbums(albums)
    }

    val isEmpty: Boolean
        get() = adapter.albums.isEmpty()

    fun setArrowImageView(ivArrowView: ImageView) {
        this.ivArrowView = ivArrowView
        this.ivArrowView?.setImageDrawable(albumSpinnerStyle.drawableDown)
        this.ivArrowView?.rotation = 0f
        this.ivArrowView?.setOnClickListener { albumSpinnerOnClick() }
    }

    fun setTitleTextView(tvAlbumTitle: TextView) {
        this.tvAlbumTitle = tvAlbumTitle
        this.tvAlbumTitle?.setOnClickListener { albumSpinnerOnClick() }
    }

    override fun showAsDropDown(anchor: View) {
        val screenLocation = IntArray(2)
        anchor.getLocationInWindow(screenLocation)
        val availableHeight = anchor.rootView.height - screenLocation[1] - anchor.height
        this.height = if (availableHeight > 0) availableHeight else RelativeLayout.LayoutParams.MATCH_PARENT
        super.showAsDropDown(anchor)
        ivArrowView?.let { ivArrowView ->
            ivArrowView.animate()
                .rotation(180f)
                .setDuration(200L)
                .start()
        }
    }

    fun setOnAlbumItemClickListener(listener: OnAlbumItemClickListener?) {
        adapter.setOnAlbumItemClickListener(listener)
    }

    override fun dismiss() {
        ivArrowView?.let { ivArrowView ->
            ivArrowView.animate()
                .rotation(0f)
                .setDuration(200L)
                .start()
        }
        super@AlbumSpinner.dismiss()
    }

    /**
     * 设置选中状态 - 红色圆点
     */
    fun updateFolderCheckStatus(result: List<Album>) {
        val albums = adapter.albums
        val size = albums.size
        val resultSize = result.size
        for (i in 0 until size) {
            val album = albums[i]
            album.checkedCount = 0
            album.name?.let {
                for (j in 0 until resultSize) {
                    val media = result[j]
                    if (album.name == media.name || -1L == album.id) {
                        album.checkedCount = 1
                        break
                    }
                }
            }
        }
        adapter.bindAlbums(albums)
    }

    /**
     * 设置选中状态
     */
    fun updateCheckStatus(selects: List<Album>) {
        val albums = adapter.albums
        for (album in albums) {
            for (select in selects) {
                if (select.name == album.name) {
                    album.isChecked = true
                    break
                }
            }
        }
        adapter.bindAlbums(albums)
    }

    /**
     * 自动绑定相关View显示本身
     */
    private fun albumSpinnerOnClick() {
        if (this.isShowing) {
            this.dismiss()
        } else {
            tvAlbumTitle?.let {
                this.showAsDropDown(it)
            }
        }
    }
}
