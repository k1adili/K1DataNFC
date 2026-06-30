package com.k1datanfc;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model for an NFC tag with associated encrypted data.
 */
public class NfcTag {
    private String tagId;          // Unique NFC hardware ID (hex string)
    private String name;           // User-given name (encrypted at rest)
    private String note;           // User text note (encrypted at rest)
    private List<String> imagePaths; // List of encrypted image file paths
    private long createdAt;
    private long updatedAt;
    private long lastScannedAt;

    public NfcTag() {
        this.imagePaths = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.lastScannedAt = System.currentTimeMillis();
    }

    public NfcTag(String tagId) {
        this();
        this.tagId = tagId;
    }

    // Getters and setters
    public String getTagId() { return tagId; }
    public void setTagId(String tagId) { this.tagId = tagId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public List<String> getImagePaths() { return imagePaths; }
    public void setImagePaths(List<String> imagePaths) { this.imagePaths = imagePaths; }
    public void addImagePath(String path) {
        if (this.imagePaths == null) this.imagePaths = new ArrayList<>();
        this.imagePaths.add(path);
    }
    public void removeImagePath(String path) {
        if (this.imagePaths != null) this.imagePaths.remove(path);
    }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public long getLastScannedAt() { return lastScannedAt; }
    public void setLastScannedAt(long lastScannedAt) { this.lastScannedAt = lastScannedAt; }

    public void touchUpdated() {
        this.updatedAt = System.currentTimeMillis();
    }
}
