package com.k1datanfc;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backup/restore manager.
 *
 * HOW IT WORKS
 * ─────────────
 * On-disk files are encrypted with a key that lives in Android Keystore and
 * never leaves the device. That key is lost if the app is reinstalled or the
 * backup is opened on a different phone.
 *
 * For portable backups we:
 *   1. Decrypt each file with the local Keystore key (plain bytes in RAM).
 *   2. Re-encrypt those plain bytes with a user password (PBKDF2+AES-GCM).
 *   3. Write the password-encrypted bytes into the ZIP.
 *
 * Restore reverses this: password-decrypt from ZIP → re-encrypt with the NEW
 * device's Keystore key → write to internal storage.
 *
 * Image paths in the JSON database are ABSOLUTE paths tied to the original
 * device. After restore we rewrite them to match the new device's path.
 */
public class BackupManager {

    private static final String TAG = "BackupManager";

    // ZIP entry names
    private static final String ENTRY_DB     = "db/k1_tags.dat";
    private static final String ENTRY_IMG_PFX = "images/";

    private final Context        context;
    private final DatabaseManager dbManager;
    private final EncryptionManager enc;

    public interface BackupCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public BackupManager(Context context, DatabaseManager dbManager) {
        this.context   = context.getApplicationContext();
        this.dbManager = dbManager;
        this.enc       = K1Application.getInstance().getEncryptionManager();
    }

    // ─────────────────────────────── LOCAL BACKUP ────────────────────────────

    public void backupToLocalStorage(String password, BackupCallback cb) {
        new Thread(() -> {
            try {
                File dir = getBackupDir();
                if (!dir.exists() && !dir.mkdirs()) {
                    cb.onError("نمی‌توان پوشه بکاپ را ایجاد کرد:\n" + dir.getAbsolutePath());
                    return;
                }
                File zip = new File(dir, dbManager.generateBackupFilename());
                createBackupZip(zip, password);
                cb.onSuccess("بکاپ ذخیره شد:\n" + zip.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "backup failed", e);
                cb.onError("خطا در بکاپ: " + e.getMessage());
            }
        }).start();
    }

    public void restoreFromLocalFile(File zipFile, String password, BackupCallback cb) {
        new Thread(() -> {
            try {
                int n = restoreFromZip(zipFile, password);
                if (n < 0) {
                    cb.onError("رمز عبور اشتباه است یا فایل بکاپ خراب است.");
                } else {
                    cb.onSuccess("بازیابی انجام شد — " + n + " تگ بازگردانده شد.");
                }
            } catch (Exception e) {
                Log.e(TAG, "restore failed", e);
                cb.onError("خطا در بازیابی: " + e.getMessage());
            }
        }).start();
    }

    private File getBackupDir() {
        return new File(Environment.getExternalStorageDirectory(), "K1DataNFC_Backups");
    }

    public File getLocalBackupDirectory() { return getBackupDir(); }

    // ─────────────────────────────── CREATE ZIP ───────────────────────────────

    /**
     * Builds a portable backup ZIP.
     *
     * ZIP layout:
     *   db/k1_tags.dat          ← password-encrypted JSON database
     *   images/<filename>.enc   ← password-encrypted image (one per image)
     *
     * Each file is independently decrypted from disk (Keystore key) and
     * then re-encrypted with the user password before entering the ZIP.
     */
    public void createBackupZip(File zipFile, String password) throws Exception {
        File dbFile    = dbManager.getDatabaseFile();
        File imagesDir = dbManager.getImagesDir();

        Log.d(TAG, "createBackupZip → db=" + dbFile.getAbsolutePath()
                + " exists=" + dbFile.exists());
        Log.d(TAG, "createBackupZip → imagesDir=" + imagesDir.getAbsolutePath()
                + " exists=" + imagesDir.exists());

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {

            // ── 1. Database ──────────────────────────────────────────────────
            if (!dbFile.exists()) {
                throw new IOException("دیتابیس پیدا نشد — هیچ داده‌ای برای بکاپ وجود ندارد.");
            }
            byte[] rawDb    = readFile(dbFile);
            byte[] plainDb  = enc.decryptBytes(rawDb);
            if (plainDb == null) throw new IOException("رمزگشایی دیتابیس شکست خورد.");
            putEntry(zos, ENTRY_DB, enc.encryptWithPassword(plainDb, password));
            Log.d(TAG, "DB entry written (" + plainDb.length + " bytes plain)");

            // ── 2. Images ────────────────────────────────────────────────────
            if (imagesDir.exists()) {
                File[] files = imagesDir.listFiles();
                int imgCount = 0;
                if (files != null) {
                    for (File img : files) {
                        if (!img.isFile()) continue;
                        byte[] rawImg   = readFile(img);
                        byte[] plainImg = enc.decryptBytes(rawImg);
                        if (plainImg == null) {
                            Log.w(TAG, "skipping unreadable image: " + img.getName());
                            continue;
                        }
                        putEntry(zos, ENTRY_IMG_PFX + img.getName(),
                                 enc.encryptWithPassword(plainImg, password));
                        imgCount++;
                        Log.d(TAG, "image entry written: " + img.getName());
                    }
                }
                Log.d(TAG, imgCount + " image(s) added to ZIP");
            } else {
                Log.d(TAG, "imagesDir does not exist, no images to back up");
            }
        }
    }

