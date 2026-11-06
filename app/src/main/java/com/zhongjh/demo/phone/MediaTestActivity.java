package com.zhongjh.demo.phone;

import android.content.Context;
import android.content.res.Configuration;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;

import com.bumptech.glide.Glide;
import com.zhongjh.common.entity.LocalMedia;
import com.zhongjh.common.enums.MimeType;
import com.zhongjh.demo.R;
import com.zhongjh.demo.configuration.Glide4Engine;
import com.zhongjh.demo.configuration.OnImageCompressionLuBan;
import com.zhongjh.demo.databinding.ActivityMediaTestBinding;
import com.zhongjh.common.utils.FileUtils;
import com.zhongjh.common.utils.MediaUtils;
import com.zhongjh.multimedia.crop.ImageCropActivity;
import com.zhongjh.multimedia.locale.AlbumLocale;
import com.zhongjh.multimedia.settings.AlbumSetting;
import com.zhongjh.multimedia.settings.GlobalSetting;
import com.zhongjh.multimedia.settings.MultiMediaSetting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A test page for moving media features into a real application.
 */
public class MediaTestActivity extends AppCompatActivity {

    private static final int ACTION_SELECT = 1;
    private static final int ACTION_CROP = 2;
    private static final int ACTION_COMPRESS = 3;
    private static final int MAX_MULTI_SELECT_COUNT = 9;

    private int pendingAction = ACTION_SELECT;
    private String actionTitle = "";

    /**
     * Opens the test page.
     */
    public static void newInstance(AppCompatActivity activity) {
        activity.startActivity(new Intent(activity, MediaTestActivity.class));
    }

