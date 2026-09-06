package com.k1datanfc;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_SETTINGS = "k1_settings";
    private static final String KEY_PIN_HASH   = "pin_hash";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.settings);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Version & about
        TextView tvVersion   = findViewById(R.id.tv_version);
        TextView tvDeveloper = findViewById(R.id.tv_developer);
        tvVersion.setText("Version 3.3 (Multi tag)");
        tvDeveloper.setText("Developed by Keyvan Adili");

        // Encryption info
        TextView tvEncInfo = findViewById(R.id.tv_enc_info);
        tvEncInfo.setText("Encryption: AES-256-GCM\nKey stored in Android Keystore\nData is only readable by this app");

        // PIN setup
        EditText etPin        = findViewById(R.id.et_pin);
        EditText etPinConfirm = findViewById(R.id.et_pin_confirm);
        Button   btnSavePin   = findViewById(R.id.btn_save_pin);
        Button   btnDeletePin = findViewById(R.id.btn_delete_pin);

        refreshDeletePinButton(btnDeletePin);

        btnSavePin.setOnClickListener(v -> {
            String pin     = etPin.getText().toString().trim();
            String confirm = etPinConfirm.getText().toString().trim();
            if (pin.isEmpty()) {
                Toast.makeText(this, "رمز عبور نمی‌تواند خالی باشد", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!pin.equals(confirm)) {
                Toast.makeText(this, R.string.pin_mismatch, Toast.LENGTH_SHORT).show();
                return;
            }
            getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).edit()
                    .putString(KEY_PIN_HASH, Integer.toHexString(pin.hashCode())).apply();
            etPin.setText("");
            etPinConfirm.setText("");
            Toast.makeText(this, R.string.pin_changed, Toast.LENGTH_SHORT).show();
            refreshDeletePinButton(btnDeletePin);
        });

        btnDeletePin.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("حذف رمز عبور")
                .setMessage("آیا مطمئن هستید؟ بعد از حذف، برنامه بدون رمز عبور باز می‌شود.")
                .setPositiveButton("حذف", (d, w) -> {
                    getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).edit()
                            .remove(KEY_PIN_HASH).apply();
                    Toast.makeText(this, "رمز عبور حذف شد", Toast.LENGTH_SHORT).show();
                    refreshDeletePinButton(btnDeletePin);
                })
                .setNegativeButton("انصراف", null)
                .show()
        );
    }

    private void refreshDeletePinButton(Button btn) {
        boolean hasPIN = hasPinSet(this);
        btn.setEnabled(hasPIN);
        btn.setAlpha(hasPIN ? 1f : 0.4f);
    }

    // ── Static helpers used by PinActivity ───────────────────────────

    public static boolean verifyPin(android.content.Context ctx, String pin) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_SETTINGS, android.content.Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_PIN_HASH, null);
        if (saved == null) return true;
        return saved.equals(Integer.toHexString(pin.hashCode()));
    }

    public static boolean hasPinSet(android.content.Context ctx) {
        return ctx.getSharedPreferences(PREFS_SETTINGS, android.content.Context.MODE_PRIVATE)
                .contains(KEY_PIN_HASH);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
