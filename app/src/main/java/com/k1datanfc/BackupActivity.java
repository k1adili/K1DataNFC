package com.k1datanfc;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class BackupActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE = 200;
    private static final int REQUEST_PICK_BACKUP = 201;
    private static final int REQUEST_MANAGE_STORAGE = 202;

    private BackupManager backupManager;
    private ProgressBar progressBar;
    private TextView tvStatus;

    // Holds the URI the user picked, while we wait for them to type the password
    private Uri pendingRestoreUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.backup);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        backupManager = new BackupManager(this, K1Application.getInstance().getDatabaseManager());
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);

        Button btnBackupLocal = findViewById(R.id.btn_backup_local);
        btnBackupLocal.setOnClickListener(v -> {
            if (!hasStoragePermission()) {
                requestStoragePermission();
                return;
            }
            promptForBackupPassword();
        });

        Button btnRestoreLocal = findViewById(R.id.btn_restore_local);
        btnRestoreLocal.setOnClickListener(v -> pickBackupFile());

        Button btnBackupDrive = findViewById(R.id.btn_backup_drive);
        btnBackupDrive.setOnClickListener(v -> showDriveInfo());

        Button btnBackupDropbox = findViewById(R.id.btn_backup_dropbox);
        btnBackupDropbox.setOnClickListener(v -> showDropboxInfo());
    }

    // ---------- Backup flow ----------

    private void promptForBackupPassword() {
        LinearLayout container = passwordDialogLayout();
        EditText etPass = (EditText) container.getChildAt(0);
        EditText etPassConfirm = (EditText) container.getChildAt(1);
        etPassConfirm.setHint("تکرار رمز عبور");

        new AlertDialog.Builder(this)
                .setTitle("رمز عبور بکاپ")
                .setMessage("یک رمز عبور برای این بکاپ انتخاب کنید. این رمز برای بازیابی در آینده (حتی روی گوشی دیگر) لازم است. آن را فراموش نکنید.")
                .setView(container)
                .setPositiveButton("ذخیره بکاپ", (d, w) -> {
                    String pass = etPass.getText().toString();
                    String confirm = etPassConfirm.getText().toString();
                    if (pass.isEmpty()) {
                        Toast.makeText(this, "رمز عبور نمی‌تواند خالی باشد", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!pass.equals(confirm)) {
                        Toast.makeText(this, R.string.pin_mismatch, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    doLocalBackup(pass);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void doLocalBackup(String password) {
        showLoading(true);
        backupManager.backupToLocalStorage(password, new BackupManager.BackupCallback() {
            @Override
            public void onSuccess(String message) {
                runOnUiThread(() -> {
                    showLoading(false);
                    tvStatus.setText(message);
                    Toast.makeText(BackupActivity.this, R.string.backup_success, Toast.LENGTH_SHORT).show();
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showLoading(false);
                    tvStatus.setText(error);
                    Toast.makeText(BackupActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ---------- Permission handling (root storage access) ----------

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return android.os.Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            new AlertDialog.Builder(this)
                    .setTitle("دسترسی به حافظه")
                    .setMessage("برای ذخیره بکاپ در پوشه اصلی حافظه گوشی، لازم است دسترسی \"All files access\" را فعال کنید.")
                    .setPositiveButton("رفتن به تنظیمات", (d, w) -> {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                        } catch (Exception e) {
                            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }

    // ---------- Restore flow ----------

    private void pickBackupFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "فایل بکاپ را انتخاب کنید"), REQUEST_PICK_BACKUP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (hasStoragePermission()) {
                promptForBackupPassword();
            } else {
                Toast.makeText(this, "دسترسی به حافظه داده نشد", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (requestCode == REQUEST_PICK_BACKUP && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            pendingRestoreUri = uri;
            promptForRestorePassword();
        }
    }

    private void promptForRestorePassword() {
        LinearLayout container = passwordDialogLayout();
        EditText etPass = (EditText) container.getChildAt(0);
        container.removeViewAt(1); // only need one field to restore

        new AlertDialog.Builder(this)
                .setTitle("بازیابی بکاپ")
                .setMessage("رمز عبوری که هنگام ساخت این بکاپ وارد کردید را وارد کنید.\n\n⚠️ تمام اطلاعات فعلی برنامه با محتوای این بکاپ جایگزین خواهد شد.")
                .setView(container)
                .setPositiveButton("بازیابی", (d, w) -> {
                    String pass = etPass.getText().toString();
                    if (pass.isEmpty()) {
                        Toast.makeText(this, "رمز عبور را وارد کنید", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    restoreFromUri(pendingRestoreUri, pass);
                })
                .setNegativeButton(R.string.cancel, (d, w) -> pendingRestoreUri = null)
                .show();
    }

    private void restoreFromUri(Uri uri, String password) {
        showLoading(true);
        new Thread(() -> {
            try {
                File tempFile = new File(getCacheDir(), "restore_temp.zip");
                InputStream is = getContentResolver().openInputStream(uri);
                FileOutputStream fos = new FileOutputStream(tempFile);
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
                fos.close(); is.close();

                backupManager.restoreFromLocalFile(tempFile, password, new BackupManager.BackupCallback() {
                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            showLoading(false);
                            tvStatus.setText(message);
                            Toast.makeText(BackupActivity.this, R.string.restore_success, Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            showLoading(false);
                            tvStatus.setText(error);
                            Toast.makeText(BackupActivity.this, error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, "خطا: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ---------- Cloud info dialogs ----------

    private void showDriveInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Google Drive")
                .setMessage("برای استفاده از Google Drive:\n\n" +
                        "۱. ابتدا پشتیبان را روی حافظه گوشی ذخیره کنید\n" +
                        "۲. اپ Google Drive را باز کنید\n" +
                        "۳. فایل ZIP را از پوشه K1DataNFC_Backups آپلود کنید\n\n" +
                        "یا از دکمه زیر برای باز کردن Drive استفاده کنید:")
                .setPositiveButton("باز کردن Drive", (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com")));
                    } catch (Exception e) { /* ignore */ }
                })
                .setNeutralButton("ذخیره روی گوشی اول", (d, w) -> {
                    if (!hasStoragePermission()) requestStoragePermission();
                    else promptForBackupPassword();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDropboxInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Dropbox")
                .setMessage("برای استفاده از Dropbox:\n\n" +
                        "۱. ابتدا پشتیبان را روی حافظه گوشی ذخیره کنید\n" +
                        "۲. اپ Dropbox را باز کنید\n" +
                        "۳. فایل ZIP را از پوشه K1DataNFC_Backups آپلود کنید\n\n" +
                        "یا از دکمه زیر برای باز کردن Dropbox استفاده کنید:")
                .setPositiveButton("باز کردن Dropbox", (d, w) -> {
                    try {
                        startActivity(getPackageManager().getLaunchIntentForPackage("com.dropbox.android"));
                    } catch (Exception e) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://dropbox.com")));
                    }
                })
                .setNeutralButton("ذخیره روی گوشی اول", (d, w) -> {
                    if (!hasStoragePermission()) requestStoragePermission();
                    else promptForBackupPassword();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------- Small UI helpers ----------

    private LinearLayout passwordDialogLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        EditText etPass = new EditText(this);
        etPass.setHint("رمز عبور");
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etPass);

        EditText etPass2 = new EditText(this);
        etPass2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etPass2);

        return layout;
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            promptForBackupPassword();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
