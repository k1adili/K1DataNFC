package com.k1datanfc;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Handles local, Dropbox and Google Drive backup/restore.
 *
 * IMPORTANT: the app's normal on-disk encryption uses a key stored in the
 * Android Keystore, which is tied to this specific app install on this
 * specific device and is NEVER exported. That's correct for local storage,
 * but it means a raw copy of those files is useless after a reinstall or on
 * another device — there is no key available to decrypt it with.
 *
 * For backups we instead re-encrypt everything with a key derived from a
 * user-chosen password (PBKDF2 + AES-256-GCM). As long as the user remembers
 * that password, the backup can be restored on any device, at any time,
 * even after the original Keystore key is gone.
 */
public class BackupManager {

    private static final String TAG = "BackupManager";
    private static final String ZIP_ENTRY_DB = "k1_tags.dat";
    private static final String ZIP_ENTRY_IMAGES_PREFIX = "images/";

    private final Context context;
    private final DatabaseManager dbManager;
    private final EncryptionManager encryptionManager;

    public interface BackupCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public BackupManager(Context context, DatabaseManager dbManager) {
        this.context = context.getApplicationContext();
        this.dbManager = dbManager;
        this.encryptionManager = K1Application.getInstance().getEncryptionManager();
    }

    // -------- Local Backup --------

    public void backupToLocalStorage(String password, BackupCallback callback) {
        new Thread(() -> {
            try {
                File backupDir = getLocalBackupDir();
                if (!backupDir.exists() && !backupDir.mkdirs()) {
                    callback.onError("امکان ایجاد پوشه بکاپ وجود ندارد:\n" + backupDir.getAbsolutePath());
                    return;
                }
                String filename = dbManager.generateBackupFilename();
                File zipFile = new File(backupDir, filename);
                createBackupZip(zipFile, password);
                callback.onSuccess("پشتیبان ذخیره شد:\n" + zipFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Local backup failed", e);
                callback.onError("خطا در پشتیبان‌گیری: " + e.getMessage());
            }
        }).start();
    }

    public void restoreFromLocalFile(File zipFile, String password, BackupCallback callback) {
        new Thread(() -> {
            try {
                int restoredTags = restoreFromZip(zipFile, password);
                if (restoredTags < 0) {
                    callback.onError("رمز عبور اشتباه است یا فایل بکاپ خراب است.");
                    return;
                }
                callback.onSuccess("بازیابی با موفقیت انجام شد (" + restoredTags + " تگ بازیابی شد)");
            } catch (Exception e) {
                Log.e(TAG, "Local restore failed", e);
                callback.onError("خطا در بازیابی: " + e.getMessage());
            }
        }).start();
    }

    private File getLocalBackupDir() {
        // Root of shared external storage (e.g. /storage/emulated/0/K1DataNFC_Backups)
        // so the folder is visible directly in the phone's main storage, not buried
        // under Android/data/<package>. Requires MANAGE_EXTERNAL_STORAGE on Android 11+.
        File root = Environment.getExternalStorageDirectory();
        return new File(root, "K1DataNFC_Backups");
    }

    public File getLocalBackupDirectory() {
        return getLocalBackupDir();
    }

    // -------- ZIP helpers --------

    /**
     * Builds a backup ZIP. Every entry is decrypted from on-disk storage
     * (using the device Keystore key) and then RE-encrypted with the
     * user's password before being written into the ZIP. This makes the
     * ZIP fully portable and independent of the device/install.
     */
    public void createBackupZip(File zipFile, String password) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {

            // Database: decrypt with device key, re-encrypt with password
            File dbFile = dbManager.getDatabaseFile();
            if (dbFile.exists()) {
                byte[] encryptedOnDisk = readFile(dbFile);
                byte[] plainJson = encryptionManager.decryptBytes(encryptedOnDisk);
                if (plainJson == null) {
                    throw new IOException("امکان خواندن دیتابیس داخلی وجود ندارد (کلید رمزنگاری نامعتبر است)");
                }
                byte[] portableEncrypted = encryptionManager.encryptWithPassword(plainJson, password);
                writeZipEntry(zos, ZIP_ENTRY_DB, portableEncrypted);
            }

            // Images: same treatment, one by one
            File imagesDir = dbManager.getImagesDir();
            if (imagesDir.exists()) {
                File[] imgs = imagesDir.listFiles();
                if (imgs != null) {
                    for (File img : imgs) {
                        byte[] encryptedOnDisk = readFile(img);
                        byte[] plainImage = encryptionManager.decryptBytes(encryptedOnDisk);
                        if (plainImage == null) {
                            Log.e(TAG, "Skipping unreadable image during backup: " + img.getName());
                            continue;
                        }
                        byte[] portableEncrypted = encryptionManager.encryptWithPassword(plainImage, password);
                        writeZipEntry(zos, ZIP_ENTRY_IMAGES_PREFIX + img.getName(), portableEncrypted);
                    }
                }
            }
        }
    }

