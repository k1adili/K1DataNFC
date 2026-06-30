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
import android.widget.EditText;
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

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class TagDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TAG_ID = "tag_id";
    public static final String EXTRA_IS_NEW = "is_new";
    private static final int REQUEST_PICK_IMAGE = 101;
    private static final int REQUEST_STORAGE_PERMISSION = 102;

    private DatabaseManager dbManager;
    private NfcTag currentTag;
    private boolean isNew;
    private boolean isEditing;

    private EditText etName, etNote;
    private TextView tvTagId;
    private LinearLayout imagesContainer;
    private ExtendedFloatingActionButton fabEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_detail);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        dbManager = K1Application.getInstance().getDatabaseManager();

        String tagId = getIntent().getStringExtra(EXTRA_TAG_ID);
        isNew = getIntent().getBooleanExtra(EXTRA_IS_NEW, false);

        setupViews();

        if (isNew) {
            currentTag = new NfcTag(tagId);
            enterEditMode();
            getSupportActionBar().setTitle("تگ جدید");
        } else {
            currentTag = dbManager.findTagById(tagId);
            if (currentTag == null) { finish(); return; }
            getSupportActionBar().setTitle(currentTag.getName() != null && !currentTag.getName().isEmpty()
                    ? currentTag.getName() : "جزئیات تگ");
            populateFields();
            enterViewMode();
        }
    }

    private void setupViews() {
        etName = findViewById(R.id.et_name);
        etNote = findViewById(R.id.et_note);
        tvTagId = findViewById(R.id.tv_tag_id);
        imagesContainer = findViewById(R.id.images_container);
        fabEdit = findViewById(R.id.fab_edit);

        fabEdit.setOnClickListener(v -> {
            if (isEditing) saveTag();
            else enterEditMode();
        });

        Button btnAddImage = findViewById(R.id.btn_add_image);
        btnAddImage.setOnClickListener(v -> pickImage());
    }

    private void enterEditMode() {
        isEditing = true;
        etName.setEnabled(true);
        etNote.setEnabled(true);
        etName.requestFocus();
        fabEdit.setText("ذخیره");
        fabEdit.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_menu_save));
        findViewById(R.id.btn_add_image).setVisibility(View.VISIBLE);
    }

    private void enterViewMode() {
        isEditing = false;
        etName.setEnabled(false);
        etNote.setEnabled(false);
        fabEdit.setText("ویرایش");
        fabEdit.setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_menu_edit));
        findViewById(R.id.btn_add_image).setVisibility(View.GONE);
    }

    private void populateFields() {
        tvTagId.setText("ID: " + currentTag.getTagId());
        etName.setText(currentTag.getName());
        etNote.setText(currentTag.getNote());
        loadImages();
    }

    private void loadImages() {
        imagesContainer.removeAllViews();
        if (currentTag.getImagePaths() == null) return;
        for (String path : currentTag.getImagePaths()) {
            addImageView(path);
        }
    }

    private void addImageView(String path) {
        View imgView = getLayoutInflater().inflate(R.layout.item_image, imagesContainer, false);
        android.widget.ImageView iv = imgView.findViewById(R.id.image_view);
        ImageButton btnDelete = imgView.findViewById(R.id.btn_delete_image);

        new Thread(() -> {
            byte[] imgBytes = dbManager.loadDecryptedImage(path);
            if (imgBytes != null) {
                Bitmap bmp = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
                runOnUiThread(() -> iv.setImageBitmap(bmp));
            }
        }).start();

        btnDelete.setOnClickListener(v -> {
            if (!isEditing) return;
            dbManager.deleteImageFile(path);
            currentTag.removeImagePath(path);
            imagesContainer.removeView(imgView);
        });
        btnDelete.setVisibility(isEditing ? View.VISIBLE : View.GONE);
        imagesContainer.addView(imgView);
    }

    private void saveTag() {
        String name = etName.getText().toString().trim();
        String note = etNote.getText().toString().trim();
        currentTag.setName(name);
        currentTag.setNote(note);
        dbManager.saveTag(currentTag);
        Toast.makeText(this, isNew ? R.string.tag_saved : R.string.tag_updated, Toast.LENGTH_SHORT).show();
        getSupportActionBar().setTitle(name.isEmpty() ? "جزئیات تگ" : name);
        isNew = false;
        enterViewMode();
    }

    private void pickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            openImagePicker();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_STORAGE_PERMISSION);
            } else {
                openImagePicker();
            }
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri == null) return;
            new Thread(() -> {
                try {
                    InputStream is = getContentResolver().openInputStream(imageUri);
                    byte[] imgBytes = readStream(is);
                    // Compress
                    Bitmap bmp = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    byte[] compressed = baos.toByteArray();
                    String savedPath = dbManager.saveEncryptedImage(compressed, currentTag.getTagId());
                    if (savedPath != null) {
                        currentTag.addImagePath(savedPath);
                        runOnUiThread(() -> addImageView(savedPath));
                    }
                } catch (IOException e) {
                    runOnUiThread(() -> Toast.makeText(this, "خطا در بارگذاری تصویر", Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }

    private byte[] readStream(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int len;
        while ((len = is.read(chunk)) != -1) buffer.write(chunk, 0, len);
        return buffer.toByteArray();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (isEditing && !isNew) {
                new AlertDialog.Builder(this)
                        .setMessage("تغییرات ذخیره نشده‌اند. خارج می‌شوید؟")
                        .setPositiveButton("بله", (d, w) -> finish())
                        .setNegativeButton("خیر", null).show();
            } else {
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