    private ActivityMediaTestBinding binding;
    private OnImageCompressionLuBan imageCompression = new OnImageCompressionLuBan();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> albumLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                handleAlbumResult(result.getResultCode(), result.getData());
            });
    private final ActivityResultLauncher<Intent> directCropLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                handleDirectCropResult(result.getResultCode(), result.getData());
            });
    private final ActivityResultLauncher<Intent> localImagePicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Uri imageUri = result.getData().getData();
                if (imageUri != null) {
                    startDirectCrop(imageUri);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMediaTestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        AlbumLocale.setContextWrapper(this::wrapAlbumContext);

        binding.btnSingleImage.setOnClickListener(v ->
                openAlbum(MimeType.ofImage(), 1, ACTION_SELECT, "Single image"));
        binding.btnMultiImage.setOnClickListener(v ->
                openAlbum(MimeType.ofImage(), MAX_MULTI_SELECT_COUNT, ACTION_SELECT, "Multi image"));
        binding.btnSingleVideo.setOnClickListener(v ->
                openAlbum(MimeType.ofVideo(), 1, ACTION_SELECT, "Single video"));
        binding.btnMultiVideo.setOnClickListener(v ->
                openAlbum(MimeType.ofVideo(), MAX_MULTI_SELECT_COUNT, ACTION_SELECT, "Multi video"));
        binding.btnCropImage1x1.setOnClickListener(v ->
                openAlbum(MimeType.ofImage(), 1, ACTION_CROP, "Crop 1:1", 1f, 1f));
        binding.btnCropImage9x16.setOnClickListener(v ->
                openAlbum(MimeType.ofImage(), 1, ACTION_CROP, "Crop 9:16", 2f, 3f));
        binding.btnCropImageRoundMask.setOnClickListener(v ->
                openAlbum(MimeType.ofImage(), 1, ACTION_CROP, "Crop round mask", 1f, 1f,
                        R.drawable.crop_round_mask));
        binding.btnLocalImageCrop.setOnClickListener(v -> selectLocalImage());
        binding.btnCompressImage.setOnClickListener(v ->
                openAlbum(MimeType.ofImage(), 1, ACTION_COMPRESS, "Compress"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AlbumLocale.setContextWrapper(null);
        executor.shutdown();
    }

    private void openAlbum(Set<MimeType> mimeTypes, int maxCount, int action, String title) {
        openAlbum(mimeTypes, maxCount, action, title, 0f, 0f);
    }

    private Context wrapAlbumContext(Context base) {
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag("ar"));
        return base.createConfigurationContext(configuration);
    }

    private void openAlbum(Set<MimeType> mimeTypes, int maxCount, int action, String title,
                           float cropRatioX, float cropRatioY) {
        openAlbum(mimeTypes, maxCount, action, title, cropRatioX, cropRatioY, 0);
    }

    private void openAlbum(Set<MimeType> mimeTypes, int maxCount, int action, String title,
                           float cropRatioX, float cropRatioY, int cropMaskImage) {
        pendingAction = action;
        actionTitle = title;
        AlbumSetting albumSetting = new AlbumSetting(false)
                .isDisplayCamera(false)
                .mimeTypeSet(mimeTypes)
                .countable(true);
        if (action == ACTION_CROP) {
            albumSetting.cropEnable(true)
                    .cropRatio(cropRatioX, cropRatioY)
                    .cropMaskImage(cropMaskImage);
        }
        GlobalSetting globalSetting = MultiMediaSetting.from(this).choose(mimeTypes)
                .albumSetting(albumSetting)
                .imageEngine(new Glide4Engine());
        globalSetting.setOnImageCompressionListener(imageCompression);
        globalSetting.maxSelectablePerMediaType(
                maxCount,
                maxCount,
                maxCount,
                maxCount,
                0,
                0,
                0
        );
        globalSetting.forResult(albumLauncher);
    }

    private void selectLocalImage() {
        localImagePicker.launch(new Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*"));
    }

    private void startDirectCrop(Uri sourceUri) {
        String sourceUriString = sourceUri.toString();
        File output = new File(getCacheDir(), "media_test_crop_" + System.currentTimeMillis() + ".jpg");
        directCropLauncher.launch(ImageCropActivity.start(this, sourceUriString, output.getAbsolutePath(), 1f, 1f));
    }

    private void handleDirectCropResult(int resultCode, @Nullable Intent intent) {
        if (resultCode != RESULT_OK || intent == null) {
            binding.tvResult.setText(R.string.crop_failed);
            return;
        }
        String croppedPath = intent.getStringExtra(ImageCropActivity.EXTRA_SAVE_OUTPUT_PATH);
        if (TextUtils.isEmpty(croppedPath)) {
            binding.tvResult.setText(R.string.crop_failed);
            return;
        }
        File croppedFile = new File(croppedPath);
        if (!croppedFile.exists()) {
            binding.tvResult.setText(R.string.crop_failed);
            return;
        }
        actionTitle = "Direct crop";
        showMediaPaths(actionTitle, createCropResult(croppedFile, intent));
    }

    private List<LocalMedia> createCropResult(File croppedFile, Intent intent) {
        LocalMedia localMedia = new LocalMedia();
        localMedia.setEditorPath(croppedFile.getAbsolutePath());
        localMedia.setCompressPath(croppedFile.getAbsolutePath());
        localMedia.setSize(croppedFile.length());
        localMedia.setFileName(croppedFile.getName());
        localMedia.setWidth(intent.getIntExtra(ImageCropActivity.EXTRA_WIDTH, 0));
        localMedia.setHeight(intent.getIntExtra(ImageCropActivity.EXTRA_HEIGHT, 0));
        ArrayList<LocalMedia> mediaList = new ArrayList<>();
        mediaList.add(localMedia);
        return mediaList;
    }

    private void handleAlbumResult(int resultCode, @Nullable Intent intent) {
        if (resultCode != RESULT_OK || intent == null) return;
        List<LocalMedia> mediaList = MultiMediaSetting.obtainLocalMediaResult(intent);
        if (mediaList.isEmpty()) return;

        if (pendingAction == ACTION_COMPRESS) {
            compress(mediaList.get(0));
        } else {
            showMediaPaths(actionTitle, mediaList);
        }
    }

    private void showMediaPaths(String title, List<LocalMedia> mediaList) {
        StringBuilder builder = new StringBuilder(title);
        for (LocalMedia media : mediaList) {
            builder.append('\n').append(getAvailablePath(media));
        }
        binding.tvResult.setText(builder);
        showMediaResults(mediaList);
    }

    private void showMediaResults(List<LocalMedia> mediaList) {
        binding.llMediaResults.removeAllViews();
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        for (LocalMedia media : mediaList) {
            View itemView = layoutInflater.inflate(
                    R.layout.item_media_test_result,
                    binding.llMediaResults,
                    false
            );
            ImageView preview = itemView.findViewById(R.id.ivPreview);
            TextView path = itemView.findViewById(R.id.tvPath);
            TextView size = itemView.findViewById(R.id.tvSize);
            TextView dimensions = itemView.findViewById(R.id.tvDimensions);
            String availablePath = getAvailablePath(media);
            long fileSize = getFileSize(media);
            int[] imageDimensions = MediaUtils.INSTANCE.getImageWidthAndHeight(availablePath);

            Glide.with(this)
                    .load(availablePath)
                    .centerInside()
                    .into(preview);
            path.setText(availablePath);
            size.setText(getString(R.string.media_size, Formatter.formatShortFileSize(this, fileSize)));
            dimensions.setText(getString(
                    R.string.media_dimensions,
                    imageDimensions[0],
                    imageDimensions[1]
            ));
            TooltipCompat.setTooltipText(path, availablePath);
            binding.llMediaResults.addView(itemView);
        }
    }

    private long getFileSize(LocalMedia media) {
        if (media.getSize() > 0) {
            return media.getSize();
        }
        File file = new File(getAvailablePath(media));
        return file.exists() ? file.length() : 0L;
    }

    private String getAvailablePath(LocalMedia media) {
        String path = media.getAvailablePath();
        if (TextUtils.isEmpty(path)) {
            path = media.getAbsolutePath();
        }
        return path;
    }

    private String getUriString(LocalMedia media) {
        String uri = media.getUri();
        if (TextUtils.isEmpty(uri)) {
            return Uri.fromFile(new File(getAvailablePath(media))).toString();
        }
        return uri;
    }

    private void compress(LocalMedia media) {
        binding.tvResult.setText(R.string.compressing);
        executor.execute(() -> {
            try {
                File sourceFile = getSourceFile(media);
                File compressed = imageCompression.compressionFile(getApplicationContext(), sourceFile);
                String text = getString(R.string.compression_done)
                        + "\n" + compressed.getAbsolutePath()
                        + "\n" + (compressed.length() / 1024) + " KB";
                runOnUiThread(() -> binding.tvResult.setText(text));
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    binding.tvResult.setText(R.string.compression_failed);
                    Toast.makeText(this, R.string.compression_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private File getSourceFile(LocalMedia media) throws IOException {
        File sourceFile = new File(getAvailablePath(media));
        if (sourceFile.exists()) {
            return sourceFile;
        }
        return copyToCache(Uri.parse(getUriString(media)), "media_test_source.jpg");
    }

    private File copyToCache(Uri sourceUri, String fileName) throws IOException {
        File file = new File(getCacheDir(), fileName);
        if (file.exists()) {
            FileUtils.deleteFile(file);
        }
        try (InputStream inputStream = getContentResolver().openInputStream(sourceUri);
             FileOutputStream outputStream = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();
        }
        return file;
    }
}
