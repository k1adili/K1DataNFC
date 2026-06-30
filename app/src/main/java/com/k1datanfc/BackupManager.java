package com.k1datanfc;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Handles local, Dropbox and Google Drive backup/restore.
 * All data remains encrypted during backup — the ZIP contains encrypted files.
 */
public class BackupManager {

    private static final String TAG = "BackupManager";
    private final Context context;
    private final DatabaseManager dbManager;

    public interface BackupCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public BackupManager(Context context, DatabaseManager dbManager) {
        this.context = context.getApplicationContext();
        this.dbManager = dbManager;
    }

    // -------- Local Backup --------

    public void backupToLocalStorage(BackupCallback callback) {
        new Thread(() -> {
            try {
                File backupDir = getLocalBackupDir();
                if (!backupDir.exists()) backupDir.mkdirs();
                String filename = dbManager.generateBackupFilename();
                File zipFile = new File(backupDir, filename);
                createBackupZip(zipFile);
                callback.onSuccess("پشتیبان ذخیره شد:\n" + zipFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Local backup failed", e);
                callback.onError("خطا در پشتیبان‌گیری: " + e.getMessage());
            }
        }).start();
    }

    public void restoreFromLocalFile(File zipFile, BackupCallback callback) {
        new Thread(() -> {
            try {
                restoreFromZip(zipFile);
                callback.onSuccess("بازیابی با موفقیت انجام شد");
            } catch (Exception e) {
                Log.e(TAG, "Local restore failed", e);
                callback.onError("خطا در بازیابی: " + e.getMessage());
            }
        }).start();
    }

    private File getLocalBackupDir() {
        // Use the root of shared external storage (e.g. /storage/emulated/0/K1DataNFC_Backups)
        // so the folder is visible directly in the phone's main storage, not buried under
        // Android/data/<package>. Requires MANAGE_EXTERNAL_STORAGE on Android 11+ or
        // WRITE_EXTERNAL_STORAGE on Android 10-.
        File root = Environment.getExternalStorageDirectory();
        return new File(root, "K1DataNFC_Backups");
    }

    public File getLocalBackupDirectory() {
        return getLocalBackupDir();
    }

    // -------- ZIP helpers --------

    public void createBackupZip(File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // Add database file
            File dbFile = dbManager.getDatabaseFile();
            if (dbFile.exists()) {
                addFileToZip(zos, dbFile, "k1_tags.enc");
            }
            // Add all encrypted images
            File imagesDir = dbManager.getImagesDir();
            if (imagesDir.exists()) {
                File[] imgs = imagesDir.listFiles();
                if (imgs != null) {
                    for (File img : imgs) {
                        addFileToZip(zos, img, "images/" + img.getName());
                    }
                }
            }
        }
    }

    public void restoreFromZip(File zipFile) throws IOException {
        File filesDir = context.getFilesDir();
        File imagesDir = new File(filesDir, "images");
        if (!imagesDir.exists()) imagesDir.mkdirs();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                File outFile;
                if (name.startsWith("images/")) {
                    outFile = new File(imagesDir, name.substring("images/".length()));
                } else {
                    outFile = new File(filesDir, name);
                }
                outFile.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = zis.read(buf)) != -1) fos.write(buf, 0, len);
                }
                zis.closeEntry();
            }
        }
    }

    private void addFileToZip(ZipOutputStream zos, File file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) != -1) zos.write(buf, 0, len);
        }
        zos.closeEntry();
    }
}
