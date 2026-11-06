package com.zhongjh.multimedia.preview

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import android.widget.SeekBar
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.zhongjh.common.entity.IncapableCause
import com.zhongjh.common.entity.LocalMedia
import com.zhongjh.common.enums.MediaType.Companion.TYPE_PICTURE
import com.zhongjh.common.listener.OnMoreClickListener
import com.zhongjh.common.utils.DisplayMetricsUtils.getScreenHeight
import com.zhongjh.common.utils.DisplayMetricsUtils.getScreenWidth
import com.zhongjh.common.utils.LogUtil
import com.zhongjh.common.utils.MediaUtils
import com.zhongjh.common.utils.StatusBarUtils.initStatusBar
import com.zhongjh.common.utils.request
// import com.zhongjh.imageedit.ImageEditActivity
import com.zhongjh.multimedia.BaseFragment
import com.zhongjh.multimedia.MainActivity
import com.zhongjh.multimedia.R
import com.zhongjh.multimedia.album.utils.AlbumCompressFileTask
import com.zhongjh.multimedia.album.utils.PhotoMetadataUtils
import com.zhongjh.multimedia.album.widget.CheckRadioView
import com.zhongjh.multimedia.album.widget.CheckView
import com.zhongjh.multimedia.album.ui.manager.SelectedMediaBarAdapter
import com.zhongjh.multimedia.model.MainModel
import com.zhongjh.multimedia.model.OriginalManage
import com.zhongjh.multimedia.model.SelectedData.Companion.STATE_SELECTION
import com.zhongjh.multimedia.model.SelectedModel
import com.zhongjh.multimedia.preview.adapter.PreviewPagerAdapter
import com.zhongjh.multimedia.preview.enum.PreviewType
import com.zhongjh.multimedia.preview.start.PreviewSetting
import com.zhongjh.multimedia.settings.AlbumSpec
import com.zhongjh.multimedia.settings.GlobalSpec
import com.zhongjh.multimedia.sharedanimation.OnSharedAnimationViewListener
import com.zhongjh.multimedia.sharedanimation.RecycleItemViewParams
import com.zhongjh.multimedia.sharedanimation.SharedAnimationView
import com.zhongjh.multimedia.utils.FileMediaUtil
import com.zhongjh.multimedia.utils.LifecycleFlowCollector
import com.zhongjh.multimedia.utils.MediaStoreUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import kotlin.math.ceil
import java.util.Locale


/**
 * 目标是可以不止该库自用，也可以别的app别的功能直接使用
 *
 * Activity和Fragment的区别:
 * Activity是一个全新的多媒体数据,而Fragment是一个来源于同一个Activity的多媒体数据
 *
 *
 * 预览窗口的基类支持以下功能：
 * 1. 决定是否共享动画
 * 2. 实例化ViewPager
 * 3. 确定后，返回的数据 - 是否压缩
 * 4. 支持放大缩小
 *
 * @author zhongjh
 * @date 2023/8/31
 */
class PreviewFragment : BaseFragment() {

    companion object {
        const val TAG: String = "PreviewFragment"
        private const val PROGRESS_UPDATE_INTERVAL = 500L
    }

    private lateinit var mContext: Context
    private val weakFragment = WeakReference(this@PreviewFragment)
    private lateinit var mViewHolder: ViewHolder
    private lateinit var mViewPager2: ViewPager2
    private lateinit var mAdapter: PreviewPagerAdapter
    private val mGlobalSpec by lazy {
        GlobalSpec
    }
    private val mAlbumSpec by lazy {
        AlbumSpec
    }

    private val mMainModel by lazy {
        val activity = requireActivity()
        val savedStateViewModelFactory = SavedStateViewModelFactory(activity.application, this)
        return@lazy ViewModelProvider(
            activity, savedStateViewModelFactory
        )[MainModel::class.java]
    }

    private val mSelectedModel by lazy {
        val activity = requireActivity()
        val savedStateViewModelFactory = SavedStateViewModelFactory(activity.application, this)
        return@lazy ViewModelProvider(
            activity, savedStateViewModelFactory
        )[SelectedModel::class.java]
    }

    /**
     * 打开ImageEditActivity的回调
     */
    private lateinit var mImageEditActivityResult: ActivityResultLauncher<Intent>

    /**
     * 统一管理原图有关功能模块
     */
    private val mOriginalManage by lazy {
        OriginalManage(this, mMainModel, mSelectedModel, mAlbumSpec)
    }

    /**
     * 完成压缩-复制的异步线程
     */
    private var mCompressFileJob: Job? = null

    /**
     * 异步线程的逻辑，确定当前选择的文件列表，根据是否压缩配置决定重新返回新的文件列表
     */
    private val mAlbumCompressFileTask by lazy {
        AlbumCompressFileTask(mContext, TAG, PreviewFragment::class.java, mGlobalSpec)
    }

    /**
     * 媒体数据
     * */
    private val mLocalMedias = ArrayList<LocalMedia>()

    /**
     * 当前编辑完的图片文件
     */
    private var mEditImagePath: String? = null

    private var screenWidth = 0
    private var screenHeight = 0

    /**
     * 是否从界面恢复回来的
     */
    private var mIsSavedInstanceState = false

    /**
     * 设置是否启动确定功能
     */
    private var mApplyEnable = true

    /**
     * 设置是否启动选择功能
     */
    private var mSelectedEnable = true

    /**
     * 设置是否开启编辑功能
     */
    private var mEditEnable = true

    /**
     * 设置是否开启原图
     */
    private var mOriginalEnable = true

    /**
     * 是否编辑了图片
     */
    private var mIsEdit = false

    /**
     * AlbumFragment是否有开启显示拍摄，如果是别的界面启动的PreviewFragment，默认为false
     */
    private var mIsDisplayCamera = false

    /**
     * 设置右上角是否检测类型
     */
    private var mIsSelectedCheck = true

    /**
     * 预览界面类型，代表从什么界面打开
     */
    private var mPreviewType = PreviewType.ALBUM_ACTIVITY

    /**
     * 当前预览数据源是否是已选中列表
     */
    private var mIsSelectedDataSource = false

    /**
     * 是否首次共享动画，只有第一次打开的时候才触发共享动画
     */
    private var mFirstSharedAnimation = true

    /**
     * 预览工具栏是否处于显示状态
     */
    private var mPreviewBarsVisible = true

    /**
     * 共享播放进度条定时器
     */
    private val mProgressHandler = Handler(Looper.getMainLooper())

