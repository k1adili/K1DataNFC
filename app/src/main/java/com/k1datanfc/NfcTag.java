package com.k1datanfc;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents one NFC tag or QR code.
 * Each tag has a name and a list of TagRecords (logbook entries).
 */
public class NfcTag {
    private String tagId;           // NFC hardware ID or "QR:<content>"
    private String name;            // user-given name for this tag
    private List<TagRecord> records; // timeline of records (newest first when displayed)
    private long createdAt;
    private long lastScannedAt;

    public NfcTag() {
        this.records      = new ArrayList<>();
        this.createdAt    = System.currentTimeMillis();
        this.lastScannedAt= System.currentTimeMillis();
    }

    public NfcTag(String tagId) {
        this();
        this.tagId = tagId;
    }

    public String getTagId()               { return tagId; }
    public void   setTagId(String id)      { this.tagId = id; }

    public String getName()                { return name; }
    public void   setName(String n)        { this.name = n; }

    public long   getCreatedAt()           { return createdAt; }
    public void   setCreatedAt(long t)     { this.createdAt = t; }

    public long   getLastScannedAt()       { return lastScannedAt; }
    public void   setLastScannedAt(long t) { this.lastScannedAt = t; }
    public void   touchScanned()           { this.lastScannedAt = System.currentTimeMillis(); }

    public List<TagRecord> getRecords()    { return records; }
    public void setRecords(List<TagRecord> r) { this.records = r; }

    public void addRecord(TagRecord r) {
        if (records == null) records = new ArrayList<>();
        records.add(0, r); // newest first
    }

    public void removeRecord(String recordId) {
        if (records == null) return;
        records.removeIf(r -> r.getRecordId().equals(recordId));
    }

    public TagRecord findRecord(String recordId) {
        if (records == null) return null;
        for (TagRecord r : records)
            if (r.getRecordId().equals(recordId)) return r;
        return null;
    }

    /** All image paths across all records — used by backup manager */
    public List<String> getAllImagePaths() {
        List<String> all = new ArrayList<>();
        if (records != null)
            for (TagRecord r : records)
                if (r.getImagePaths() != null) all.addAll(r.getImagePaths());
        return all;
    }
}
