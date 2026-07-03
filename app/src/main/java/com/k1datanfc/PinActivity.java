package com.k1datanfc;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Shown at app launch when the user has set a PIN in Settings.
 * Only proceeds to MainActivity if the correct PIN is entered.
 */
public class PinActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If no PIN is set, skip straight to main
        if (!SettingsActivity.hasPinSet(this)) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_pin);

        EditText etPin = findViewById(R.id.et_pin_input);
        Button btnUnlock = findViewById(R.id.btn_unlock);
        TextView tvError = findViewById(R.id.tv_pin_error);

        btnUnlock.setOnClickListener(v -> {
            String entered = etPin.getText().toString().trim();
            if (entered.isEmpty()) {
                tvError.setVisibility(View.VISIBLE);
                tvError.setText("رمز عبور را وارد کنید");
                return;
            }
            if (SettingsActivity.verifyPin(this, entered)) {
                goToMain();
            } else {
                etPin.setText("");
                tvError.setVisibility(View.VISIBLE);
                tvError.setText("رمز عبور اشتباه است");
            }
        });
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    // Don't let user press back to skip PIN
    @Override
    public void onBackPressed() {
        finishAffinity();
    }
}
