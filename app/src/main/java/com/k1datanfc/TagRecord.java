package com.k1datanfc;

import java.util.ArrayList;
import java.util.List;

/**
 * A single record (entry) attached to an NFC/QR tag.
 * One tag can have many records — like a timeline/logbook.
 */
public class TagRecord {
    private String recordId;      // unique ID (timestamp-based)
    private String title;         // e.g. "تعویض روغن - تیر ۱۴۰۵"
    private String note;          // detailed text
    private List<String> imagePaths; // encrypted image file paths
    private long createdAt;

    public TagRecord() {
        this.recordId   = String.valueOf(System.currentTimeMillis()) + (int)(Math.random()*1000);
        this.createdAt  = System.currentTimeMillis();
        this.imagePaths = new ArrayList<>();
    }

    public String getRecordId()               { return recordId; }
    public void   setRecordId(String id)      { this.recordId = id; }

    public String getTitle()                  { return title; }
    public void   setTitle(String t)          { this.title = t; }

    public String getNote()                   { return note; }
    public void   setNote(String n)           { this.note = n; }

    public long   getCreatedAt()              { return createdAt; }
    public void   setCreatedAt(long t)        { this.createdAt = t; }

    public List<String> getImagePaths()       { return imagePaths; }
    public void setImagePaths(List<String> p) { this.imagePaths = p; }
    public void addImagePath(String p)        { if (imagePaths==null) imagePaths=new ArrayList<>(); imagePaths.add(p); }
    public void removeImagePath(String p)     { if (imagePaths!=null) imagePaths.remove(p); }
}
