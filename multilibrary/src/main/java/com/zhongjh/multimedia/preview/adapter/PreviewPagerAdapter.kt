package com.zhongjh.multimedia.preview.adapter

import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.VideoView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.github.chrisbanes.photoview.PhotoView
import com.zhongjh.common.entity.LocalMedia
import com.zhongjh.common.enums.MimeType
import com.zhongjh.multimedia.AlbumCameraRecorderFileProvider
import com.zhongjh.multimedia.R
import com.zhongjh.multimedia.preview.adapter.PreviewPagerAdapter.PreviewViewHolder
import com.zhongjh.multimedia.settings.GlobalSpec.imageEngine
import java.io.File

/**
 * @author zhongjh
 */
class PreviewPagerAdapter(private val mContext: Context, private val mActivity: Activity, private val mIsDisplayCamera: Boolean) :
    RecyclerView.Adapter<PreviewViewHolder>() {

    private companion object {
        private const val TAG = "PreviewPlayback"
    }

    /**
     * 数据源
     */
    private val items = ArrayList<LocalMedia>()

    /**
     * view的缓存
     */
    private val mViewHolderCache = LinkedHashMap<Int, PreviewViewHolder>()
    private var isFirstAttachedToWindow = false

    private var onListener: OnListener? = null
    private var onPlaybackStateChange: (() -> Unit)? = null
    private var onActivePlaybackChanged: ((position: Int) -> Unit)? = null

    fun setOnListener(listener: OnListener) {
        this.onListener = listener
    }

    interface OnListener {
        /**
         * adapter显示view时的触发事件
         */
        fun onViewFirstAttachedToWindow(holder: PreviewViewHolder)

        /**
         * 单击图片预览
         */
        fun onImageClick()
    }

    override fun onViewAttachedToWindow(holder: PreviewViewHolder) {
        super.onViewAttachedToWindow(holder)
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION && mViewHolderCache[position] !== holder) {
            mViewHolderCache[position] = holder
        }
        if (!isFirstAttachedToWindow) {
            // 只有第一次初始化该Adapter才触发
            onListener?.onViewFirstAttachedToWindow(holder)
            isFirstAttachedToWindow = true
        }
    }

    override fun onViewDetachedFromWindow(holder: PreviewViewHolder) {
        super.onViewDetachedFromWindow(holder)
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION && mViewHolderCache[position] === holder) {
            mViewHolderCache.remove(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
        val holder = PreviewViewHolder(LayoutInflater.from(mActivity).inflate(R.layout.fragment_preview_item_zjh, parent, false))
        return holder
    }

    override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
        mViewHolderCache[position] = holder
        holder.videoView.alpha = 0F
        holder.imageView.alpha = 1F
        holder.videoCoverView.alpha = 1F
        holder.videoView.videoWidth = 0
        holder.videoView.videoHeight = 0

        val item = items[position]
        if (item.isVideo()) {
            holder.imageView.visibility = View.GONE
            holder.videoCoverView.visibility = View.VISIBLE
            holder.imageView.setZoomable(false)
            setupPlayback(holder, item.uri, item.duration)
            holder.videoCoverView.scaleType = ImageView.ScaleType.FIT_CENTER
            item.loadLocalImage(mContext, imageEngine, holder.videoCoverView)
        } else if (item.isAudio()) {
            holder.imageView.visibility = View.VISIBLE
            holder.videoCoverView.visibility = View.GONE
            holder.imageView.setZoomable(false)
            setupPlayback(holder, item.uri, item.duration)
            item.loadDrawableImage(mContext, imageEngine, holder.imageView)
        } else {
            holder.imageView.visibility = View.VISIBLE
            holder.videoCoverView.visibility = View.GONE
            holder.imageView.setZoomable(true)
            // 判断本地是否有图片，有则加载本地图片，没有则加载网络图片
            if (item.uri.isNotEmpty()) {
                item.loadLocalImage(mContext, imageEngine, holder.imageView)
            } else {
                item.loadNetworkImage(mContext, imageEngine, holder.imageView)
            }
        }
        if (item.isVideo() || item.isAudio()) {
            holder.imageView.setOnTouchListener(null)
            holder.imageView.setOnClickListener {
                onListener?.onImageClick()
            }
            holder.videoCoverView.setOnClickListener {
                onListener?.onImageClick()
            }
        } else {
            holder.imageView.setOnClickListener(null)
            holder.videoCoverView.setOnClickListener(null)
            holder.imageView.setOnViewTapListener { _, _, _ ->
                onListener?.onImageClick()
            }
        }
        if (item.isVideo() || item.isAudio()) {
            onActivePlaybackChanged?.invoke(position)
        }
        holder.videoView.setOnCompletionListener {
            resetPlayback(holder)
            onPlaybackStateChange?.invoke()
        }
        holder.videoView.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "playback error holder=${holder.hashCode()} what=$what extra=$extra currentUri=${holder.currentUri}")
            resetPlayback(holder)
            onPlaybackStateChange?.invoke()
            true
        }

        holder.videoView.setOnInfoListener { _, what, extra ->
            Log.d(TAG, "info holder=${holder.hashCode()} what=$what extra=$extra playing=${holder.videoView.isPlaying}")
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                hideCover(holder)
            }
            false
        }
    }

    /**
     * 停止当前正在内嵌播放的媒体
     */
    fun pausePlayback(position: Int = -1) {
        if (position >= 0) {
            mViewHolderCache[position]?.let { currentViewHolder ->
                currentViewHolder.videoView.pause()
                setPlayingState(currentViewHolder, false)
                showCover(currentViewHolder)
                onPlaybackStateChange?.invoke()
            }
        } else {
            mViewHolderCache.values.forEach { viewHolder ->
                viewHolder.videoView.pause()
                setPlayingState(viewHolder, false)
                showCover(viewHolder)
            }
            onPlaybackStateChange?.invoke()
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    val size: Int
        get() = items.size

    /**
     * 返回当前数据源
     */
    fun getLocalMedias(): ArrayList<LocalMedia> {
        return items
    }

    fun getLocalMedia(position: Int): LocalMedia? {
        return if (size > 0 && position < size) items[position] else null
    }

    fun getCurrentViewHolder(position: Int): PreviewViewHolder? {
        return mViewHolderCache[position]
    }

    /**
     * 播放状态变化时通知fragment刷新控制条
     */
    fun setOnPlaybackStateChange(callback: (() -> Unit)?) {
        onPlaybackStateChange = callback
    }

    /**
     * 当前holder绑定后通知fragment刷新控制条
     */
    fun setOnActivePlaybackChanged(callback: ((position: Int) -> Unit)?) {
        onActivePlaybackChanged = callback
    }

    /**
     * 切换当前正在显示的媒体播放状态
     */
    fun togglePlayback(position: Int) {
        val holder = mViewHolderCache[position]
        val path = holder?.currentPath
        if (holder == null || path == null) {
            Log.w(TAG, "toggle skipped position=$position holder=$holder path=$path")
            return
        }
        Log.d(TAG, "toggle position=$position holder=${holder.hashCode()} path=$path currentUri=${holder.currentUri} prepared=${holder.isPrepared} requested=${holder.isPlaying} playing=${holder.videoView.isPlaying}")
        if (holder.videoView.isPlaying) {
            holder.videoView.pause()
            setPlayingState(holder, false)
        } else {
            startPlayback(holder, path)
        }
        onPlaybackStateChange?.invoke()
    }

    fun setLocalMedia(position: Int, localMedia: LocalMedia) {
        items[position] = localMedia
    }

    fun addAll(items: List<LocalMedia>) {
        this.items.addAll(items)
    }

    fun onDestroy() {
        pausePlayback()
        mViewHolderCache.clear()
        onListener = null
        onPlaybackStateChange = null
        onActivePlaybackChanged = null
    }

    /**
     * 使用内嵌VideoView播放媒体
     */
    private fun startPlayback(holder: PreviewViewHolder, path: String) {
        val uri = resolveUri(path)
        if (holder.currentUri != uri || !holder.isPrepared) {
            holder.currentUri = uri
            holder.isPrepared = false
            holder.mediaDuration = 0
            holder.videoView.stopPlayback()
            holder.videoView.alpha = 0F
            holder.videoView.setVideoURI(uri)
        }
        holder.videoView.start()
        if (holder.isPrepared && holder.videoView.currentPosition > 0) {
            hideCover(holder)
        }
        setPlayingState(holder, true)
    }

    /**
     * 解析本地或远程媒体地址
     */
    private fun resolveUri(path: String): Uri {
        return if (MimeType.isContent(path) || MimeType.isHasHttp(path)) {
            Uri.parse(path)
        } else {
            AlbumCameraRecorderFileProvider.getUriForFile(mContext, File(path))
        }
    }

    /**
     * 准备内嵌播放器
     */
    private fun setupPlayback(holder: PreviewViewHolder, path: String, mediaDuration: Long) {
        setPlayingState(holder, false)
        holder.isPrepared = false
        holder.mediaDuration = 0
        val uri = resolveUri(path)
        holder.currentUri = uri
        holder.currentPath = path
        holder.videoView.setOnPreparedListener { mediaPlayer ->
            holder.isPrepared = true
            holder.mediaDuration = maxOf(mediaPlayer.duration, mediaDuration.toInt(), 1)
            holder.videoView.videoWidth = mediaPlayer.videoWidth
            holder.videoView.videoHeight = mediaPlayer.videoHeight
            if (holder.isPlaying) {
                holder.videoView.start()
                onPlaybackStateChange?.invoke()
            }
        }
        holder.videoView.stopPlayback()
        holder.videoView.alpha = 0F
        Log.d(TAG, "preload holder=${holder.hashCode()} position=${holder.bindingAdapterPosition} uri=$uri")
        holder.videoView.setVideoURI(uri)
    }

    private fun togglePlayback(holder: PreviewViewHolder, path: String) {
        if (holder.videoView.isPlaying) {
            holder.videoView.pause()
            setPlayingState(holder, false)
        } else {
            startPlayback(holder, path)
        }
        onPlaybackStateChange?.invoke()
    }

    private fun setPlayingState(holder: PreviewViewHolder, isPlaying: Boolean) {
        holder.isPlaying = isPlaying
    }

    /**
     * 暂停播放场景恢复封面，避免切回已停止的VideoView时出现黑屏
     */
    private fun showCover(holder: PreviewViewHolder) {
        holder.videoView.animate().cancel()
        holder.videoCoverView.animate().cancel()
        holder.videoView.alpha = 0F
        holder.videoCoverView.alpha = 1F
    }

    /**
     * 暂停后复用同一播放器时，可能不再触发渲染开始事件，需要立即隐藏封面
     */
    private fun hideCover(holder: PreviewViewHolder) {
        holder.videoView.animate().cancel()
        holder.videoCoverView.animate().cancel()
        holder.videoView.alpha = 1F
        holder.videoCoverView.alpha = 0F
        Log.d(TAG, "hide cover holder=${holder.hashCode()} prepared=${holder.isPrepared} playing=${holder.videoView.isPlaying}")
    }

    private fun resetPlayback(holder: PreviewViewHolder) {
        holder.videoView.pause()
        holder.videoView.seekTo(0)
        setPlayingState(holder, false)
        holder.videoView.animate().alpha(0F).setDuration(120L).start()
        holder.videoCoverView.animate().alpha(1F).setDuration(120L).start()
    }

    class PreviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var imageView: PhotoView = itemView.findViewById(R.id.image_view)
        var videoCoverView: ImageView = itemView.findViewById(R.id.video_cover_view)
        var videoView: FitCenterVideoView = itemView.findViewById(R.id.video_view)
        var currentUri: Uri? = null
        var currentPath: String? = null
        var isPrepared = false
        var isPlaying = false
        var mediaDuration = 0
    }
}
