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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class RecordEditActivity extends AppCompatActivity {

    public static final String EXTRA_TAG_ID    = "tag_id";
    public static final String EXTRA_RECORD_ID = "record_id";

    private static final int REQ_IMAGE   = 101;
    private static final int REQ_STORAGE = 102;

    private DatabaseManager dbManager;
    private NfcTag          currentTag;
    private TagRecord       currentRecord;
    private boolean         isNew;

    private TextInputEditText etTitle, etNote;
    private LinearLayout      imagesContainer;
    private String            replacingImagePath = null;

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

        if (recordId != null) {
            currentRecord = currentTag.findRecord(recordId);
            isNew = false;
        }
        if (currentRecord == null) {
            currentRecord = new TagRecord();
            isNew = true;
        }

        setupViews();
        populateFields();
    }

    private void setupViews() {
        getSupportActionBar().setTitle(isNew ? "رکورد جدید" : "ویرایش رکورد");

        etTitle        = findViewById(R.id.et_record_title);
        etNote         = findViewById(R.id.et_record_note);
        imagesContainer= findViewById(R.id.images_container);

        Button btnAddImage = findViewById(R.id.btn_add_image);
        btnAddImage.setOnClickListener(v -> pickImage());

        ExtendedFloatingActionButton fabSave = findViewById(R.id.fab_save_record);
        fabSave.setOnClickListener(v -> saveRecord());
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

        // Tap to replace
        iv.setOnClickListener(vv -> { replacingImagePath = path; openImagePicker(); });

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
            // update in-place
            for (int i = 0; i < currentTag.getRecords().size(); i++) {
                if (currentTag.getRecords().get(i).getRecordId()
                        .equals(currentRecord.getRecordId())) {
                    currentTag.getRecords().set(i, currentRecord);
                    break;
                }
            }
        }
        dbManager.saveTag(currentTag);
        Toast.makeText(this, isNew ? "رکورد ذخیره شد" : "رکورد به‌روز شد", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    // ── Image picking ──────────────────────────────────────────────────

    private void pickImage() { replacingImagePath = null; openImagePicker(); }

    private void openImagePicker() {
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
        startActivityForResult(i, REQ_IMAGE);
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_STORAGE && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            openImagePicker();
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMAGE || res != RESULT_OK || data == null) {
            replacingImagePath = null; return;
        }
        Uri uri = data.getData();
        if (uri == null) { replacingImagePath = null; return; }

        final String oldPath = replacingImagePath;
        replacingImagePath = null;

        new Thread(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                byte[] raw = readStream(is);
                Bitmap bmp = BitmapFactory.decodeByteArray(raw, 0, raw.length);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
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
