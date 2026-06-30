package com.k1datanfc;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Stores all NFC tag data as AES-256-GCM encrypted JSON on internal storage.
 * The file is stored in app-private directory — inaccessible to other apps.
 */
public class DatabaseManager {

    private static final String TAG = "DatabaseManager";
    private static final String DB_FILE = "k1_tags.enc";
    private static final String IMAGES_DIR = "images";

    private final Context context;
    private final EncryptionManager encryption;

    public DatabaseManager(Context context, EncryptionManager encryption) {
        this.context = context.getApplicationContext();
        this.encryption = encryption;
        // Ensure images directory exists
        File imgDir = new File(context.getFilesDir(), IMAGES_DIR);
        if (!imgDir.exists()) imgDir.mkdirs();
    }

    // ---------- CRUD ----------

    /** True if the last loadAllTags() call failed to decrypt an existing, non-empty database file. */
    private boolean lastLoadFailed = false;

    public boolean didLastLoadFail() {
        return lastLoadFailed;
    }

    public List<NfcTag> loadAllTags() {
        lastLoadFailed = false;
        List<NfcTag> tags = new ArrayList<>();
        File dbFile = new File(context.getFilesDir(), DB_FILE);
        if (!dbFile.exists()) return tags;
        try {
            FileInputStream fis = new FileInputStream(dbFile);
            byte[] encData = new byte[(int) dbFile.length()];
            fis.read(encData);
            fis.close();
            if (encData.length == 0) return tags;
            byte[] jsonBytes = encryption.decryptBytes(encData);
            if (jsonBytes == null) {
                // Decryption failed — almost always means the encryption key changed
                // (app reinstalled / data cleared / restored on a different device)
                // and the backup file can no longer be read with the current key.
                Log.e(TAG, "Decryption returned null - encryption key mismatch or corrupted file");
                lastLoadFailed = true;
                return tags;
            }
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                tags.add(tagFromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading tags", e);
            lastLoadFailed = true;
        }
        return tags;
    }

    public void saveAllTags(List<NfcTag> tags) {
        try {
            JSONArray arr = new JSONArray();
            for (NfcTag tag : tags) arr.put(tagToJson(tag));
            byte[] jsonBytes = arr.toString().getBytes(StandardCharsets.UTF_8);
            byte[] encData = encryption.encryptBytes(jsonBytes);
            File dbFile = new File(context.getFilesDir(), DB_FILE);
            FileOutputStream fos = new FileOutputStream(dbFile);
            fos.write(encData);
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "Error saving tags", e);
        }
    }

    public NfcTag findTagById(String tagId) {
        for (NfcTag tag : loadAllTags()) {
            if (tag.getTagId().equals(tagId)) return tag;
        }
        return null;
    }

    public void saveTag(NfcTag newTag) {
        List<NfcTag> tags = loadAllTags();
        boolean found = false;
        for (int i = 0; i < tags.size(); i++) {
            if (tags.get(i).getTagId().equals(newTag.getTagId())) {
                newTag.touchUpdated();
                tags.set(i, newTag);
                found = true;
                break;
            }
        }
        if (!found) tags.add(newTag);
        saveAllTags(tags);
    }

    public void deleteTag(String tagId) {
        List<NfcTag> tags = loadAllTags();
        NfcTag toDelete = null;
        for (NfcTag tag : tags) {
            if (tag.getTagId().equals(tagId)) { toDelete = tag; break; }
        }
        if (toDelete != null) {
            // Delete encrypted image files
            if (toDelete.getImagePaths() != null) {
                for (String path : toDelete.getImagePaths()) {
                    new File(path).delete();
                }
            }
            tags.remove(toDelete);
            saveAllTags(tags);
        }
    }

    // ---------- Image storage ----------

    public String saveEncryptedImage(byte[] imageBytes, String tagId) {
        try {
            String filename = tagId.replace(":", "") + "_" + System.currentTimeMillis() + ".enc";
            File imgDir = new File(context.getFilesDir(), IMAGES_DIR);
            File imgFile = new File(imgDir, filename);
            byte[] encData = encryption.encryptBytes(imageBytes);
            FileOutputStream fos = new FileOutputStream(imgFile);
            fos.write(encData);
            fos.close();
            return imgFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error saving image", e);
            return null;
        }
    }

    public byte[] loadDecryptedImage(String path) {
        try {
            File imgFile = new File(path);
            if (!imgFile.exists()) return null;
            FileInputStream fis = new FileInputStream(imgFile);
            byte[] encData = new byte[(int) imgFile.length()];
            fis.read(encData);
            fis.close();
            return encryption.decryptBytes(encData);
        } catch (Exception e) {
            Log.e(TAG, "Error loading image", e);
            return null;
        }
    }

    public void deleteImageFile(String path) {
        new File(path).delete();
    }

    // ---------- Backup / Restore ----------

    /**
     * Returns the raw encrypted database file for backup.
     */
    public File getDatabaseFile() {
        return new File(context.getFilesDir(), DB_FILE);
    }

    public File getImagesDir() {
        return new File(context.getFilesDir(), IMAGES_DIR);
    }

    public String generateBackupFilename() {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return "k1nfc_backup_" + ts + ".zip";
    }

    // ---------- JSON helpers ----------

    private JSONObject tagToJson(NfcTag tag) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("tagId", tag.getTagId());
        obj.put("name", tag.getName() != null ? tag.getName() : "");
        obj.put("note", tag.getNote() != null ? tag.getNote() : "");
        obj.put("createdAt", tag.getCreatedAt());
        obj.put("updatedAt", tag.getUpdatedAt());
        obj.put("lastScannedAt", tag.getLastScannedAt());
        JSONArray imgs = new JSONArray();
        if (tag.getImagePaths() != null) {
            for (String p : tag.getImagePaths()) imgs.put(p);
        }
        obj.put("imagePaths", imgs);
        return obj;
    }

    private NfcTag tagFromJson(JSONObject obj) throws Exception {
        NfcTag tag = new NfcTag();
        tag.setTagId(obj.getString("tagId"));
        tag.setName(obj.optString("name", ""));
        tag.setNote(obj.optString("note", ""));
        tag.setCreatedAt(obj.optLong("createdAt", System.currentTimeMillis()));
        tag.setUpdatedAt(obj.optLong("updatedAt", System.currentTimeMillis()));
        tag.setLastScannedAt(obj.optLong("lastScannedAt", System.currentTimeMillis()));
        JSONArray imgs = obj.optJSONArray("imagePaths");
        List<String> imagePaths = new ArrayList<>();
        if (imgs != null) {
            for (int i = 0; i < imgs.length(); i++) imagePaths.add(imgs.getString(i));
        }
        tag.setImagePaths(imagePaths);
        return tag;
    }
}
