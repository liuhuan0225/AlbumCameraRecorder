package com.zhongjh.multimedia.crop

import android.app.Activity
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import com.zhongjh.multimedia.R
import com.zhongjh.multimedia.databinding.ActivityImageCropBinding
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 固定比例图片裁剪页，只提供居中比例裁剪
 */
class ImageCropActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageCropBinding
    private var imageUri: Uri? = null
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
        binding = ActivityImageCropBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupStatusBarInsets()

        imageUri = intent.getStringExtra(EXTRA_IMAGE_URI)?.let(Uri::parse)
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
        val uri = imageUri
        if (uri == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { decodeUriBitmap(uri) }.getOrNull()
            }
            if (bitmap == null || isFinishing || isDestroyed) {
                Toast.makeText(this@ImageCropActivity, R.string.z_load_media_failed, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            binding.imageView.setImageBitmap(bitmap)
        }
    }

    private fun decodeUriBitmap(uri: Uri): Bitmap? {
        val inputStream = contentResolver.openInputStream(uri) ?: return null
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
        contentResolver.openInputStream(uri)?.use { stream ->
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
            contentResolver.openInputStream(uri)?.use { decodeStream ->
                val bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions) ?: return null
                return rotateBitmap(bitmap, orientation)
            } ?: return null
        } ?: return null
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) {
            return bitmap
        }
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun confirmCrop() {
        val uri = imageUri ?: return
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
                val bitmap = decodeUriBitmap(uri) ?: throw IllegalStateException("图片解码失败")
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

        fun start(
            activity: Activity,
            sourceUri: String,
            output: String,
            ratioX: Float,
            ratioY: Float,
            maskImage: Int = 0
        ): Intent {
            return Intent(activity, ImageCropActivity::class.java)
                .putExtra(EXTRA_IMAGE_URI, sourceUri)
                .putExtra(EXTRA_IMAGE_SAVE_PATH, output)
                .putExtra(EXTRA_SAVE_OUTPUT_PATH, output)
                .putExtra(EXTRA_RATIO_X, ratioX)
                .putExtra(EXTRA_RATIO_Y, ratioY)
                .putExtra(EXTRA_MASK_IMAGE, maskImage)
        }
    }
}
