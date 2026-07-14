package com.k1datanfc;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

public class TagDetailActivity extends AppCompatActivity implements RecordAdapter.Listener {

    public static final String EXTRA_TAG_ID = "tag_id";
    public static final String EXTRA_IS_NEW = "is_new";

    private DatabaseManager dbManager;
    private NfcTag currentTag;

    private TextView tvTagId, tvTagName, tvEmpty;
    private RecyclerView recyclerRecords;
    private RecordAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_detail);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        dbManager = K1Application.getInstance().getDatabaseManager();

        String tagId = getIntent().getStringExtra(EXTRA_TAG_ID);
        boolean isNew = getIntent().getBooleanExtra(EXTRA_IS_NEW, false);

        // Load or create tag
        currentTag = dbManager.findTagById(tagId);
        if (currentTag == null) {
            currentTag = new NfcTag(tagId);
            dbManager.saveTag(currentTag);
        }

        setupViews();

        if (isNew || (currentTag.getName() == null || currentTag.getName().isEmpty())) {
            promptEditName(true);
        }
    }

    private void setupViews() {
        tvTagId   = findViewById(R.id.tv_tag_id);
        tvTagName = findViewById(R.id.tv_tag_name);
        tvEmpty   = findViewById(R.id.tv_empty_records);
        recyclerRecords = findViewById(R.id.recycler_records);

        tvTagId.setText("ID: " + currentTag.getTagId());
        refreshName();

        recyclerRecords.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecordAdapter(this, currentTag.getRecords(), this);
        recyclerRecords.setAdapter(adapter);
        refreshEmpty();

        // Edit name on tap
        tvTagName.setOnClickListener(v -> promptEditName(false));

        // FAB — add new record
        ExtendedFloatingActionButton fab = findViewById(R.id.fab_add_record);
        fab.setOnClickListener(v -> openRecordEditor(null));
    }

    private void refreshName() {
        String name = currentTag.getName();
        tvTagName.setText(name != null && !name.isEmpty() ? name : "بدون نام");
        getSupportActionBar().setTitle(name != null && !name.isEmpty() ? name : "جزئیات تگ");
    }

    private void refreshEmpty() {
        boolean empty = currentTag.getRecords() == null || currentTag.getRecords().isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerRecords.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /** Open RecordEditActivity for a new record (null) or existing one */
    private void openRecordEditor(TagRecord record) {
        Intent intent = new Intent(this, RecordEditActivity.class);
        intent.putExtra(RecordEditActivity.EXTRA_TAG_ID, currentTag.getTagId());
        if (record != null)
            intent.putExtra(RecordEditActivity.EXTRA_RECORD_ID, record.getRecordId());
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            // Reload tag from DB (record was saved inside RecordEditActivity)
            currentTag = dbManager.findTagById(currentTag.getTagId());
            if (currentTag == null) { finish(); return; }
            adapter.update(currentTag.getRecords());
            refreshEmpty();
        }
    }

    // ── RecordAdapter.Listener ─────────────────────────────────────────

    @Override
    public void onRecordClick(TagRecord record) {
        openRecordEditor(record);
    }

    @Override
    public void onRecordDelete(TagRecord record) {
        new AlertDialog.Builder(this)
                .setMessage("رکورد «" + record.getTitle() + "» حذف شود؟")
                .setPositiveButton("حذف", (d, w) -> {
                    // Delete image files
                    if (record.getImagePaths() != null)
                        for (String p : record.getImagePaths()) dbManager.deleteImageFile(p);
                    currentTag.removeRecord(record.getRecordId());
                    dbManager.saveTag(currentTag);
                    adapter.update(currentTag.getRecords());
                    refreshEmpty();
                    Toast.makeText(this, "رکورد حذف شد", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    // ── Edit tag name ──────────────────────────────────────────────────

    private void promptEditName(boolean isFirst) {
        int dp16 = (int)(16 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp16*2, dp16, dp16*2, 0);

        EditText et = new EditText(this);
        et.setHint("نام تگ را وارد کنید");
        et.setSingleLine(true);
        if (currentTag.getName() != null) et.setText(currentTag.getName());
        layout.addView(et);

        new AlertDialog.Builder(this)
                .setTitle(isFirst ? "نام این تگ چیست؟" : "ویرایش نام")
                .setView(layout)
                .setPositiveButton("ذخیره", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty() && isFirst) name = "تگ بدون نام";
                    currentTag.setName(name);
                    dbManager.saveTag(currentTag);
                    refreshName();
                })
                .setNegativeButton("انصراف", (d, w) -> { if (isFirst) finish(); })
                .setCancelable(!isFirst)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
