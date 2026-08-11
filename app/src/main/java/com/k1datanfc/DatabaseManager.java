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

public class DatabaseManager {

    private static final String TAG       = "DatabaseManager";
    private static final String DB_FILE   = "k1_tags.enc";
    private static final String IMAGES_DIR= "images";

    private final Context           context;
    private final EncryptionManager encryption;
    private boolean lastLoadFailed = false;

    public DatabaseManager(Context context, EncryptionManager encryption) {
        this.context    = context.getApplicationContext();
        this.encryption = encryption;
        new File(context.getFilesDir(), IMAGES_DIR).mkdirs();
    }

    public boolean didLastLoadFail() { return lastLoadFailed; }

    // ─────────────────────────── CRUD ────────────────────────────────

    public List<NfcTag> loadAllTags() {
        lastLoadFailed = false;
        List<NfcTag> tags = new ArrayList<>();
        File f = getDatabaseFile();
        if (!f.exists()) return tags;
        try {
            byte[] enc  = readFile(f);
            byte[] json = encryption.decryptBytes(enc);
            if (json == null) { lastLoadFailed = true; return tags; }
            JSONArray arr = new JSONArray(new String(json, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++)
                tags.add(tagFromJson(arr.getJSONObject(i)));
        } catch (Exception e) {
            Log.e(TAG, "loadAllTags", e);
            lastLoadFailed = true;
        }
        return tags;
    }

    public void saveAllTags(List<NfcTag> tags) {
        try {
            JSONArray arr = new JSONArray();
            for (NfcTag t : tags) arr.put(tagToJson(t));
            byte[] enc = encryption.encryptBytes(
                    arr.toString().getBytes(StandardCharsets.UTF_8));
            writeFile(getDatabaseFile(), enc);
        } catch (Exception e) { Log.e(TAG, "saveAllTags", e); }
    }

    /** Find a group by ANY of its associated tag IDs */
    public NfcTag findTagById(String tagId) {
        for (NfcTag t : loadAllTags())
            if (t.hasTagId(tagId)) return t;
        return null;
    }

    /** Find a group by its stable groupId */
    public NfcTag findTagByGroupId(String groupId) {
        for (NfcTag t : loadAllTags())
            if (groupId.equals(t.getGroupId())) return t;
        return null;
    }

    public void saveTag(NfcTag tag) {
        List<NfcTag> tags = loadAllTags();
        boolean found = false;
        for (int i = 0; i < tags.size(); i++) {
            if (tags.get(i).getGroupId().equals(tag.getGroupId())) {
                tags.set(i, tag); found = true; break;
            }
        }
        if (!found) tags.add(tag);
        saveAllTags(tags);
    }

    public void deleteTag(String groupId) {
        List<NfcTag> tags = loadAllTags();
        for (NfcTag t : tags) {
            if (groupId.equals(t.getGroupId())) {
                for (String p : t.getAllImagePaths()) new File(p).delete();
                tags.remove(t);
                break;
            }
        }
        saveAllTags(tags);
    }

    // ─────────────────────────── Images ──────────────────────────────

    public String saveEncryptedImage(byte[] imageBytes, String tagId) {
        try {
            String fn = tagId.replace(":", "").replace("/", "").replace("-", "")
                    + "_" + System.currentTimeMillis() + ".enc";
            File f = new File(getImagesDir(), fn);
            writeFile(f, encryption.encryptBytes(imageBytes));
            return f.getAbsolutePath();
        } catch (Exception e) { Log.e(TAG, "saveImage", e); return null; }
    }

    public byte[] loadDecryptedImage(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return null;
            return encryption.decryptBytes(readFile(f));
        } catch (Exception e) { Log.e(TAG, "loadImage", e); return null; }
    }

    public void deleteImageFile(String path) { new File(path).delete(); }

    // ─────────────────────────── Paths ───────────────────────────────

