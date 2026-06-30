package com.k1datanfc;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_SETTINGS = "k1_settings";
    private static final String KEY_PIN_HASH = "pin_hash";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.settings);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // App version
        TextView tvVersion = findViewById(R.id.tv_version);
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText("نسخه " + versionName);
        } catch (Exception e) { tvVersion.setText(R.string.version); }

        // PIN setup
        EditText etPin = findViewById(R.id.et_pin);
        EditText etPinConfirm = findViewById(R.id.et_pin_confirm);
        Button btnSavePin = findViewById(R.id.btn_save_pin);
        btnSavePin.setOnClickListener(v -> {
            String pin = etPin.getText().toString().trim();
            String confirm = etPinConfirm.getText().toString().trim();
            if (pin.isEmpty()) {
                Toast.makeText(this, "رمز عبور نمی‌تواند خالی باشد", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!pin.equals(confirm)) {
                Toast.makeText(this, R.string.pin_mismatch, Toast.LENGTH_SHORT).show();
                return;
            }
            // Store hashed PIN
            String hash = Integer.toHexString(pin.hashCode());
            getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).edit()
                    .putString(KEY_PIN_HASH, hash).apply();
            etPin.setText("");
            etPinConfirm.setText("");
            Toast.makeText(this, R.string.pin_changed, Toast.LENGTH_SHORT).show();
        });

        // Encryption info
        TextView tvEncInfo = findViewById(R.id.tv_enc_info);
        tvEncInfo.setText("رمزنگاری: AES-256-GCM\nکلید در Android Keystore ذخیره می‌شود\nداده‌ها فقط توسط این اپ قابل خواندن هستند");
    }

    public static boolean verifyPin(android.content.Context ctx, String pin) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_SETTINGS, android.content.Context.MODE_PRIVATE);
        String savedHash = prefs.getString(KEY_PIN_HASH, null);
        if (savedHash == null) return true; // no PIN set
        return savedHash.equals(Integer.toHexString(pin.hashCode()));
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
