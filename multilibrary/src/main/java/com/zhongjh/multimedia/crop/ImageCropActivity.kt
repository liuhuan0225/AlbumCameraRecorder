package com.zhongjh.multimedia.crop

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import com.zhongjh.common.utils.LogUtil
import com.zhongjh.multimedia.R
import com.zhongjh.multimedia.databinding.ActivityImageCropBinding
import com.zhongjh.multimedia.locale.AlbumLocale
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 固定比例图片裁剪页，只提供居中比例裁剪
 */
class ImageCropActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AlbumLocale.wrap(newBase))
    }

    private lateinit var binding: ActivityImageCropBinding
    private var imageSource: String? = null
    private var savePath: String? = null
    private val topBarNavigationHeight by lazy {
        resources.getDimensionPixelSize(R.dimen.crop_top_bar_navigation_height)
    }
    private val bottomBarBaseHeight by lazy {
        resources.getDimensionPixelSize(R.dimen.crop_bottom_bar_height)
    }
    private val buttonBaseBottomMargin by lazy {
        resources.getDimensionPixelSize(R.dimen.crop_button_margin_bottom)
    }
    private val mCacheMatrix = Matrix()
    private val mCacheRect = android.graphics.RectF()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityImageCropBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupStatusBarInsets()

        imageSource = intent.getStringExtra(EXTRA_IMAGE_URI)
        savePath = intent.getStringExtra(EXTRA_IMAGE_SAVE_PATH)
        val ratioX = intent.getFloatExtra(EXTRA_RATIO_X, DEFAULT_RATIO)
        val ratioY = intent.getFloatExtra(EXTRA_RATIO_Y, DEFAULT_RATIO)

        binding.cropOverlayView.setRatio(ratioX, ratioY)
        binding.cropOverlayView.setMaskImageResource(intent.getIntExtra(EXTRA_MASK_IMAGE, 0))
        binding.cropOverlayView.doOnLayout {
            binding.imageView.setCropRect(binding.cropOverlayView.getCropRect())
        }
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonConfirm.setOnClickListener { confirmCrop() }
        binding.imageView.doOnLayout { loadImage() }
    }

    private fun setupStatusBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            binding.topBar.layoutParams.height = statusBarHeight + topBarNavigationHeight
            binding.statusBarSpace.layoutParams.height = statusBarHeight
            binding.bottomBar.layoutParams.height = bottomBarBaseHeight + navigationBarHeight
            (binding.buttonConfirm.layoutParams as android.view.ViewGroup.MarginLayoutParams).bottomMargin =
                buttonBaseBottomMargin + navigationBarHeight
            binding.topBar.requestLayout()
            binding.statusBarSpace.requestLayout()
            binding.bottomBar.requestLayout()
            binding.buttonConfirm.requestLayout()
            insets
        }
    }

    private fun loadImage() {
        val source = imageSource
        if (source.isNullOrBlank()) {
            finish()
            return
        }
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { decodeBitmap(source) }
                    .onFailure { LogUtil.e(TAG, "decode crop image failed: $source", it) }
                    .getOrNull()
            }
            if (bitmap == null || isFinishing || isDestroyed) {
                Toast.makeText(this@ImageCropActivity, R.string.z_load_media_failed, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            binding.imageView.setImageBitmap(bitmap)
        }
    }

    private fun decodeBitmap(source: String): Bitmap? {
        val inputStream = openSourceStream(source) ?: return null
        val orientation = inputStream.use { stream ->
            runCatching { ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .map { orientationValue ->
                    when (orientationValue) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        ExifInterface.ORIENTATION_TRANSPOSE -> 90
                        ExifInterface.ORIENTATION_TRANSVERSE -> 270
                        else -> 0
                    }
                }.getOrDefault(0)
        }
        openSourceStream(source)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            val maxImageSize = max(options.outWidth, options.outHeight)
            val displayWidth = binding.imageView.width.coerceAtLeast(1)
            val displayHeight = binding.imageView.height.coerceAtLeast(1)
            val maxDisplaySize = max(displayWidth, displayHeight)
            var sampleSize = 1
            while (maxImageSize / (sampleSize * 2) >= maxDisplaySize) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            openSourceStream(source)?.use { decodeStream ->
                val bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions) ?: return null
                return rotateBitmap(bitmap, orientation)
            } ?: return null
        } ?: return null
    }

    /**
     * Android 9 及以下 MediaStore 返回的是绝对路径，Android 10+ 返回 content://。
     * 这里统一兼容纯路径、file:// 和 content:// 三种来源。
     */
    private fun openSourceStream(source: String): InputStream? {
        val uri = Uri.parse(source)
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            null, "" -> File(source).inputStream()
            ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).inputStream() }
            else -> contentResolver.openInputStream(uri)
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) {
            return bitmap
        }
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun confirmCrop() {
        val source = imageSource ?: return
        val output = savePath
        val cropRect = binding.cropOverlayView.getCropRect()
        if (output.isNullOrBlank() || cropRect.isEmpty) {
            finish()
            return
        }
        binding.buttonConfirm.isEnabled = false
        lifecycleScope.launch {
            val size = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = decodeBitmap(source) ?: throw IllegalStateException("图片解码失败")
                    val croppedBitmap = cropBitmap(bitmap, cropRect)
                    writeBitmap(croppedBitmap, output)
                    croppedBitmap.width to croppedBitmap.height
                }.getOrNull()
            }
            if (size == null) {
                binding.buttonConfirm.isEnabled = true
                Toast.makeText(this@ImageCropActivity, R.string.z_load_media_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val intent = Intent()
                .putExtra(EXTRA_IMAGE_SAVE_PATH, output)
                .putExtra(EXTRA_SAVE_OUTPUT_PATH, output)
                .putExtra(EXTRA_WIDTH, size.first)
                .putExtra(EXTRA_HEIGHT, size.second)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    private fun cropBitmap(source: Bitmap, screenRect: android.graphics.RectF): Bitmap {
        val sourceRect = binding.imageView.getVisibleSourceRect(screenRect)
        val left = sourceRect.left.coerceIn(0f, source.width.toFloat())
        val top = sourceRect.top.coerceIn(0f, source.height.toFloat())
        val right = sourceRect.right.coerceIn(0f, source.width.toFloat())
        val bottom = sourceRect.bottom.coerceIn(0f, source.height.toFloat())
        val width = ceil(right - left).toInt().coerceAtLeast(1)
        val height = ceil(bottom - top).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(source, left.toInt(), top.toInt(), width, height)
    }

    private fun writeBitmap(bitmap: Bitmap, output: String) {
        File(output).parentFile?.mkdirs()
        FileOutputStream(output).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            stream.flush()
        }
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        super.onBackPressed()
    }

    companion object {
        const val EXTRA_IMAGE_URI = "EXTRA_IMAGE_URI"
        const val EXTRA_IMAGE_SAVE_PATH = "EXTRA_IMAGE_SAVE_PATH"
        const val EXTRA_RATIO_X = "EXTRA_RATIO_X"
        const val EXTRA_RATIO_Y = "EXTRA_RATIO_Y"
        const val EXTRA_MASK_IMAGE = "EXTRA_MASK_IMAGE"
        const val EXTRA_WIDTH = "EXTRA_WIDTH"
        const val EXTRA_HEIGHT = "EXTRA_HEIGHT"
        const val EXTRA_SAVE_OUTPUT_PATH = "EXTRA_SAVE_OUTPUT_PATH"

        private const val DEFAULT_RATIO = 1f
        private const val JPEG_QUALITY = 95
        private val TAG = ImageCropActivity::class.java.simpleName

        @JvmStatic
        @JvmOverloads
        fun start(
            activity: Activity,
            sourceUri: String,
            output: String,
            ratioX: Float,
            ratioY: Float,
            maskImage: Int = 0
        ): Intent {
            return Intent(activity, ImageCropActivity::class.java)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(EXTRA_IMAGE_URI, sourceUri)
                .putExtra(EXTRA_IMAGE_SAVE_PATH, output)
                .putExtra(EXTRA_SAVE_OUTPUT_PATH, output)
                .putExtra(EXTRA_RATIO_X, ratioX)
                .putExtra(EXTRA_RATIO_Y, ratioY)
                .putExtra(EXTRA_MASK_IMAGE, maskImage)
        }
    }
}
