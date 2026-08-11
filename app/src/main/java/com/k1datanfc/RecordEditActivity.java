package com.k1datanfc;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class RecordEditActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID  = "group_id";
    public static final String EXTRA_RECORD_ID = "record_id";

    private static final int REQ_GALLERY  = 101;
    private static final int REQ_CAMERA   = 102;
    private static final int REQ_STORAGE  = 103;
    private static final int REQ_CAM_PERM = 104;

    private DatabaseManager   dbManager;
    private NfcTag            currentTag;
    private TagRecord         currentRecord;
    private boolean           isNew;
    private String            replacingImagePath = null;
    private Uri               cameraImageUri     = null;
    private File              cameraImageFile    = null;

    private TextInputEditText etTitle, etNote;
    private LinearLayout      imagesContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_edit);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        dbManager = K1Application.getInstance().getDatabaseManager();

        String groupId  = getIntent().getStringExtra(EXTRA_GROUP_ID);
        String recordId = getIntent().getStringExtra(EXTRA_RECORD_ID);

        currentTag = dbManager.findTagByGroupId(groupId);
        if (currentTag == null) { finish(); return; }

        if (recordId != null) { currentRecord = currentTag.findRecord(recordId); isNew = false; }
        if (currentRecord == null) { currentRecord = new TagRecord(); isNew = true; }

        setupViews();
        populateFields();
    }

    private void setupViews() {
        getSupportActionBar().setTitle(isNew ? "رکورد جدید" : "ویرایش رکورد");
        etTitle         = findViewById(R.id.et_record_title);
        etNote          = findViewById(R.id.et_record_note);
        imagesContainer = findViewById(R.id.images_container);

        Button btnAddImage = findViewById(R.id.btn_add_image);
        btnAddImage.setOnClickListener(v -> showImageSourceDialog(null));

        ExtendedFloatingActionButton fabSave = findViewById(R.id.fab_save_record);
        fabSave.setOnClickListener(v -> saveRecord());
    }

    private void showImageSourceDialog(String replacePath) {
        replacingImagePath = replacePath;
        new AlertDialog.Builder(this)
                .setTitle(replacePath != null ? "تغییر تصویر" : "افزودن تصویر")
                .setItems(new String[]{"📷  دوربین", "🖼️  گالری"}, (d, w) -> {
                    if (w == 0) openCamera(); else openGallery();
                }).show();
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAM_PERM);
            return;
        }
        launchCamera();
    }

    private void launchCamera() {
        try {
            cameraImageFile = File.createTempFile("k1_photo_", ".jpg", getCacheDir());
            cameraImageUri  = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", cameraImageFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            startActivityForResult(intent, REQ_CAMERA);
        } catch (IOException e) {
            Toast.makeText(this, "خطا در باز کردن دوربین", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
                return;
            }
        }
        Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        i.setType("image/*");
        startActivityForResult(i, REQ_GALLERY);
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) return;
        if (req == REQ_CAM_PERM) launchCamera();
        else if (req == REQ_STORAGE) openGallery();
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK) { replacingImagePath = null; cameraImageFile = null; return; }

        boolean fromCamera = (req == REQ_CAMERA);
        Uri imageUri = fromCamera ? cameraImageUri : (data != null ? data.getData() : null);
        if (imageUri == null) { replacingImagePath = null; return; }

        final String oldPath = replacingImagePath;
        replacingImagePath   = null;
        final Uri   finalUri = imageUri;
        final File  camFile  = cameraImageFile;
        cameraImageFile      = null;

        new Thread(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(finalUri);
                byte[] raw = readStream(is);
                Bitmap bmp = BitmapFactory.decodeByteArray(raw, 0, raw.length);
                if (fromCamera && camFile != null) bmp = fixExifRotation(bmp, camFile.getAbsolutePath());
                else bmp = fixExifRotationUri(bmp, finalUri);
                bmp = resizeIfNeeded(bmp, 1920);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 82, baos);
                String saved = dbManager.saveEncryptedImage(baos.toByteArray(), currentTag.getGroupId());
                if (saved != null) {
                    if (oldPath != null) {
                        dbManager.deleteImageFile(oldPath);
                        currentRecord.removeImagePath(oldPath);
                        currentRecord.addImagePath(saved);
                        runOnUiThread(this::loadImages);
                    } else {
                        currentRecord.addImagePath(saved);
                        runOnUiThread(() -> addImageView(saved));
                    }
                }
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "خطا در بارگذاری تصویر", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void populateFields() {
        etTitle.setText(currentRecord.getTitle());
        etNote.setText(currentRecord.getNote());
        loadImages();
    }

    private void loadImages() {
        imagesContainer.removeAllViews();
        if (currentRecord.getImagePaths() == null) return;
        for (String path : currentRecord.getImagePaths()) addImageView(path);
    }

    private void addImageView(String path) {
        View v = getLayoutInflater().inflate(R.layout.item_image, imagesContainer, false);
        android.widget.ImageView iv = v.findViewById(R.id.image_view);
        ImageButton btnDel = v.findViewById(R.id.btn_delete_image);

        new Thread(() -> {
            byte[] bytes = dbManager.loadDecryptedImage(path);
            if (bytes != null) {
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                runOnUiThread(() -> iv.setImageBitmap(bmp));
            }
        }).start();

        // Tap = full screen view
        iv.setOnClickListener(vv -> openFullScreen(path));
        // Long press = replace
        iv.setOnLongClickListener(vv -> { showImageSourceDialog(path); return true; });

        btnDel.setOnClickListener(vv ->
            new AlertDialog.Builder(this)
                .setMessage("این تصویر حذف شود؟")
                .setPositiveButton("بله", (d, w) -> {
                    dbManager.deleteImageFile(path);
                    currentRecord.removeImagePath(path);
                    imagesContainer.removeView(v);
                })
                .setNegativeButton("خیر", null).show()
        );
        btnDel.setVisibility(View.VISIBLE);
        imagesContainer.addView(v);
    }

    /** Open full-screen image viewer */
    private void openFullScreen(String path) {
        Intent intent = new Intent(this, FullScreenImageActivity.class);
        intent.putExtra(FullScreenImageActivity.EXTRA_IMAGE_PATH, path);
        startActivity(intent);
    }

    private void saveRecord() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String note  = etNote.getText()  != null ? etNote.getText().toString().trim()  : "";
        if (title.isEmpty()) { etTitle.setError("عنوان الزامی است"); etTitle.requestFocus(); return; }
        currentRecord.setTitle(title);
        currentRecord.setNote(note);
        if (isNew) {
            currentTag.addRecord(currentRecord);
        } else {
            for (int i = 0; i < currentTag.getRecords().size(); i++) {
                if (currentTag.getRecords().get(i).getRecordId().equals(currentRecord.getRecordId())) {
                    currentTag.getRecords().set(i, currentRecord); break;
                }
            }
        }
        dbManager.saveTag(currentTag);
        Toast.makeText(this, isNew ? "رکورد ذخیره شد" : "رکورد به‌روز شد", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    // ── EXIF helpers ──────────────────────────────────────────────────

    private Bitmap fixExifRotation(Bitmap bmp, String filePath) {
        try {
            ExifInterface exif = new ExifInterface(filePath);
            return rotateBitmap(bmp, exifToDegrees(exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)));
        } catch (Exception e) { return bmp; }
    }

    private Bitmap fixExifRotationUri(Bitmap bmp, Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return bmp;
            ExifInterface exif = new ExifInterface(is); is.close();
            return rotateBitmap(bmp, exifToDegrees(exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)));
        } catch (Exception e) { return bmp; }
    }

    private int exifToDegrees(int o) {
        if (o == ExifInterface.ORIENTATION_ROTATE_90)  return 90;
        if (o == ExifInterface.ORIENTATION_ROTATE_180) return 180;
        if (o == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        return 0;
    }

    private Bitmap rotateBitmap(Bitmap bmp, int deg) {
        if (deg == 0) return bmp;
        Matrix m = new Matrix(); m.postRotate(deg);
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
    }

    private Bitmap resizeIfNeeded(Bitmap src, int maxSide) {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxSide && h <= maxSide) return src;
        float scale = maxSide / (float) Math.max(w, h);
        return Bitmap.createScaledBitmap(src, (int)(w*scale), (int)(h*scale), true);
    }

    private byte[] readStream(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096]; int len;
        while ((len = is.read(chunk)) != -1) buf.write(chunk, 0, len);
        return buf.toByteArray();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            new AlertDialog.Builder(this)
                    .setMessage("تغییرات ذخیره نشده‌اند. خارج می‌شوید؟")
                    .setPositiveButton("بله", (d, w) -> finish())
                    .setNegativeButton("خیر", null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
