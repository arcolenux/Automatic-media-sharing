package com.penguin.app.activity;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.penguin.app.PenguinApplication;
import com.penguin.app.databinding.ActivityMainBinding;
import com.penguin.app.databinding.DialogAboutBinding;
import com.penguin.app.db.AppDatabase;
import com.penguin.app.model.Session;
import com.penguin.app.util.PermissionHelper;
import com.penguin.app.util.UserNameHelper;

/**
 * Launch screen of PENGUIN.
 * Allows creating a new session or joining an existing nearby session.
 * Automatically resumes active sessions if present.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                // Permissions handled
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        checkAndResumeActiveSession();
        setupProfileCard();
        setupClickListeners();
        requestRequiredPermissionsIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateProfileDisplay();
    }

    private void setupProfileCard() {
        updateProfileDisplay();

        View.OnClickListener editListener = v -> {
            UserNameHelper.showEditNameDialog(this, newName -> updateProfileDisplay());
        };

        binding.cardProfile.setOnClickListener(editListener);
        binding.btnEditProfileName.setOnClickListener(editListener);
    }

    private void updateProfileDisplay() {
        String currentName = PenguinApplication.getInstance().getUserName();
        binding.tvProfileName.setText(currentName != null && !currentName.isEmpty() ? currentName : "Set Name");
    }

    private void checkAndResumeActiveSession() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Session activeSession = PenguinApplication.getInstance().getDatabase().sessionDao().getActiveSession();
            if (activeSession != null && activeSession.isActive()) {
                runOnUiThread(() -> {
                    Intent intent = new Intent(MainActivity.this, SessionActivity.class);
                    intent.putExtra(SessionActivity.EXTRA_SESSION_ID, activeSession.getSessionId());
                    startActivity(intent);
                    finish();
                });
            } else {
                runOnUiThread(() -> com.penguin.app.service.MediaDetectionService.stop(MainActivity.this));
            }
        });
    }

    private void setupClickListeners() {
        binding.btnAbout.setOnClickListener(v -> showAboutDialog());

        binding.btnCreateSession.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CreateSessionActivity.class);
            startActivity(intent);
        });

        binding.btnJoinSession.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, JoinSessionActivity.class);
            startActivity(intent);
        });
    }

    private void showAboutDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        DialogAboutBinding aboutBinding = DialogAboutBinding.inflate(getLayoutInflater());
        dialog.setContentView(aboutBinding.getRoot());

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        aboutBinding.btnGotIt.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void requestRequiredPermissionsIfNeeded() {
        String[] missing = PermissionHelper.getMissingPermissions(this);
        if (missing.length > 0) {
            permissionLauncher.launch(missing);
        }
    }
}