    // ─────────────────────────────── RESTORE ZIP ──────────────────────────────

    /**
     * Restores from a ZIP produced by createBackupZip().
     *
     * Steps:
     *   1. Password-decrypt EVERY entry into RAM (first pass).
     *      If even one entry fails → return -1 (wrong password / corrupt).
     *   2. Delete all current local data.
     *   3. Write images (re-encrypt with Keystore key).
     *   4. Rewrite absolute image paths in the JSON so they match THIS device.
     *   5. Write the database (re-encrypt with Keystore key).
     *
     * Returns the number of tags restored, or -1 on password failure.
     */
    public int restoreFromZip(File zipFile, String password) throws Exception {

        // ── Pass 1: decrypt everything into memory ───────────────────────────
        byte[]              plainDb     = null;
        Map<String, byte[]> plainImages = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            int entriesRead = 0;
            while ((entry = zis.getNextEntry()) != null) {
                entriesRead++;
                String name      = entry.getName();
                byte[] encrypted = readAllBytes(zis);
                byte[] plain     = enc.decryptWithPassword(encrypted, password);
                if (plain == null) {
                    Log.e(TAG, "decryptWithPassword returned null for entry: " + name);
                    return -1;   // wrong password or corrupt data
                }
                if (name.equals(ENTRY_DB)) {
                    plainDb = plain;
                    Log.d(TAG, "DB entry decrypted (" + plain.length + " bytes)");
                } else if (name.startsWith(ENTRY_IMG_PFX)) {
                    String filename = name.substring(ENTRY_IMG_PFX.length());
                    if (!filename.isEmpty()) {
                        plainImages.put(filename, plain);
                        Log.d(TAG, "image entry decrypted: " + filename);
                    }
                }
                zis.closeEntry();
            }
            if (entriesRead == 0) return -1;   // empty ZIP
        }

        Log.d(TAG, "Restore pass 1 done — db=" + (plainDb != null)
                + " images=" + plainImages.size());

        // ── Pass 2: clear old data, write new data ───────────────────────────
        clearLocalData();

        // Write images first so we know the final paths before touching the DB
        File imagesDir = dbManager.getImagesDir();
        if (!imagesDir.exists()) imagesDir.mkdirs();

        for (Map.Entry<String, byte[]> e : plainImages.entrySet()) {
            File dest = new File(imagesDir, e.getKey());
            writeFile(dest, enc.encryptBytes(e.getValue()));
            Log.d(TAG, "image written: " + dest.getAbsolutePath());
        }

        // Fix absolute paths in the JSON and write the database
        int tagCount = 0;
        if (plainDb != null) {
            byte[] fixedJson = fixImagePaths(plainDb, imagesDir.getAbsolutePath());
            writeFile(dbManager.getDatabaseFile(), enc.encryptBytes(fixedJson));
            try {
                tagCount = new JSONArray(new String(fixedJson, StandardCharsets.UTF_8)).length();
            } catch (Exception ignore) {}
            Log.d(TAG, "DB written with " + tagCount + " tag(s)");
        }

        return tagCount;
    }

    /**
     * Rewrites every imagePath in the JSON so the filename part stays the same
     * but the directory prefix is replaced with newImagesDirPath.
     *
     * Example:
     *   old: /data/user/0/com.k1datanfc/files/images/AABB_123.enc
     *   new: /data/user/10/com.k1datanfc/files/images/AABB_123.enc
     */
    private byte[] fixImagePaths(byte[] jsonBytes, String newImagesDirPath) {
        try {
            String   json = new String(jsonBytes, StandardCharsets.UTF_8);
            JSONArray arr  = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject tag   = arr.getJSONObject(i);
                JSONArray  paths = tag.optJSONArray("imagePaths");
                if (paths == null) continue;
                JSONArray fixed = new JSONArray();
                for (int j = 0; j < paths.length(); j++) {
                    String oldPath  = paths.getString(j);
                    String filename = new File(oldPath).getName();
                    fixed.put(new File(newImagesDirPath, filename).getAbsolutePath());
                }
                tag.put("imagePaths", fixed);
            }
            return arr.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "fixImagePaths failed — returning original JSON", e);
            return jsonBytes;
        }
    }

    // ─────────────────────────────── HELPERS ─────────────────────────────────

    private void clearLocalData() {
        File db = dbManager.getDatabaseFile();
        if (db.exists()) db.delete();

        File imgDir = dbManager.getImagesDir();
        if (imgDir.exists()) {
            File[] fs = imgDir.listFiles();
            if (fs != null) for (File f : fs) f.delete();
        }
    }

    private void putEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private byte[] readFile(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            return readAllBytes(fis);
        }
    }

    private void writeFile(File f, byte[] data) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(data);
        }
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int len;
        while ((len = is.read(chunk)) != -1) buf.write(chunk, 0, len);
        return buf.toByteArray();
    }
}
