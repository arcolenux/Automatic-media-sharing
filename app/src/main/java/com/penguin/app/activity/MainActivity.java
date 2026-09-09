package com.penguin.app.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.penguin.app.PenguinApplication;
import com.penguin.app.databinding.ActivityMainBinding;
import com.penguin.app.db.AppDatabase;
import com.penguin.app.model.Session;
import com.penguin.app.util.PermissionHelper;

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
        setupClickListeners();
        requestRequiredPermissionsIfNeeded();
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
            }
        });
    }

    private void setupClickListeners() {
        binding.btnCreateSession.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CreateSessionActivity.class);
            startActivity(intent);
        });

        binding.btnJoinSession.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, JoinSessionActivity.class);
            startActivity(intent);
        });
    }

    private void requestRequiredPermissionsIfNeeded() {
        String[] missing = PermissionHelper.getMissingPermissions(this);
        if (missing.length > 0) {
            permissionLauncher.launch(missing);
        }
    }
}
