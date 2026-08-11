package com.k1datanfc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a Record Group — one logical entity (e.g. "موتور سیکلت")
 * that can be reached by scanning ANY of its associated tag IDs.
 *
 * Key design:
 *   groupId  → stable internal UUID, never changes
 *   tagIds   → list of NFC hardware IDs or "QR:<content>" strings
 *              Multiple tags can point to the same group.
 *   records  → the actual logbook entries
 */
public class NfcTag {

    private String       groupId;    // stable UUID, never shown to user
    private String       name;
    private List<String> tagIds;     // all NFC/QR IDs linked to this group
    private List<TagRecord> records;
    private long         createdAt;
    private long         lastScannedAt;

    public NfcTag() {
        this.groupId      = UUID.randomUUID().toString();
        this.tagIds       = new ArrayList<>();
        this.records      = new ArrayList<>();
        this.createdAt    = System.currentTimeMillis();
        this.lastScannedAt= System.currentTimeMillis();
    }

    /** Convenience constructor: create a group with one initial tag ID */
    public NfcTag(String firstTagId) {
        this();
        this.tagIds.add(firstTagId);
    }

    // ── groupId ──────────────────────────────────────────────────────
    public String getGroupId()            { return groupId; }
    public void   setGroupId(String id)   { this.groupId = id; }

    // ── name ─────────────────────────────────────────────────────────
    public String getName()               { return name; }
    public void   setName(String n)       { this.name = n; }

    // ── tagIds ───────────────────────────────────────────────────────
    public List<String> getTagIds()       { return tagIds; }
    public void setTagIds(List<String> t) { this.tagIds = t; }

    public void addTagId(String id) {
        if (tagIds == null) tagIds = new ArrayList<>();
        if (!tagIds.contains(id)) tagIds.add(id);
    }

    public void removeTagId(String id) {
        if (tagIds != null) tagIds.remove(id);
    }

    public boolean hasTagId(String id) {
        return tagIds != null && tagIds.contains(id);
    }

    /** Returns the first tag ID (for display purposes) */
    public String getPrimaryTagId() {
        return (tagIds != null && !tagIds.isEmpty()) ? tagIds.get(0) : groupId;
    }

    // ── records ──────────────────────────────────────────────────────
    public List<TagRecord> getRecords()      { return records; }
    public void setRecords(List<TagRecord> r){ this.records = r; }

    public void addRecord(TagRecord r) {
        if (records == null) records = new ArrayList<>();
        records.add(0, r);
    }

    public void removeRecord(String recordId) {
        if (records != null) records.removeIf(r -> r.getRecordId().equals(recordId));
    }

    public TagRecord findRecord(String recordId) {
        if (records == null) return null;
        for (TagRecord r : records)
            if (r.getRecordId().equals(recordId)) return r;
        return null;
    }

    public List<String> getAllImagePaths() {
        List<String> all = new ArrayList<>();
        if (records != null)
            for (TagRecord r : records)
                if (r.getImagePaths() != null) all.addAll(r.getImagePaths());
        return all;
    }

    // ── timestamps ───────────────────────────────────────────────────
    public long getCreatedAt()             { return createdAt; }
    public void setCreatedAt(long t)       { this.createdAt = t; }

    public long getLastScannedAt()         { return lastScannedAt; }
    public void setLastScannedAt(long t)   { this.lastScannedAt = t; }
    public void touchScanned()             { this.lastScannedAt = System.currentTimeMillis(); }
}
