package com.screentranslator.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.screentranslator.app.R;
import com.screentranslator.app.services.FloatingBubbleService;
import com.screentranslator.app.utils.TranslationManager;

/**
 * Main setup screen. Guides the user through:
 * 1. Granting overlay (SYSTEM_ALERT_WINDOW) permission
 * 2. Enabling the Accessibility Service
 * 3. Choosing target language
 * 4. Starting / Stopping the floating bubble service
 */
public class MainActivity extends AppCompatActivity {

    private Button btnOverlayPermission;
    private Button btnAccessibilityPermission;
    private Button btnStartService;
    private Button btnStopService;
    private Button btnDownloadModels;

    private ImageView ivOverlayStatus;
    private ImageView ivAccessibilityStatus;

    private Spinner spinnerTargetLang;

    private String selectedTargetLang = "ur";
    private TranslationManager translationManager;

    private static final String[] LANG_DISPLAY = {"Urdu", "Hindi", "Arabic", "English"};
    private static final String[] LANG_CODES   = {"ur",   "hi",   "ar",    "en"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        translationManager = new TranslationManager();

        bindViews();
        setupLanguageSpinner();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
    }

    private void bindViews() {
        btnOverlayPermission      = findViewById(R.id.btn_overlay_permission);
        btnAccessibilityPermission = findViewById(R.id.btn_accessibility_permission);
        btnStartService           = findViewById(R.id.btn_start_service);
        btnStopService            = findViewById(R.id.btn_stop_service);
        btnDownloadModels         = findViewById(R.id.btn_download_models);
        ivOverlayStatus           = findViewById(R.id.iv_overlay_status);
        ivAccessibilityStatus     = findViewById(R.id.iv_accessibility_status);
        spinnerTargetLang         = findViewById(R.id.spinner_target_lang);
    }

    private void setupLanguageSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, LANG_DISPLAY);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTargetLang.setAdapter(adapter);

        spinnerTargetLang.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                selectedTargetLang = LANG_CODES[pos];
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupClickListeners() {
        btnOverlayPermission.setOnClickListener(v -> requestOverlayPermission());
        btnAccessibilityPermission.setOnClickListener(v -> openAccessibilitySettings());
        btnStartService.setOnClickListener(v -> startFloatingService());
        btnStopService.setOnClickListener(v -> stopFloatingService());
        btnDownloadModels.setOnClickListener(v -> downloadModels());
    }

    // ─── Permission Helpers ───────────────────────────────────────────────────

    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this,
                "Find 'Screen Translator' in Accessibility and enable it",
                Toast.LENGTH_LONG).show();
    }

    private void refreshPermissionStatus() {
        boolean hasOverlay = Settings.canDrawOverlays(this);
        ivOverlayStatus.setImageResource(
                hasOverlay ? R.drawable.ic_check : R.drawable.ic_warning);
        btnOverlayPermission.setText(hasOverlay ? "Overlay: Granted" : "Grant Overlay Permission");
        btnOverlayPermission.setEnabled(!hasOverlay);

        boolean startEnabled = hasOverlay;
        btnStartService.setEnabled(startEnabled);
    }

    // ─── Service Control ──────────────────────────────────────────────────────

    private void startFloatingService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent serviceIntent = new Intent(this, FloatingBubbleService.class);
        serviceIntent.putExtra("target_language", selectedTargetLang);
        startForegroundService(serviceIntent);

        Toast.makeText(this,
                "Floating bubble started! Tap or drag it over any text to translate.",
                Toast.LENGTH_LONG).show();

        btnStartService.setEnabled(false);
        btnStopService.setEnabled(true);

        // Go to home screen so user can see floating bubble on other apps
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        startActivity(home);
    }

    private void stopFloatingService() {
        Intent serviceIntent = new Intent(this, FloatingBubbleService.class);
        stopService(serviceIntent);
        btnStartService.setEnabled(true);
        btnStopService.setEnabled(false);
        Toast.makeText(this, "Floating bubble stopped", Toast.LENGTH_SHORT).show();
    }

    // ─── Model Download ───────────────────────────────────────────────────────

    private void downloadModels() {
        btnDownloadModels.setEnabled(false);
        btnDownloadModels.setText("Downloading...");

        translationManager.preDownloadModels(selectedTargetLang,
                new TranslationManager.ModelDownloadCallback() {
                    @Override
                    public void onComplete() {
                        runOnUiThread(() -> {
                            btnDownloadModels.setEnabled(true);
                            btnDownloadModels.setText("Download Offline Models");
                            Toast.makeText(MainActivity.this,
                                    "Model downloaded for offline use!",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onFailure(String error) {
                        runOnUiThread(() -> {
                            btnDownloadModels.setEnabled(true);
                            btnDownloadModels.setText("Download Offline Models");
                            Toast.makeText(MainActivity.this,
                                    "Download failed: " + error,
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }
}