    /**
     * Restores from a backup ZIP created by createBackupZip(). Each entry is
     * decrypted with the password, then re-encrypted with this device's
     * current Keystore key before being written to internal storage —
     * exactly mirroring how data is normally saved.
     *
     * Existing local data is cleared first so the result matches the backup
     * exactly (no leftover tags/images from before the restore).
     *
     * @return number of tag entries restored, or -1 if the password was wrong.
     */
    public int restoreFromZip(File zipFile, String password) throws IOException {
        // First pass: decrypt every entry into memory. If the password is
        // wrong, the very first entry will fail to decrypt and we bail out
        // WITHOUT touching any existing local data.
        byte[] decryptedDb = null;
        java.util.Map<String, byte[]> decryptedImages = new java.util.HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            boolean sawAnyEntry = false;
            while ((entry = zis.getNextEntry()) != null) {
                sawAnyEntry = true;
                String name = entry.getName();
                byte[] portableEncrypted = readAllBytes(zis);
                byte[] plain = encryptionManager.decryptWithPassword(portableEncrypted, password);
                if (plain == null) {
                    // Wrong password (or corrupted backup)
                    return -1;
                }
                if (name.equals(ZIP_ENTRY_DB)) {
                    decryptedDb = plain;
                } else if (name.startsWith(ZIP_ENTRY_IMAGES_PREFIX)) {
                    String imageName = name.substring(ZIP_ENTRY_IMAGES_PREFIX.length());
                    decryptedImages.put(imageName, plain);
                }
                zis.closeEntry();
            }
            if (!sawAnyEntry) {
                return -1; // empty/invalid zip
            }
        }

        // Second pass: now that we know the password is correct, clear
        // existing local data and write the restored data in its place.
        clearLocalData();

        int tagCount = 0;
        if (decryptedDb != null) {
            byte[] reEncrypted = encryptionManager.encryptBytes(decryptedDb);
            writeFile(dbManager.getDatabaseFile(), reEncrypted);
            // Count tags for the success message
            try {
                String json = new String(decryptedDb, java.nio.charset.StandardCharsets.UTF_8);
                tagCount = new org.json.JSONArray(json).length();
            } catch (Exception ignored) { }
        }

        File imagesDir = dbManager.getImagesDir();
        if (!imagesDir.exists()) imagesDir.mkdirs();
        for (java.util.Map.Entry<String, byte[]> imgEntry : decryptedImages.entrySet()) {
            byte[] reEncrypted = encryptionManager.encryptBytes(imgEntry.getValue());
            writeFile(new File(imagesDir, imgEntry.getKey()), reEncrypted);
        }

        return tagCount;
    }

    /**
     * Deletes the current database file and all images before a restore,
     * so old data can never mix with or block the restored data.
     */
    private void clearLocalData() {
        File dbFile = dbManager.getDatabaseFile();
        if (dbFile.exists()) dbFile.delete();

        File imagesDir = dbManager.getImagesDir();
        if (imagesDir.exists()) {
            File[] imgs = imagesDir.listFiles();
            if (imgs != null) {
                for (File img : imgs) img.delete();
            }
        }
    }

    // -------- low-level file/zip helpers --------

    private void writeZipEntry(ZipOutputStream zos, String entryName, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private byte[] readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return readAllBytes(fis);
        }
    }

    private void writeFile(File file, byte[] data) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }

    private byte[] readAllBytes(java.io.InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int len;
        while ((len = is.read(chunk)) != -1) buffer.write(chunk, 0, len);
        return buffer.toByteArray();
    }
}
