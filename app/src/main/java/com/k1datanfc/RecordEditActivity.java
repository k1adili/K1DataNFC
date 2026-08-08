package com.k1datanfc;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

    public static final String EXTRA_TAG_ID    = "tag_id";
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

        String tagId    = getIntent().getStringExtra(EXTRA_TAG_ID);
        String recordId = getIntent().getStringExtra(EXTRA_RECORD_ID);

        currentTag = dbManager.findTagById(tagId);
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
        btnAddImage.setOnClickListener(v -> showImageSourceDialog());

        ExtendedFloatingActionButton fabSave = findViewById(R.id.fab_save_record);
        fabSave.setOnClickListener(v -> saveRecord());
    }

    // ── Image source chooser ──────────────────────────────────────────

    private void showImageSourceDialog() {
        replacingImagePath = null;
        new AlertDialog.Builder(this)
                .setTitle("افزودن تصویر")
                .setItems(new String[]{"📷  دوربین", "🖼️  گالری"}, (d, which) -> {
                    if (which == 0) openCamera();
                    else            openGallery();
                })
                .show();
    }

    private void showReplaceImageDialog(String path) {
        replacingImagePath = path;
        new AlertDialog.Builder(this)
                .setTitle("تغییر تصویر")
                .setItems(new String[]{"📷  دوربین", "🖼️  گالری"}, (d, which) -> {
                    if (which == 0) openCamera();
                    else            openGallery();
                })
                .show();
    }

    // ── Camera ────────────────────────────────────────────────────────

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
            File photoFile = File.createTempFile("k1_photo_", ".jpg", getCacheDir());
            cameraImageUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            startActivityForResult(intent, REQ_CAMERA);
        } catch (IOException e) {
            Toast.makeText(this, "خطا در باز کردن دوربین", Toast.LENGTH_SHORT).show();
        }
    }

    // ── Gallery ───────────────────────────────────────────────────────

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

    // ── Permissions ───────────────────────────────────────────────────

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) return;
        if (req == REQ_CAM_PERM) launchCamera();
        else if (req == REQ_STORAGE) openGallery();
    }

    // ── Activity results ──────────────────────────────────────────────

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);

        Uri imageUri = null;
        if (req == REQ_GALLERY && res == RESULT_OK && data != null) {
            imageUri = data.getData();
        } else if (req == REQ_CAMERA && res == RESULT_OK) {
            imageUri = cameraImageUri;
        } else {
            replacingImagePath = null;
            cameraImageUri = null;
            return;
        }

        if (imageUri == null) { replacingImagePath = null; return; }

        final String oldPath  = replacingImagePath;
        replacingImagePath    = null;
        final Uri finalUri    = imageUri;

        new Thread(() -> {
            try {
                byte[] raw;
                if (req == REQ_CAMERA) {
                    // Read directly from file URI
                    InputStream is = getContentResolver().openInputStream(finalUri);
                    raw = readStream(is);
                } else {
                    InputStream is = getContentResolver().openInputStream(finalUri);
                    raw = readStream(is);
                }
                Bitmap bmp = BitmapFactory.decodeByteArray(raw, 0, raw.length);
                // Resize if too large (max 1920px on longer side)
                bmp = resizeIfNeeded(bmp, 1920);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 82, baos);
                byte[] compressed = baos.toByteArray();

                String saved = dbManager.saveEncryptedImage(compressed, currentTag.getTagId());
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

    // ── Image views ───────────────────────────────────────────────────

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

        iv.setOnClickListener(vv -> showReplaceImageDialog(path));
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

    // ── Save ──────────────────────────────────────────────────────────

    private void saveRecord() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String note  = etNote.getText()  != null ? etNote.getText().toString().trim()  : "";

        if (title.isEmpty()) {
            etTitle.setError("عنوان الزامی است");
            etTitle.requestFocus();
            return;
        }
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

    // ── Helpers ───────────────────────────────────────────────────────

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
