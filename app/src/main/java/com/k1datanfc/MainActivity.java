package com.k1datanfc;

import android.app.AlertDialog;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements TagListAdapter.TagClickListener {

    private NfcHelper nfcHelper;
    private DatabaseManager dbManager;
    private TagListAdapter adapter;
    private List<NfcTag> allTags = new ArrayList<>();

    private RecyclerView recyclerView;
    private View emptyView;
    private View nfcScanHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.app_name);

        dbManager = K1Application.getInstance().getDatabaseManager();
        nfcHelper = new NfcHelper(this);

        setupViews();
        checkNfcStatus();
        handleIntent(getIntent());
    }

    private void setupViews() {
        recyclerView = findViewById(R.id.recycler_tags);
        emptyView = findViewById(R.id.empty_view);
        nfcScanHint = findViewById(R.id.nfc_scan_hint);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TagListAdapter(this, new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        FloatingActionButton fabScan = findViewById(R.id.fab_scan);
        fabScan.setOnClickListener(v -> showScanDialog());
    }

    private void checkNfcStatus() {
        if (!nfcHelper.isNfcSupported()) {
            Snackbar.make(recyclerView, R.string.nfc_not_supported, Snackbar.LENGTH_LONG).show();
        } else if (!nfcHelper.isNfcEnabled()) {
            Snackbar.make(recyclerView, R.string.nfc_disabled, Snackbar.LENGTH_LONG)
                    .setAction("تنظیمات", v -> startActivity(new Intent(android.provider.Settings.ACTION_NFC_SETTINGS)))
                    .show();
        }
    }

    private void loadTags() {
        allTags = dbManager.loadAllTags();
        adapter.updateTags(allTags);
        emptyView.setVisibility(allTags.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(allTags.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showScanDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.scan_nfc)
                .setMessage(R.string.scan_instruction)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        nfcHelper.enableForegroundDispatch();
        loadTags();
    }

    @Override
    protected void onPause() {
        super.onPause();
        nfcHelper.disableForegroundDispatch();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (!NfcHelper.isNfcIntent(intent)) return;

        String tagId = NfcHelper.getTagId(intent);
        if (tagId == null || tagId.isEmpty()) {
            Toast.makeText(this, "خواندن تگ ناموفق بود", Toast.LENGTH_SHORT).show();
            return;
        }

        NfcTag existingTag = dbManager.findTagById(tagId);
        if (existingTag != null) {
            // Update last scanned time
            existingTag.setLastScannedAt(System.currentTimeMillis());
            dbManager.saveTag(existingTag);
            // Open detail view
            openTagDetail(tagId, false);
            Snackbar.make(recyclerView, getString(R.string.existing_tag_found), Snackbar.LENGTH_SHORT).show();
        } else {
            // New tag — open editor
            openTagDetail(tagId, true);
            Snackbar.make(recyclerView, getString(R.string.new_tag_found), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void openTagDetail(String tagId, boolean isNew) {
        Intent intent = new Intent(this, TagDetailActivity.class);
        intent.putExtra(TagDetailActivity.EXTRA_TAG_ID, tagId);
        intent.putExtra(TagDetailActivity.EXTRA_IS_NEW, isNew);
        startActivity(intent);
    }

    @Override
    public void onTagClick(NfcTag tag) {
        openTagDetail(tag.getTagId(), false);
    }

    @Override
    public void onTagLongClick(NfcTag tag) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm)
                .setMessage("تگ \"" + tag.getName() + "\" حذف شود؟")
                .setPositiveButton(R.string.yes, (d, w) -> {
                    dbManager.deleteTag(tag.getTagId());
                    loadTags();
                    Toast.makeText(this, R.string.tag_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { return false; }
            @Override
            public boolean onQueryTextChange(String newText) {
                filterTags(newText);
                return true;
            }
        });
        return true;
    }

    private void filterTags(String query) {
        if (query == null || query.isEmpty()) {
            adapter.updateTags(allTags);
            return;
        }
        List<NfcTag> filtered = new ArrayList<>();
        for (NfcTag tag : allTags) {
            String name = tag.getName() != null ? tag.getName().toLowerCase() : "";
            String note = tag.getNote() != null ? tag.getNote().toLowerCase() : "";
            if (name.contains(query.toLowerCase()) || note.contains(query.toLowerCase())) {
                filtered.add(tag);
            }
        }
        adapter.updateTags(filtered);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_backup) {
            startActivity(new Intent(this, BackupActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