    /**
     * 定时同步共享播放进度条
     */
    private val mProgressRunnable = object : Runnable {
        override fun run() {
            updatePlaybackProgress()
            mProgressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
        }
    }

    /**
     * 预览界面底部已选择媒体列表
     */
    private lateinit var mPreviewSelectedBarAdapter: SelectedMediaBarAdapter

    /**
     * 预览界面底部已选择媒体的占位图
     */
    private fun getPlaceholderDrawable(): Drawable {
        val typedArray = requireActivity().theme.obtainStyledAttributes(
            intArrayOf(R.attr.album_thumbnail_placeholder)
        )
        return try {
            typedArray.getDrawable(0)
                ?: ContextCompat.getDrawable(requireContext(), R.drawable.album_spinner_thumbnail_bg)!!
        } finally {
            typedArray.recycle()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context.applicationContext
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 获取样式
        return initStyle(inflater, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 获取宽高
        screenWidth = getScreenWidth(requireContext())
        screenHeight = getScreenHeight(requireContext())
        // 初始化回调
        initActivityResult()
        // 初始化bundle的Value
        initBundleValue(savedInstanceState)
        mViewHolder = ViewHolder(view)

        // 安全设置颜色：有主题用主题，没有就用白色
        val textColor = getThemeAttrColor(mContext, R.attr.preview_bottomToolbar_back_textColor, Color.WHITE)
        val colorStateList = getThemeColorStateList(mContext, R.attr.preview_bottomToolbar_apply_textColor, R.color.blue_preview_bottom_toolbar_apply_zjh)
        mViewHolder.tvEdit.setTextColor(textColor)
        mViewHolder.tvOriginal.setTextColor(textColor)
        mViewHolder.tvSize.setTextColor(textColor)
        mViewHolder.buttonApply.setTextColor(colorStateList)
        mViewHolder.pbLoading.indeterminateTintList = colorStateList

        mViewHolder.checkView.setCountable(mAlbumSpec.countable)
        if (mAlbumSpec.singleSelectionModeEnabled()) {
            mViewHolder.checkView.visibility = View.GONE
        }
        // 初始化状态栏
        if (requireActivity() is MainActivity) {
            initStatusBar(requireActivity(), mViewHolder.constraintLayout)
        } else {
            initStatusBar(requireActivity())
        }

        // 初始化共享动画view
        initSharedAnimationView()
        initViewPagerData()
        initPlaybackBar()
        mLocalMedias.getOrNull(mViewPager2.currentItem)?.let { currentMedia ->
            updatePlaybackBar(currentMedia)
        }
        initListener()
        initObserveData()
        refreshSelectedState()
    }

    override fun onBackPressed(): Boolean {
        return if (isSharedAnimation()) {
            mViewHolder.sharedAnimationView.backToMin()
            true
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        mProgressHandler.removeCallbacks(mProgressRunnable)
        mViewPager2.unregisterOnPageChangeCallback(mOnPageChangeCallback)
        mAdapter.onDestroy()
        // 如果依附的Activity是MainActivity,就显示底部控件动画
        if (requireActivity() is MainActivity) {
            (requireActivity() as MainActivity).showHideTableLayoutAnimator(true)
        }
        super.onDestroy()
    }

    override fun onPause() {
        if (::mAdapter.isInitialized) {
            mAdapter.pausePlayback()
            if (::mViewHolder.isInitialized) {
                updatePlaybackProgress()
            }
        }
        super.onPause()
    }

    /**
     * 初始化样式
     */
    private fun initStyle(inflater: LayoutInflater, container: ViewGroup?): View {
        val wrapper = ContextThemeWrapper(activity, mGlobalSpec.themeId)
        val cloneInContext = inflater.cloneInContext(wrapper)
        return cloneInContext.inflate(R.layout.fragment_preview_zjh, container, false)
    }

    /**
     * 针对回调
     */
    private fun initActivityResult() {
//        mImageEditActivityResult =
//            registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
//                if (result.resultCode == RESULT_OK) {
//                    mIsEdit = true
//                    refreshMultiMediaItem()
//                }
//            }
    }

    /**
     * 初始化bundle的Value
     */
    private fun initBundleValue(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            // 初始化别的界面传递过来的数据
            arguments?.let {
                // 设置是否启动 '确定' 功能
                mApplyEnable = it.getBoolean(PreviewSetting.APPLY_ENABLE, true)
                // 设置是否启动 '选择' 功能
                mSelectedEnable = it.getBoolean(PreviewSetting.SELECTED_ENABLE, true)
                // 验证当前item是否满足可以被选中的条件
                mIsSelectedCheck = it.getBoolean(PreviewSetting.IS_SELECTED_CHECK, true)
                // 预览类型，表达从什么界面打开进来的
                mPreviewType = it.getSerializable(PreviewSetting.PREVIEW_TYPE) as PreviewType
                mIsSelectedDataSource = it.getBoolean(PreviewSetting.SELECTED_MEDIA_PREVIEW, false)
                // 设置是否开启编辑功能
                mEditEnable = it.getBoolean(PreviewSetting.EDIT_ENABLE, true)
                // 设置是否开启原图
                mOriginalEnable = it.getBoolean(PreviewSetting.EXTRA_RESULT_ORIGINAL_ENABLE, true)
                // 上一个相册界面是否显示拍照，如果显示，关联的预览界面也要相对应做position调整
                mIsDisplayCamera = it.getBoolean(PreviewSetting.IS_DISPLAY_CAMERA, false)
                // 数据源
                it.getParcelableArrayList<LocalMedia>(PreviewSetting.PREVIEW_DATA)?.let { selection ->
                    if (mPreviewType != PreviewType.ALBUM_FRAGMENT) {
                        mSelectedModel.getSelectedData().addAll(selection)
                    }
                    mMainModel.previewPosition = it.getInt(PreviewSetting.CURRENT_POSITION, 0)
                }
            }
        } else {
            mIsSavedInstanceState = true
        }
    }

    /**
     * 初始化MagicalView
     */
    private fun initSharedAnimationView() {
        mViewPager2 = ViewPager2(requireContext())
        mViewHolder.sharedAnimationView.setContentView(mViewPager2)
        if (isSharedAnimation()) {
            val alpha = if (mIsSavedInstanceState) 1F else 0F
            mViewHolder.sharedAnimationView.setBackgroundAlpha(alpha)
            mViewHolder.bottomToolbar.alpha = alpha
            mViewHolder.videoPlaybackBar.alpha = alpha
            mViewHolder.constraintLayout.alpha = alpha
        } else {
            mViewHolder.sharedAnimationView.setBackgroundAlpha(1.0F)
        }
        mViewHolder.sharedAnimationView.setBackgroundColor(Color.BLACK)
        initPreviewSelectedMedia()
        mViewHolder.sharedAnimationView.setOnSharedAnimationViewListener(object :
            OnSharedAnimationViewListener {
            override fun onBeginBackMinAnim() {
                // 开始 退出共享动画
                this@PreviewFragment.onSharedBeginBackMinAnim()
            }

            override fun onBeginBackMinMagicalFinish(isResetSize: Boolean) {
                this@PreviewFragment.onSharedBeginBackMinFinish(isResetSize)
            }

            override fun onBeginSharedAnimComplete(
                sharedAnimationView: SharedAnimationView,
                showImmediately: Boolean
            ) {
                // 开始共享动画完成后
                this@PreviewFragment.onSharedBeginAnimComplete(sharedAnimationView, showImmediately)
            }

            override fun onBackgroundAlpha(alpha: Float) {
                this@PreviewFragment.onBackgroundAlpha(alpha)
            }

            override fun onMagicalViewFinish() {
                this@PreviewFragment.onSharedViewFinish()
            }
        })
    }

    /**
     * 初始化预览界面底部已选择媒体列表
     */
    private fun initPreviewSelectedMedia() {
        mViewHolder.rvSelectedMedia.layoutManager = LinearLayoutManager(
            requireContext(), LinearLayoutManager.HORIZONTAL, false
        )
        mPreviewSelectedBarAdapter = SelectedMediaBarAdapter(
            getPlaceholderDrawable(),
            ::onPreviewSelectedMediaClick,
            ::removeCurrentMediaFromList,
            { media -> !mSelectedModel.getSelectedData().isSelected(media) },
            { false }
        )
        mViewHolder.rvSelectedMedia.adapter = mPreviewSelectedBarAdapter
        mViewHolder.rvSelectedMedia.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                if (parent.getChildAdapterPosition(view) > 0) {
                    val spacing = requireContext().resources.getDimensionPixelSize(
                        R.dimen.z_selected_media_bar_spacing
                    )
                    if (parent.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                        outRect.right = spacing
                    } else {
                        outRect.left = spacing
                    }
                }
            }
        })
    }

    /**
     * 跳转到底部列表中对应的预览页
     */
    private fun onPreviewSelectedMediaClick(position: Int) {
        val media = mPreviewSelectedBarAdapter.currentList.getOrNull(position) ?: return
        val viewPagerPosition = mLocalMedias.indexOfFirst { it.fileId == media.fileId }
        if (viewPagerPosition >= 0) {
            mViewPager2.setCurrentItem(viewPagerPosition, false)
        }
    }

    /**
     * 在已选数据预览中移除当前媒体，并刷新底部列表和按钮状态
     */
    private fun removeCurrentMediaFromList(item: LocalMedia) {
        val position = mLocalMedias.indexOfFirst { it.fileId == item.fileId }
        if (position >= 0) {
            mSelectedModel.removeSelectedData(item, position)
        }
        val currentItem = mLocalMedias.getOrNull(mViewPager2.currentItem)
        if (currentItem?.fileId == item.fileId) {
            mViewHolder.checkView.setChecked(false)
        }
        refreshSelectedState()
    }

    /**
     * 初始化ViewPager2
     */
    private fun initViewPagerData() {
        mAdapter = PreviewPagerAdapter(mContext, requireActivity(), mIsDisplayCamera)
        val previewData = arguments?.getParcelableArrayList<LocalMedia>(PreviewSetting.PREVIEW_DATA)
        if (previewData != null && previewData.isNotEmpty()) {
            mLocalMedias.addAll(previewData)
        } else {
            mLocalMedias.addAll(mMainModel.getLocalMedias())
        }
        mAdapter.addAll(mLocalMedias)
        mAdapter.notifyItemRangeChanged(0, mLocalMedias.size)
        mViewPager2.adapter = mAdapter

        // adapter显示view时的触发事件
        mAdapter.setOnListener(object : PreviewPagerAdapter.OnListener {
            override fun onViewFirstAttachedToWindow(holder: PreviewPagerAdapter.PreviewViewHolder) {
                this@PreviewFragment.onViewFirstAttachedToWindow(holder)
            }

            override fun onImageClick() {
                togglePreviewBars()
            }
        })
        mAdapter.setOnPlaybackStateChange {
            updatePlaybackProgress()
        }
        mAdapter.setOnActivePlaybackChanged { position ->
            mLocalMedias.getOrNull(position)?.let { media ->
                updatePlaybackBar(media)
            }
        }

        // 多图时滑动事件
        mViewPager2.registerOnPageChangeCallback(mOnPageChangeCallback)
        mViewPager2.setCurrentItem(getDataPosition(), false)
    }

    /**
     * 初始化共享自定义播放控制条
     */
    private fun initPlaybackBar() {
        mViewHolder.videoPlayPauseButton.setOnClickListener {
            val position = mViewPager2.currentItem
            Log.d("PreviewPlayback", "fragment click position=$position")
            mAdapter.togglePlayback(mViewPager2.currentItem)
        }
        mViewHolder.videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mViewHolder.tvCurrentTime.text = formatElapsedTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                Log.d("PreviewPlayback", "seek stopped position=${mViewPager2.currentItem} progress=${seekBar.progress} max=${seekBar.max}")
                mAdapter.getCurrentViewHolder(mViewPager2.currentItem)
                    ?.videoView?.seekTo(seekBar.progress)
                updatePlaybackProgress()
            }
        })
    }

    /**
     * 所有事件
     */
    private fun initListener() {
        // 编辑
        mViewHolder.tvEdit.setOnClickListener(object : OnMoreClickListener() {
            override fun onListener(v: View) {
                mViewHolder.tvEdit.text = resources.getText(R.string.z_multi_library_button_edit)
//                openImageEditActivity()
            }
        })
        // 返回
        mViewHolder.iBtnBack.setOnClickListener {
            if (isSharedAnimation()) {
                mViewHolder.sharedAnimationView.backToMin()
            } else {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        // 确认
        mViewHolder.buttonApply.setOnClickListener(object : OnMoreClickListener() {
            override fun onListener(v: View) {
                if (mAlbumSpec.singleSelectionModeEnabled()) {
                    val position = if (mIsDisplayCamera) {
                        mViewPager2.currentItem + 1
                    } else {
                        mViewPager2.currentItem
                    }
                    val media = mLocalMedias.getOrNull(mViewPager2.currentItem)
                    if (media != null) {
                        mSelectedModel.getSelectedData().clearAll()
                        mSelectedModel.addSelectedData(media, position)
                    }
                }
                // 确认的一刻赋值
                val localMediaArrayList: ArrayList<LocalMedia> = mSelectedModel.getSelectedData().getSelectedMediaArrayList()
                // 设置是否原图状态
                for (localMedia in localMediaArrayList) {
                    localMedia.isOriginal = mMainModel.getOriginalEnable()
                }
                setResultOkByIsCompress()
            }
        })
        // 右上角选择事件
        mViewHolder.checkView.setOnClickListener {
            setCheckViewClickable()
        }
        // 点击原图事件
        mViewHolder.originalLayout.setOnClickListener {
            mOriginalManage.originalClick()
        }
        // 点击Loading停止
        mViewHolder.pbLoading.setOnClickListener {
            // 中断线程
            mCompressFileJob?.cancel()
            // 恢复界面可用
            setControlTouchEnable(true)
        }
    }

    /**
     * 单击预览图切换顶栏和底栏
     */
    private fun togglePreviewBars() {
        mPreviewBarsVisible = !mPreviewBarsVisible
        mViewHolder.constraintLayout.isVisible = mPreviewBarsVisible
        if (mPreviewBarsVisible) {
            mLocalMedias.getOrNull(mViewPager2.currentItem)?.let { currentMedia ->
                updatePlaybackBar(currentMedia)
            }
        } else {
            mViewHolder.videoPlaybackBar.visibility = View.GONE
        }
        updateBottomToolbarVisibility()
    }

    /**
     * 更新共享播放控制条，视频和音频显示，图片隐藏
     */
    private fun updatePlaybackBar(item: LocalMedia) {
        val hasPlayback = item.isVideo() || item.isAudio()
        val holder = mAdapter.getCurrentViewHolder(mViewPager2.currentItem)
        Log.d("PreviewPlayback", "updateBar position=${mViewPager2.currentItem} hasPlayback=$hasPlayback uri=${item.uri} itemDuration=${item.duration} holder=${holder?.hashCode()} playerDuration=${holder?.videoView?.duration} mediaDuration=${holder?.mediaDuration}")
        mViewHolder.videoPlaybackBar.visibility = if (hasPlayback) View.VISIBLE else View.GONE
        if (!hasPlayback) {
            mProgressHandler.removeCallbacks(mProgressRunnable)
            return
        }
        mViewHolder.videoSeekBar.max = maxOf(
            holder?.videoView?.duration ?: 0,
            holder?.mediaDuration ?: 0,
            item.duration.toInt(),
            1
        )
        mViewHolder.tvDuration.text = formatTotalElapsedTime(mViewHolder.videoSeekBar.max)
        updatePlaybackProgress()
    }

    /**
     * 同步播放按钮、时间和进度
     */
    private fun updatePlaybackProgress() {
        updatePlaybackProgress(mLocalMedias.getOrNull(mViewPager2.currentItem))
    }

    /**
     * 按指定媒体同步播放状态；未拿到播放器时使用媒体自身时长重置进度
     */
    private fun updatePlaybackProgress(item: LocalMedia?) {
        if (item == null) {
            mProgressHandler.removeCallbacks(mProgressRunnable)
            return
        }
        val holder = mAdapter.getCurrentViewHolder(mViewPager2.currentItem)
        val currentPosition = holder?.videoView?.currentPosition ?: 0
        val duration = maxOf(
            holder?.videoView?.duration ?: 0,
            holder?.mediaDuration ?: 0,
            item.duration.toInt(),
            1
        )
        Log.d(
            "PreviewPlayback",
            "updateProgress position=${mViewPager2.currentItem} holder=${holder?.hashCode()} " +
                "current=$currentPosition duration=$duration playerDuration=${holder?.videoView?.duration ?: 0} " +
                "itemDuration=${item.duration} requested=${holder?.isPlaying == true} " +
                "playing=${holder?.videoView?.isPlaying == true}"
        )
        mViewHolder.videoSeekBar.max = duration
        if (holder != null && !mViewHolder.videoSeekBar.isPressed) {
            mViewHolder.videoSeekBar.progress = currentPosition
        }
        mViewHolder.tvCurrentTime.text = formatElapsedTime(currentPosition)
        mViewHolder.tvDuration.text = formatTotalElapsedTime(duration)
        mViewHolder.videoPlayPauseButton.setImageResource(
            if (holder?.isPlaying == true) R.drawable.ic_pause_white_24dp else R.drawable.ic_play_arrow_white_24dp
        )
        if (holder?.isPlaying == true) {
            mProgressHandler.removeCallbacks(mProgressRunnable)
            mProgressHandler.postDelayed(mProgressRunnable, PROGRESS_UPDATE_INTERVAL)
        } else {
            mProgressHandler.removeCallbacks(mProgressRunnable)
        }
    }

    private fun formatElapsedTime(millisecond: Int): String {
        val totalSeconds = millisecond / 1000
        val hours = totalSeconds / 3600
        val minutes = totalSeconds % 3600 / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun formatTotalElapsedTime(millisecond: Int): String {
        val totalSeconds = ceil(millisecond / 1000.0).toInt()
        val hours = totalSeconds / 3600
        val minutes = totalSeconds % 3600 / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * 初始化数据的监控
     */
    private fun initObserveData() {
        // 原图选项改变
        LifecycleFlowCollector.collect(
            viewLifecycleOwner,
            mMainModel.originalEnable
        ) { value: Boolean ->
            mViewHolder.original.setChecked(value)
        }
        // 相册界面移动完成后触发
        LifecycleFlowCollector.collect(
            viewLifecycleOwner,
            mMainModel.onScrollToPositionComplete
        ) { value: Int ->
            setSharedAnimationViewParams(value)
        }
    }

    /**
     * 关闭Activity回调相关数值
     */
    private fun setResultOkByIsCompress() {
        if (mPreviewType == PreviewType.CAMERA || mPreviewType == PreviewType.GRID || mPreviewType == PreviewType.THIRD_PARTY) {
            // 直接返回
            setResultOk(mSelectedModel.getSelectedData().getSelectedMediaArrayList())
        } else {
            // 其他界面就要先压缩
            compressFile()
        }
    }

    /**
     * 刷新MultiMedia
     */
    private fun refreshMultiMediaItem() {
        // 获取当前查看的multimedia
        val localMedia = mAdapter.getLocalMedia(mViewPager2.currentItem)
        // 更新当前fragment编辑后的uri和path
        localMedia?.let {
            localMedia.editorPath = mEditImagePath
            mAdapter.setLocalMedia(mViewPager2.currentItem, it)
            mAdapter.notifyItemChanged(mViewPager2.currentItem)
        }
    }

    /**
     * 打开编辑的Activity
     */
    private fun openImageEditActivity() {
//        val item = mAdapter.getLocalMedia(getDataPosition())
//        item?.let {
//            val file = FileMediaUtil.createCacheFile(mContext, TYPE_PICTURE)
//            mEditImagePath = file.absoluteFile.toString()
//            val intent = Intent()
//            intent.setClass(requireActivity(), ImageEditActivity::class.java)
//            intent.putExtra(
//                ImageEditActivity.EXTRA_IMAGE_SCREEN_ORIENTATION,
//                requireActivity().requestedOrientation
//            )
//            intent.putExtra(ImageEditActivity.EXTRA_IMAGE_URI, item.uri)
//            intent.putExtra(ImageEditActivity.EXTRA_IMAGE_SAVE_PATH, file.absolutePath)
//            mImageEditActivityResult.launch(intent)
//        }
    }

    /**
     * 判断所有编辑的图片是否加入相册
     * 如果加入相册后，更新所有数据
     */
    private fun handleEditImages() {
        // 循环所有数据
        for (localMedia in mAdapter.getLocalMedias()) {
            val editFile = localMedia.editorPath?.let { File(it) }
            editFile?.let {
                // 加入相册条件：1.相册界面直接打开的预览 2. 编辑过的
                if ((mPreviewType == PreviewType.ALBUM_ACTIVITY || mPreviewType == PreviewType.ALBUM_FRAGMENT) && mGlobalSpec.isAddAlbumByEdit) {
                    val uri = MediaStoreUtils.displayToGallery(
                        mContext,
                        editFile,
                        TYPE_PICTURE,
                        localMedia.duration,
                        localMedia.width,
                        localMedia.height
                    )
                    uri?.let {
                        localMedia.absolutePath = localMedia.editorPath as String
                        localMedia.uri = uri.toString()
                        // 从Uri中提取ID（最后一段数字）
                        localMedia.fileId = uri.lastPathSegment?.toLongOrNull() ?: -1
                        // 宽高刷新，这里能直接用absolutePath是因为编辑后的图片都是在App应用内存里面的
                        val imageWidthAndHeight: IntArray =
                            MediaUtils.getImageWidthAndHeight(localMedia.absolutePath)
                        localMedia.width = imageWidthAndHeight[0]
                        localMedia.height = imageWidthAndHeight[1]
                        // 大小
                        localMedia.size = editFile.length()
                        // 文件名称
                        localMedia.fileName = editFile.name
                        // 时间
                        localMedia.dateAddedTime = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    /**
     * 设置返回值
     */
    @Synchronized
    private fun setResultOk(localMedias: ArrayList<LocalMedia>) {
        LogUtil.d(TAG, "setResultOk")
        val intent = Intent()
        intent.putExtra(STATE_SELECTION, localMedias)
        requireActivity().setResult(RESULT_OK, intent)
        requireActivity().finish()
    }

    /**
     * 设置是否启用界面触摸，不可禁止中断、退出
     */
    private fun setControlTouchEnable(enable: Boolean) {
        // 如果不可用就显示 加载中 view,否则隐藏
        if (!enable) {
            mViewHolder.pbLoading.visibility = View.VISIBLE
            mViewHolder.buttonApply.visibility = View.GONE
            setCheckViewEnable(false)
            mViewHolder.checkView.setOnClickListener(null)
            mViewHolder.tvEdit.isEnabled = false
            mViewHolder.originalLayout.isEnabled = false
        } else {
            mViewHolder.pbLoading.visibility = View.GONE
            mViewHolder.buttonApply.visibility = View.VISIBLE
            setCheckViewEnable(true)
            mViewHolder.tvEdit.isEnabled = true
            mViewHolder.originalLayout.isEnabled = true
        }
        updateBottomToolbarVisibility()
    }

    /**
     * 右上角点击事件
     */
    private fun setCheckViewClickable() {
        val media = mLocalMedias[mViewPager2.currentItem]
        if (mSelectedModel.getSelectedData().isSelected(media)) {
            mSelectedModel.removeSelectedData(media, mViewPager2.currentItem)
            if (mAlbumSpec.countable) {
                mViewHolder.checkView.setCheckedNum(CheckView.UNCHECKED)
            } else {
                mViewHolder.checkView.setChecked(false)
            }
        } else {
            var isTrue = true
            if (mIsSelectedCheck) {
                isTrue = assertAddSelection(media)
            }
            if (isTrue) {
                val position = if (mIsDisplayCamera) {
                    mViewPager2.currentItem + 1
                } else {
                    mViewPager2.currentItem
                }
                mSelectedModel.addSelectedData(media, position)
                if (mAlbumSpec.countable) {
                    mViewHolder.checkView.setCheckedNum(
                        mSelectedModel.getSelectedData().checkedNumOf(
                            media
                        )
                    )
                } else {
                    mViewHolder.checkView.setChecked(true)
                }
            }
        }
        refreshSelectedState()
        mAlbumSpec.onSelectedListener?.let {
            if (mPreviewType == PreviewType.ALBUM_ACTIVITY || mPreviewType == PreviewType.ALBUM_FRAGMENT) {
                // 触发选择的接口事件
                it.onSelected(mSelectedModel.getSelectedData().getSelectedMediaArrayList())
            }
        }
    }

    private fun refreshSelectedState() {
        updateApplyButton()
        updatePreviewSelectedMedia()
    }

    /**
     * 更新确定按钮状态
     */
    private fun updateApplyButton() {
        val selectedCount = mSelectedModel.getSelectedData().count()
        // 单选在预览页确定时才产生选中数据，因此确定按钮始终可用
        val enabled = mAlbumSpec.singleSelectionModeEnabled() || selectedCount > 0
        mViewHolder.buttonApply.isEnabled = enabled
        mViewHolder.buttonApply.alpha = if (enabled) 1F else 0.5F
        mViewHolder.buttonApply.text =
            if (mAlbumSpec.singleSelectionModeEnabled() || selectedCount == 0) {
                getString(R.string.z_multi_library_single_preview_next)
            } else {
                getString(R.string.z_multi_library_button_next, selectedCount)
            }

        // 判断是否启动操作
        if (!mApplyEnable) {
            mViewHolder.checkView.visibility = View.GONE
            mViewHolder.buttonApply.visibility = View.GONE
        } else {
            mViewHolder.checkView.visibility =
                if (mAlbumSpec.singleSelectionModeEnabled()) View.GONE else View.VISIBLE
            mViewHolder.buttonApply.visibility = View.VISIBLE
        }
        setCheckViewEnable(mSelectedEnable)
        updateBottomToolbarVisibility()
    }

    private fun updateBottomToolbarVisibility() {
        val hasSelectedMedia = mSelectedModel.getSelectedData().count() > 0
        mViewHolder.bottomToolbar.isVisible = mPreviewBarsVisible &&
            (mAlbumSpec.singleSelectionModeEnabled() || hasSelectedMedia)
    }

    /**
     * 更新底部已选择媒体列表和顶部进度
     */
    private fun updatePreviewSelectedMedia() {
        val selectedCount = mSelectedModel.getSelectedData().count()
        val isFromSelectedMedia = mIsSelectedDataSource
        mViewHolder.tvTitleCount.isVisible = !mAlbumSpec.singleSelectionModeEnabled() && mIsSelectedDataSource
        mViewHolder.tvTitleCount.text = String.format(
            Locale.getDefault(), "%d/%d", mViewPager2.currentItem + 1, mLocalMedias.size
        )
        mViewHolder.rvSelectedMedia.isVisible =
            !mAlbumSpec.singleSelectionModeEnabled() && selectedCount > 0
        if (isFromSelectedMedia) {
            mPreviewSelectedBarAdapter.submitList(mLocalMedias.toList())
        } else {
            mPreviewSelectedBarAdapter.submitList(
                mSelectedModel.getSelectedData().getSelectedMediaArrayList()
            )
        }
        mPreviewSelectedBarAdapter.currentPreviewFileId =
            mLocalMedias.getOrNull(mViewPager2.currentItem)?.fileId ?: Long.MIN_VALUE
        mPreviewSelectedBarAdapter.deselectedMediaIds =
            if (mIsSelectedDataSource) {
                mLocalMedias
                    .filter { !mSelectedModel.getSelectedData().isSelected(it) }
                    .map { it.fileId }
                    .toSet()
            } else {
                emptySet()
            }
    }

    /**
     * 判断是否压缩，如果要压缩先要迁移复制再压缩
     */
    private fun compressFile() {
        // 显示loading动画
        setControlTouchEnable(false)

        // 复制相册的文件
        compressFileJob()
    }

    /**
     * 复制相册的文件的协程
     */
    private fun compressFileJob() {
        mCompressFileJob?.cancel()
        mCompressFileJob = lifecycleScope.request {
            handleEditImages()
            val fragment = weakFragment.get()
            fragment?.let {
                // 是否只压缩编辑的图片
                val isOnlyCompressEditPicture =
                    fragment.mPreviewType == PreviewType.GRID || fragment.mPreviewType == PreviewType.THIRD_PARTY
                return@request fragment.mAlbumCompressFileTask.compressFileTaskDoInBackground(
                    fragment.mSelectedModel.getSelectedData().getSelectedMediaArrayList(),
                    isOnlyCompressEditPicture
                )
            } ?: let {
                return@request ArrayList()
            }
        }.onSuccess { data ->
            val fragment = weakFragment.get()
            fragment?.setResultOk(data)
        }.onFail { error ->
            val fragment = weakFragment.get()
            fragment?.let {
                // 结束loading
                fragment.setControlTouchEnable(true)
                Toast.makeText(fragment.mContext, error.message, Toast.LENGTH_SHORT).show()
                error.message?.let {
                    LogUtil.e(TAG, it)
                }
            }
        }.onCancel {
            val fragment = weakFragment.get()
            fragment?.let {
                // 结束loading
                fragment.setControlTouchEnable(true)
            }
        }.launch()
    }

    /**
     * 处理窗口
     *
     * @param item 当前图片
     * @return 为true则代表符合规则
     */
    private fun assertAddSelection(item: LocalMedia): Boolean {
        val cause = mSelectedModel.getSelectedData().isAcceptable(item)
        IncapableCause.handleCause(mContext, cause)
        return cause == null
    }

    /**
     * 设置checkView是否启动，配置优先
     *
     * @param enable 是否启动
     */
    private fun setCheckViewEnable(enable: Boolean) {
        if (mSelectedEnable) {
            mViewHolder.checkView.isEnabled = enable
        } else {
            mViewHolder.checkView.isEnabled = false
        }
    }

    /**
     * 是否开启共享动画
     */
    private fun isSharedAnimation(): Boolean {
        return mPreviewType == PreviewType.ALBUM_FRAGMENT
    }

    /**
     * 第一次加载Adapter时候触发事件
     *
     * @param holder 预览的view
     */
    private fun onViewFirstAttachedToWindow(holder: PreviewPagerAdapter.PreviewViewHolder) {
        if (mIsSavedInstanceState) {
            return
        }
        if (isSharedAnimation()) {
            setImageViewScaleType(holder, mLocalMedias[getDataPosition()])
        }
    }

    /**
     * 设置图片的scaleType
     * @param holder 预览的view
     * @param media 实体
     */
    private fun setImageViewScaleType(
        holder: PreviewPagerAdapter.PreviewViewHolder, media: LocalMedia
    ) {
        if (media.width == 0 && media.height == 0) {
            holder.imageView.scaleType = ScaleType.FIT_CENTER
        } else {
            // 这个才能保持跟RecyclerView的item一样scaleType，不然突兀
            holder.imageView.scaleType = ScaleType.CENTER_CROP
        }
    }

    /**
     * 开启了共享动画
     *
     * @param position 索引
     */
    private fun startSharedAnimation(position: Int) {
        // 先隐藏viewPager,等共享动画结束后，再显示viewPager
        mViewPager2.alpha = 0F
        mMainModel.viewModelScope.launch {
            val media = mLocalMedias[position]
            val mediaRealSize = getMediaRealSizeFromMedia(media)
            val width = mediaRealSize[0]
            val height = mediaRealSize[1]
            val viewParams = RecycleItemViewParams.getItem(getViewPosition())

            if (viewParams == null || width == 0 && height == 0) {
                mViewHolder.sharedAnimationView.startNormal(width, height, false)
                mViewHolder.sharedAnimationView.setBackgroundAlpha(1F)
                mViewHolder.bottomToolbar.alpha = 1F
                mViewHolder.videoPlaybackBar.alpha = 1F
                mViewHolder.constraintLayout.alpha = 1F
            } else {
                // 将记录好的RecyclerView的位置大小，进行动画扩大到width,height
                mViewHolder.sharedAnimationView.setViewParams(
                    viewParams.left,
                    viewParams.top,
                    viewParams.width,
                    viewParams.height,
                    width,
                    height
                )
                mViewHolder.sharedAnimationView.start(false)
            }
            // 50毫秒时间渐变显示viewPager2,这样可以不会太生硬
            val objectAnimator = ObjectAnimator.ofFloat(mViewPager2, "alpha", 0F, 1F)
            objectAnimator.duration = 50
            objectAnimator.start()
        }
    }

    /**
     * 设置共享参数，主要是为了退出时的共享动画
     */
    private fun setSharedAnimationViewParams(position: Int) {
        mMainModel.viewModelScope.launch {
            val media = mLocalMedias[position]
            val mediaSize = getMediaRealSizeFromMedia(media)
            val width = mediaSize[0]
            val height = mediaSize[1]
            val viewParams = RecycleItemViewParams.getItem(getViewPosition())
            if (viewParams == null || width == 0 || height == 0) {
                mViewHolder.sharedAnimationView.setViewParams(0, 0, 0, 0, width, height)
            } else {
                mViewHolder.sharedAnimationView.setViewParams(
                    viewParams.left,
                    viewParams.top,
                    viewParams.width,
                    viewParams.height,
                    width,
                    height
                )
            }
        }
    }

    /**
     * 获取LocalMedia的宽高
     * @param media 文件
     */
    private suspend fun getMediaRealSizeFromMedia(media: LocalMedia): IntArray {
        var realWidth = media.width
        var realHeight = media.height
        if (MediaUtils.isLongImage(realWidth, realHeight)) {
            return intArrayOf(screenWidth, screenHeight)
        }
        // 如果宽高其中一个<=0  重新获取宽高。如果宽度大于高度也要重新获取，因为有可能是横拍，要根据角度判断重新反转宽高
        if ((realWidth <= 0 || realHeight <= 0)) {
            withContext(Dispatchers.IO) {
                MediaUtils.getMediaInfo(requireContext(), media.getMediaType(), media.uri.toUri())
                    .let {
                        if (it.width > 0) {
                            realWidth = it.width
                        }
                        if (it.height > 0) {
                            realHeight = it.height
                        }
                    }
            }
        }
        // 如果是裁剪后的图片，宽高是会改变的 TODO
//        if ((media.isCrop() || media.isEditor()) && media.cropWidth > 0 && media.cropHeight > 0) {
//            realWidth = media.cropWidth
//            realHeight = media.cropHeight
//        }
        return intArrayOf(realWidth, realHeight)
    }

    /**
     * 开始共享动画完成后
     */
    private fun onSharedBeginAnimComplete(
        sharedAnimationView: SharedAnimationView?,
        showImmediately: Boolean
    ) {
        val currentHolder = mAdapter.getCurrentViewHolder(mViewPager2.currentItem) ?: return
        val media = mLocalMedias[mViewPager2.currentItem]
//        val isResetSize = (media.isCrop() || media.isEditor()) && media.cropWidth > 0 && media.cropHeight > 0
//        val realWidth = if (isResetSize) media.cropWidth else media.width
//        val realHeight = if (isResetSize) media.cropHeight else media.height
        val realWidth = media.width
        val realHeight = media.height
        if (MediaUtils.isLongImage(realWidth, realHeight)) {
            currentHolder.imageView.scaleType = ScaleType.CENTER_CROP
        } else {
            currentHolder.imageView.scaleType = ScaleType.FIT_CENTER
        }
    }

    /**
     * 开始 退出共享动画
     */
    private fun onSharedBeginBackMinAnim() {
        val currentHolder = mAdapter.getCurrentViewHolder(mViewPager2.currentItem) ?: return
        if (currentHolder.imageView.isGone) {
            currentHolder.imageView.visibility = View.VISIBLE
        }
        mViewHolder.videoPlaybackBar.visibility = View.GONE
    }

    /**
     * 结束 退出共享动画
     * 设置预览 view 跟相册的 view 一样的高度宽度
     */
    private fun onSharedBeginBackMinFinish(isResetSize: Boolean) {
        val itemViewParams = RecycleItemViewParams.getItemViewParams(mViewPager2.currentItem) ?: return
        val currentHolder = mAdapter.getCurrentViewHolder(mViewPager2.currentItem) ?: return
        val layoutParams = currentHolder.imageView.layoutParams
        layoutParams?.width = itemViewParams.width
        layoutParams?.height = itemViewParams.height
        currentHolder.imageView.scaleType = ScaleType.CENTER_CROP
    }

    private fun onBackgroundAlpha(alpha: Float) {
        mViewHolder.sharedAnimationView.setBackgroundAlpha(alpha)
        mViewHolder.bottomToolbar.alpha = alpha
        mViewHolder.videoPlaybackBar.alpha = alpha
        mViewHolder.constraintLayout.alpha = alpha
    }

    /**
     * 共享动画结束，退出fragment
     */
    private fun onSharedViewFinish() {
        if (!isAdded) {
            return
        }
        requireActivity().supportFragmentManager.popBackStack()
    }

    /**
     * 滑动事件
     */
    private fun onViewPageSelected(position: Int) {
        if (mIsDisplayCamera) {
            mMainModel.previewPosition = position + 1
        } else {
            mMainModel.previewPosition = position
        }
        if (isSharedAnimation()) {
            if (mFirstSharedAnimation) {
                startSharedAnimation(position)
                mFirstSharedAnimation = false
            } else {
                // 获取目前可见的view,如果不可见,则将RecyclerView移动到可见
                if (position < RecycleItemViewParams.firstPosition || position > RecycleItemViewParams.lastPosition) {
                    // 移动相册的位置,并且重新赋值RecycleItemViewParams
                    mMainModel.onViewPageSelected(position)
                } else {
                    setSharedAnimationViewParams(position)
                }
            }
        }
    }

    /**
     * 更新ui
     * 如果当前item是gif就显示多少M的文本
     * 如果当前item是video就显示播放按钮
     *
     * @param item 当前图片
     */
    @SuppressLint("SetTextI18n")
    private fun updateUi(item: LocalMedia) {
        mViewHolder.groupOriginal.visibility = View.GONE
        mViewHolder.tvEdit.visibility = View.GONE
        if (item.isGif()) {
            mViewHolder.tvSize.visibility = View.VISIBLE
            mViewHolder.tvSize.text = "(${PhotoMetadataUtils.getSizeInMb(item.size)}M)"
        } else {
            mViewHolder.tvSize.visibility = View.GONE
        }
    }

    /**
     * 更新原图按钮状态
     */
    private fun updateOriginalState() {
        // 设置原图按钮根据配置来
        mViewHolder.original.setChecked(mMainModel.getOriginalEnable())
        mOriginalManage.updateOriginalState()
    }

    /**
     * 返回数据源的索引,因为由于相册界面可能会有Add,预览界面没有Add,所以两者的postion会出现不一样的情况
     */
    private fun getDataPosition(): Int {
        return if (mIsDisplayCamera) {
            mMainModel.previewPosition - 1
        } else {
            mMainModel.previewPosition
        }
    }

    /**
     * 返回视图索引,因为由于相册界面可能会有Add,预览界面没有Add,所以两者的postion会出现不一样的情况
     */
    private fun getViewPosition(): Int {
        return mMainModel.previewPosition
    }

    /**
     * 安全获取主题属性颜色，没有定义则返回默认白色，解决库崩溃问题
     */
    private fun getThemeAttrColor(context: Context, attrResId: Int, defaultColor: Int = Color.WHITE): Int {
        val typedArray = context.obtainStyledAttributes(intArrayOf(attrResId))
        return try {
            // 取不到就用默认值，绝对不崩
            typedArray.getColor(0, defaultColor)
        } catch (e: Exception) {
            defaultColor
        } finally {
            typedArray.recycle()
        }
    }

    /**
     * 兼容 API 21+ 安全获取 ColorStateList
     * 有主题取主题，无主题取默认XML选择器
     */
    private fun getThemeColorStateList(context: Context, @AttrRes attrResId: Int, @ColorRes defaultResId: Int): ColorStateList? {
        val ta: TypedArray = context.obtainStyledAttributes(intArrayOf(attrResId))
        return try {
            // 兼容方式获取主题色
            val csl = ta.getColorStateList(0)
            // 取不到 → 用兼容方法获取默认selector
            csl ?: ContextCompat.getColorStateList(context, defaultResId)
        } catch (e: Exception) {
            // 异常兜底
            ContextCompat.getColorStateList(context, defaultResId)
        } finally {
            ta.recycle()
        }
    }

    /**
     * 滑动事件
     */
    private val mOnPageChangeCallback: OnPageChangeCallback = object : OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            val adapter = mViewPager2.adapter as PreviewPagerAdapter
            Log.d("PreviewPlayback", "page selected position=$position currentItem=${mViewPager2.currentItem}")
            if (::mAdapter.isInitialized) {
                mAdapter.pausePlayback()
            }
            val item = adapter.getLocalMedia(position)
            item?.let {
                if (mAlbumSpec.countable) {
                    val checkedNum = mSelectedModel.getSelectedData().checkedNumOf(item)
                    mViewHolder.checkView.setCheckedNum(checkedNum)
                    if (checkedNum > 0) {
                        setCheckViewEnable(true)
                    } else {
                        setCheckViewEnable(!mSelectedModel.getSelectedData().maxSelectableReached())
                    }
                } else {
                    val checked = mSelectedModel.getSelectedData().isSelected(item)
                    mViewHolder.checkView.setChecked(checked)
                    if (checked) {
                        setCheckViewEnable(true)
                    } else {
                        setCheckViewEnable(!mSelectedModel.getSelectedData().maxSelectableReached())
                    }
                }
                updateUi(item)
                updatePlaybackBar(item)
                updatePlaybackProgress(item)
            }
            updatePreviewSelectedMedia()
            updateBottomToolbarVisibility()
            onViewPageSelected(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            Log.d(
                "PreviewPlayback",
                "page scroll state=$state current=${mViewPager2.currentItem} childCount=${mViewPager2.childCount}"
            )
            if (state == ViewPager2.SCROLL_STATE_DRAGGING && ::mAdapter.isInitialized) {
                mAdapter.pausePlayback()
            }
        }
    }

    class ViewHolder internal constructor(rootView: View) {
        var sharedAnimationView: SharedAnimationView =
            rootView.findViewById(R.id.sharedAnimationView)
        var iBtnBack: ImageView = rootView.findViewById(R.id.iBtnBack)
        var tvEdit: TextView = rootView.findViewById(R.id.tvEdit)
        var groupOriginal: Group = rootView.findViewById(R.id.groupOriginal)
        var original: CheckRadioView = rootView.findViewById(R.id.original)
        var tvOriginal: TextView = rootView.findViewById(R.id.tvOriginal)
        var originalLayout: View = rootView.findViewById(R.id.originalLayout)
        var tvSize: TextView = rootView.findViewById(R.id.tvSize)
        var buttonApply: TextView = rootView.findViewById(R.id.buttonApply)
        var bottomToolbar: ConstraintLayout = rootView.findViewById(R.id.bottomToolbar)
        var constraintLayout: ConstraintLayout = rootView.findViewById(R.id.constraintLayout)
        var checkView: CheckView = rootView.findViewById(R.id.checkView)
        var tvTitleCount: TextView = rootView.findViewById(R.id.tvTitleCount)
        var rvSelectedMedia: RecyclerView = rootView.findViewById(R.id.rvPreviewSelectedMedia)
        var pbLoading: ProgressBar = rootView.findViewById(R.id.pbLoading)
        var videoPlaybackBar: View = rootView.findViewById(R.id.videoPlaybackBar)
        var videoPlayPauseButton: ImageView = rootView.findViewById(R.id.video_play_pause_button)
        var tvCurrentTime: TextView = rootView.findViewById(R.id.tv_current_time)
        var videoSeekBar: SeekBar = rootView.findViewById(R.id.video_seek_bar)
        var tvDuration: TextView = rootView.findViewById(R.id.tv_duration)
    }

}
