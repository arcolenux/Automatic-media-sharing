package com.penguin.app.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.penguin.app.PenguinApplication;
import com.penguin.app.R;
import com.penguin.app.databinding.ActivityCreateSessionBinding;
import com.penguin.app.db.AppDatabase;
import com.penguin.app.model.Session;
import com.penguin.app.model.SessionMember;
import com.penguin.app.transfer.TransferManager;
import com.penguin.app.util.QRCodeUtil;
import com.penguin.app.util.RoomCodeGenerator;

import java.util.UUID;

/**
 * Screen where a host creates a new PENGUIN session, generates a room code and QR code.
 */
public class CreateSessionActivity extends AppCompatActivity {

    private ActivityCreateSessionBinding binding;
    private String generatedRoomCode;
    private String sessionId;
    private String sessionName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateSessionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupListeners();
    }

    private void setupListeners() {
        binding.btnGenerateSession.setOnClickListener(v -> generateSessionData());

        binding.btnCopyCode.setOnClickListener(v -> {
            if (generatedRoomCode != null) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("PENGUIN Room Code", generatedRoomCode);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Room code copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnStartSession.setOnClickListener(v -> startSession());
    }

    private void generateSessionData() {
        String nameInput = binding.etSessionName.getText() != null
                ? binding.etSessionName.getText().toString().trim()
                : "";

        if (nameInput.isEmpty()) {
            binding.layoutSessionName.setError(getString(R.string.err_session_name_empty));
            return;
        }
        binding.layoutSessionName.setError(null);

        this.sessionName = nameInput;
        this.sessionId = UUID.randomUUID().toString();
        this.generatedRoomCode = RoomCodeGenerator.generateRoomCode();

        binding.tvRoomCode.setText(generatedRoomCode);

        // Generate QR code bitmap
        try {
            Bitmap qrBitmap = QRCodeUtil.generateQRCodeBitmap(generatedRoomCode, 400, 400);
            binding.ivQRCode.setImageBitmap(qrBitmap);
            binding.cardInvitation.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to generate QR Code", Toast.LENGTH_SHORT).show();
        }
    }

    private void startSession() {
        if (sessionId == null || generatedRoomCode == null || sessionName == null) {
            return;
        }

        String myDeviceId = PenguinApplication.getInstance().getAppDeviceId();
        String myName = PenguinApplication.getInstance().getUserName();

        Session session = new Session(
                sessionId,
                sessionName,
                generatedRoomCode,
                myDeviceId,
                System.currentTimeMillis(),
                0,
                true,
                1,
                true
        );

        SessionMember hostMember = new SessionMember(
                sessionId,
                myDeviceId,
                myName,
                "HOST",
                true,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                false
        );

        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Deactivate any previous active session
            PenguinApplication.getInstance().getDatabase().sessionDao()
                    .deactivateAllSessions(System.currentTimeMillis());

            // Save new active session and host member
            PenguinApplication.getInstance().getDatabase().sessionDao().insertSession(session);
            PenguinApplication.getInstance().getDatabase().sessionDao().insertOrUpdateMember(hostMember);

            // Start Nearby advertising
            runOnUiThread(() -> {
                TransferManager.getInstance(CreateSessionActivity.this).startHostSession(session);

                Intent intent = new Intent(CreateSessionActivity.this, SessionActivity.class);
                intent.putExtra(SessionActivity.EXTRA_SESSION_ID, sessionId);
                startActivity(intent);
                finish();
            });
        });
    }
}