    public File getDatabaseFile() { return new File(context.getFilesDir(), DB_FILE); }
    public File getImagesDir()    { return new File(context.getFilesDir(), IMAGES_DIR); }

    public String generateBackupFilename() {
        return "k1nfc_backup_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                + ".zip";
    }

    // ─────────────────────────── JSON ────────────────────────────────

    private JSONObject tagToJson(NfcTag tag) throws Exception {
        JSONObject o = new JSONObject();
        o.put("groupId",     tag.getGroupId());
        o.put("name",        tag.getName() != null ? tag.getName() : "");
        o.put("createdAt",   tag.getCreatedAt());
        o.put("lastScanned", tag.getLastScannedAt());

        // tagIds array
        JSONArray ids = new JSONArray();
        if (tag.getTagIds() != null)
            for (String id : tag.getTagIds()) ids.put(id);
        o.put("tagIds", ids);

        // records array
        JSONArray rArr = new JSONArray();
        if (tag.getRecords() != null)
            for (TagRecord r : tag.getRecords()) rArr.put(recordToJson(r));
        o.put("records", rArr);

        return o;
    }

    private NfcTag tagFromJson(JSONObject o) throws Exception {
        NfcTag tag = new NfcTag();
        tag.setGroupId(o.optString("groupId", o.optString("tagId", "")));
        tag.setName(o.optString("name", ""));
        tag.setCreatedAt(o.optLong("createdAt", System.currentTimeMillis()));
        tag.setLastScannedAt(o.optLong("lastScanned", System.currentTimeMillis()));

        // Support both old format (single "tagId") and new ("tagIds" array)
        List<String> ids = new ArrayList<>();
        if (o.has("tagIds")) {
            JSONArray arr = o.getJSONArray("tagIds");
            for (int i = 0; i < arr.length(); i++) ids.add(arr.getString(i));
        } else if (o.has("tagId")) {
            // migration: old single-tagId format
            String oldId = o.getString("tagId");
            if (!oldId.isEmpty()) ids.add(oldId);
        }
        tag.setTagIds(ids);

        // records
        JSONArray rArr = o.optJSONArray("records");
        List<TagRecord> records = new ArrayList<>();
        if (rArr != null)
            for (int i = 0; i < rArr.length(); i++)
                records.add(recordFromJson(rArr.getJSONObject(i)));
        tag.setRecords(records);

        return tag;
    }

    private JSONObject recordToJson(TagRecord r) throws Exception {
        JSONObject o = new JSONObject();
        o.put("recordId",   r.getRecordId());
        o.put("title",      r.getTitle() != null ? r.getTitle() : "");
        o.put("note",       r.getNote()  != null ? r.getNote()  : "");
        o.put("createdAt",  r.getCreatedAt());
        JSONArray imgs = new JSONArray();
        if (r.getImagePaths() != null) for (String p : r.getImagePaths()) imgs.put(p);
        o.put("imagePaths", imgs);
        return o;
    }

    private TagRecord recordFromJson(JSONObject o) throws Exception {
        TagRecord r = new TagRecord();
        r.setRecordId(o.optString("recordId", String.valueOf(System.currentTimeMillis())));
        r.setTitle(o.optString("title", ""));
        r.setNote(o.optString("note", ""));
        r.setCreatedAt(o.optLong("createdAt", System.currentTimeMillis()));
        JSONArray imgs = o.optJSONArray("imagePaths");
        List<String> paths = new ArrayList<>();
        if (imgs != null) for (int i = 0; i < imgs.length(); i++) paths.add(imgs.getString(i));
        r.setImagePaths(paths);
        return r;
    }

    // ─────────────────────────── File helpers ────────────────────────

    private byte[] readFile(File f) throws Exception {
        FileInputStream fis = new FileInputStream(f);
        byte[] data = new byte[(int) f.length()];
        fis.read(data); fis.close();
        return data;
    }

    private void writeFile(File f, byte[] data) throws Exception {
        f.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(data); fos.close();
    }
}
