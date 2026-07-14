package com.k1datanfc;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements TagListAdapter.TagClickListener {

    private static final int REQUEST_CAMERA_PERMISSION = 300;

    private NfcHelper       nfcHelper;
    private DatabaseManager dbManager;
    private TagListAdapter  adapter;
    private List<NfcTag>    allTags = new ArrayList<>();

    private RecyclerView recyclerView;
    private View         emptyView;

    // ZXing QR scanner
    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), this::handleQrResult);

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
        emptyView    = findViewById(R.id.empty_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TagListAdapter(this, new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab_scan);
        fab.setOnClickListener(v -> showScanDialog());
    }

    private void checkNfcStatus() {
        if (!nfcHelper.isNfcSupported()) {
            Snackbar.make(recyclerView, R.string.nfc_not_supported, Snackbar.LENGTH_LONG).show();
        } else if (!nfcHelper.isNfcEnabled()) {
            Snackbar.make(recyclerView, R.string.nfc_disabled, Snackbar.LENGTH_LONG)
                    .setAction("تنظیمات", v ->
                            startActivity(new Intent(android.provider.Settings.ACTION_NFC_SETTINGS)))
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
        openTag(tagId);
    }

    private void openTag(String tagId) {
        NfcTag existing = dbManager.findTagById(tagId);
        boolean isNew   = existing == null;
        if (existing != null) {
            existing.touchScanned();
            dbManager.saveTag(existing);
        }
        Intent intent = new Intent(this, TagDetailActivity.class);
        intent.putExtra(TagDetailActivity.EXTRA_TAG_ID, tagId);
        intent.putExtra(TagDetailActivity.EXTRA_IS_NEW, isNew);
        startActivity(intent);
    }

    // ── TagListAdapter.TagClickListener ───────────────────────────────

    @Override
    public void onTagClick(NfcTag tag) {
        Intent intent = new Intent(this, TagDetailActivity.class);
        intent.putExtra(TagDetailActivity.EXTRA_TAG_ID, tag.getTagId());
        intent.putExtra(TagDetailActivity.EXTRA_IS_NEW, false);
        startActivity(intent);
    }

    @Override
    public void onTagLongClick(NfcTag tag) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm)
                .setMessage("تگ \"" + tag.getName() + "\" و تمام رکوردهایش حذف شود؟")
                .setPositiveButton(R.string.yes, (d, w) -> {
                    dbManager.deleteTag(tag.getTagId());
                    loadTags();
                    Toast.makeText(this, R.string.tag_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    // ── Search ────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) { return false; }
            @Override public boolean onQueryTextChange(String q) { filterTags(q); return true; }
        });
        return true;
    }

    private void filterTags(String query) {
        if (query == null || query.isEmpty()) {
            adapter.updateTags(allTags);
            return;
        }
        String q = query.toLowerCase();
        List<NfcTag> filtered = new ArrayList<>();
        for (NfcTag tag : allTags) {
            // Search in tag name
            String name = tag.getName() != null ? tag.getName().toLowerCase() : "";
            if (name.contains(q)) { filtered.add(tag); continue; }
            // Search in record titles and notes
            boolean found = false;
            if (tag.getRecords() != null) {
                for (TagRecord r : tag.getRecords()) {
                    String title = r.getTitle() != null ? r.getTitle().toLowerCase() : "";
                    String note  = r.getNote()  != null ? r.getNote().toLowerCase()  : "";
                    if (title.contains(q) || note.contains(q)) { found = true; break; }
                }
            }
            if (found) filtered.add(tag);
        }
        adapter.updateTags(filtered);
    }

    // ── Options menu ──────────────────────────────────────────────────

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_scan_qr) {
            startQrScan(); return true;
        } else if (id == R.id.action_backup) {
            startActivity(new Intent(this, BackupActivity.class)); return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class)); return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── QR Scanner ────────────────────────────────────────────────────

    private void startQrScan() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        launchQrScanner();
    }

    private void launchQrScanner() {
        ScanOptions opts = new ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("کد QR را در مقابل دوربین قرار دهید")
                .setCameraId(0)
                .setBeepEnabled(true)
                .setBarcodeImageEnabled(false)
                .setOrientationLocked(false);
        qrLauncher.launch(opts);
    }

    private void handleQrResult(ScanIntentResult result) {
        if (result.getContents() == null) return;
        openTag("QR:" + result.getContents());
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQUEST_CAMERA_PERMISSION && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            launchQrScanner();
        } else if (req == REQUEST_CAMERA_PERMISSION) {
            Toast.makeText(this, "دسترسی به دوربین لازم است", Toast.LENGTH_SHORT).show();
        }
    }
}
