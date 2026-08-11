package com.k1datanfc;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
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

import java.util.List;

public class TagDetailActivity extends AppCompatActivity implements RecordAdapter.Listener {

    public static final String EXTRA_TAG_ID  = "tag_id";
    public static final String EXTRA_IS_NEW  = "is_new";
    public static final String EXTRA_GROUP_ID= "group_id";

    private DatabaseManager dbManager;
    private NfcTag          currentTag;
    private String          scannedTagId;

    private TextView      tvTagId, tvEmpty;
    private RecyclerView  recyclerRecords;
    private RecordAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_detail);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        dbManager    = K1Application.getInstance().getDatabaseManager();
        scannedTagId = getIntent().getStringExtra(EXTRA_TAG_ID);
        String groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        boolean isNew  = getIntent().getBooleanExtra(EXTRA_IS_NEW, false);

        if (groupId != null) {
            currentTag = dbManager.findTagByGroupId(groupId);
        } else if (scannedTagId != null) {
            currentTag = dbManager.findTagById(scannedTagId);
        }

        if (currentTag == null) {
            currentTag = new NfcTag(scannedTagId);
            dbManager.saveTag(currentTag);
        }

        setupViews();

        if (isNew || currentTag.getName() == null || currentTag.getName().isEmpty()) {
            promptEditName(true);
        }
    }

    private void setupViews() {
        tvTagId         = findViewById(R.id.tv_tag_id);
        tvEmpty         = findViewById(R.id.tv_empty_records);
        recyclerRecords = findViewById(R.id.recycler_records);

        refreshTagIdDisplay();
        refreshTitle();

        recyclerRecords.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecordAdapter(this, currentTag.getRecords(), this);
        recyclerRecords.setAdapter(adapter);
        refreshEmpty();

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnLongClickListener(v -> { promptEditName(false); return true; });

        ExtendedFloatingActionButton fab = findViewById(R.id.fab_add_record);
        fab.setOnClickListener(v -> openRecordEditor(null));
    }

    private void refreshTagIdDisplay() {
        List<String> ids = currentTag.getTagIds();
        if (ids == null || ids.isEmpty()) {
            tvTagId.setText("بدون تگ");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(ids.get(i));
        }
        tvTagId.setText(sb.toString());
    }

    private void refreshTitle() {
        String name = currentTag.getName();
        getSupportActionBar().setTitle(
                (name != null && !name.isEmpty()) ? name : "بدون نام");
    }

    private void refreshEmpty() {
        boolean empty = currentTag.getRecords() == null || currentTag.getRecords().isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerRecords.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // ── Options menu ──────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.tag_detail_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home)         { finish(); return true; }
        if (id == R.id.action_manage_tags)   { showManageTagsDialog(); return true; }
        if (id == R.id.action_rename)        { promptEditName(false); return true; }
        return super.onOptionsItemSelected(item);
    }

    // ── Manage linked tags ────────────────────────────────────────────

    private void showManageTagsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("مدیریت تگ‌ها")
                .setItems(new String[]{
                        "➕  افزودن تگ/QR جدید به این گروه",
                        "🗑️  حذف یک تگ از این گروه",
                        "📋  نمایش همه تگ‌های مرتبط"
                }, (d, which) -> {
                    if (which == 0)      promptAddNewTag();
                    else if (which == 1) promptRemoveTag();
                    else                 showAllTagIds();
                })
                .show();
    }

    private void promptAddNewTag() {
        new AlertDialog.Builder(this)
                .setTitle("افزودن تگ جدید")
                .setMessage("تگ NFC یا QR کد جدید را اسکن کنید.\n\n" +
                        "سپس برگردید و دوباره آن تگ را اسکن کنید — " +
                        "از شما پرسیده می‌شود به این گروه اضافه شود.\n\n" +
                        "یا اگر ID تگ را می‌دانید، دستی وارد کنید:")
                .setPositiveButton("وارد کردن دستی", (d, w) -> promptManualTagId())
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void promptManualTagId() {
        int dp = (int)(16 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp * 2, dp, dp * 2, 0);
        EditText et = new EditText(this);
        et.setHint("مثال: AABBCCDD یا QR:https://...");
        et.setSingleLine(true);
        layout.addView(et);

        new AlertDialog.Builder(this)
                .setTitle("وارد کردن ID تگ")
                .setView(layout)
                .setPositiveButton("افزودن", (d, w) -> {
                    String newId = et.getText().toString().trim();
                    if (newId.isEmpty()) return;
                    NfcTag existing = dbManager.findTagById(newId);
                    if (existing != null && !existing.getGroupId().equals(currentTag.getGroupId())) {
                        new AlertDialog.Builder(this)
                                .setMessage("این تگ قبلاً به گروه «" + existing.getName()
                                        + "» وصل است. از آنجا جدا و به اینجا اضافه شود؟")
                                .setPositiveButton("بله", (d2, w2) -> {
                                    existing.removeTagId(newId);
                                    dbManager.saveTag(existing);
                                    addTagIdToCurrentGroup(newId);
                                })
                                .setNegativeButton("خیر", null).show();
                    } else if (existing != null) {
                        Toast.makeText(this, "این تگ قبلاً به همین گروه وصل است",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        addTagIdToCurrentGroup(newId);
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void addTagIdToCurrentGroup(String newTagId) {
        currentTag.addTagId(newTagId);
        dbManager.saveTag(currentTag);
        refreshTagIdDisplay();
        Toast.makeText(this, "تگ جدید اضافه شد", Toast.LENGTH_SHORT).show();
    }

    public static void offerLinkToExistingGroup(android.content.Context ctx,
                                                  String newTagId,
                                                  DatabaseManager db,
                                                  Runnable onCreateNew) {
        List<NfcTag> all = db.loadAllTags();
        if (all.isEmpty()) { onCreateNew.run(); return; }

        String[] names = new String[all.size() + 1];
        for (int i = 0; i < all.size(); i++)
            names[i] = (all.get(i).getName() != null ? all.get(i).getName() : "بدون نام");
        names[all.size()] = "➕  ایجاد گروه جدید";

        new AlertDialog.Builder(ctx)
                .setTitle("تگ جدید — کجا اضافه شود؟")
                .setItems(names, (d, which) -> {
                    if (which == all.size()) {
                        onCreateNew.run();
                    } else {
                        NfcTag chosen = all.get(which);
                        chosen.addTagId(newTagId);
                        db.saveTag(chosen);
                        Intent intent = new Intent(ctx, TagDetailActivity.class);
                        intent.putExtra(EXTRA_GROUP_ID, chosen.getGroupId());
                        intent.putExtra(EXTRA_IS_NEW, false);
                        ctx.startActivity(intent);
                    }
                })
                .show();
    }

    private void promptRemoveTag() {
        List<String> ids = currentTag.getTagIds();
        if (ids == null || ids.isEmpty()) {
            Toast.makeText(this, "هیچ تگی وجود ندارد", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ids.size() == 1) {
            Toast.makeText(this, "حداقل یک تگ باید باقی بماند", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] arr = ids.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("کدام تگ حذف شود؟")
                .setItems(arr, (d, which) -> {
                    String toRemove = arr[which];
                    new AlertDialog.Builder(this)
                            .setMessage("تگ " + toRemove + " از این گروه حذف شود؟")
                            .setPositiveButton("حذف", (d2, w2) -> {
                                currentTag.removeTagId(toRemove);
                                dbManager.saveTag(currentTag);
                                refreshTagIdDisplay();
                                Toast.makeText(this, "تگ حذف شد", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("انصراف", null).show();
                })
                .show();
    }

    private void showAllTagIds() {
        List<String> ids = currentTag.getTagIds();
        if (ids == null || ids.isEmpty()) {
            Toast.makeText(this, "هیچ تگی وصل نیست", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++)
            sb.append(i + 1).append(". ").append(ids.get(i)).append("\n");
        new AlertDialog.Builder(this)
                .setTitle("تگ‌های مرتبط (" + ids.size() + " عدد)")
                .setMessage(sb.toString().trim())
                .setPositiveButton("باشه", null)
                .show();
    }

    // ── Records ───────────────────────────────────────────────────────

    private void openRecordEditor(TagRecord record) {
        Intent intent = new Intent(this, RecordEditActivity.class);
        intent.putExtra(RecordEditActivity.EXTRA_GROUP_ID, currentTag.getGroupId());
        if (record != null)
            intent.putExtra(RecordEditActivity.EXTRA_RECORD_ID, record.getRecordId());
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 100 && res == RESULT_OK) {
            currentTag = dbManager.findTagByGroupId(currentTag.getGroupId());
            if (currentTag == null) { finish(); return; }
            adapter.update(currentTag.getRecords());
            refreshEmpty();
        }
    }

    @Override public void onRecordClick(TagRecord record) { openRecordEditor(record); }

    @Override
    public void onRecordDelete(TagRecord record) {
        new AlertDialog.Builder(this)
                .setMessage("رکورد «" + record.getTitle() + "» حذف شود؟")
                .setPositiveButton("حذف", (d, w) -> {
                    if (record.getImagePaths() != null)
                        for (String p : record.getImagePaths()) dbManager.deleteImageFile(p);
                    currentTag.removeRecord(record.getRecordId());
                    dbManager.saveTag(currentTag);
                    adapter.update(currentTag.getRecords());
                    refreshEmpty();
                    Toast.makeText(this, "رکورد حذف شد", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("انصراف", null).show();
    }

    // ── Rename ────────────────────────────────────────────────────────

    private void promptEditName(boolean isFirst) {
        int dp = (int)(16 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp * 2, dp, dp * 2, 0);
        EditText et = new EditText(this);
        et.setHint("نام گروه را وارد کنید");
        et.setSingleLine(true);
        if (currentTag.getName() != null) et.setText(currentTag.getName());
        layout.addView(et);
        new AlertDialog.Builder(this)
                .setTitle(isFirst ? "نام این گروه چیست؟" : "ویرایش نام")
                .setView(layout)
                .setPositiveButton("ذخیره", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty() && isFirst) name = "گروه بدون نام";
                    currentTag.setName(name);
                    dbManager.saveTag(currentTag);
                    refreshTitle();
                })
                .setNegativeButton("انصراف", (d, w) -> { if (isFirst) finish(); })
                .setCancelable(!isFirst)
                .show();
    }
}
