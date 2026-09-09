package com.penguin.app.activity;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.penguin.app.PenguinApplication;
import com.penguin.app.R;
import com.penguin.app.databinding.ActivityJoinSessionBinding;
import com.penguin.app.db.AppDatabase;
import com.penguin.app.model.Session;
import com.penguin.app.model.SessionMember;
import com.penguin.app.model.SharedPhoto;
import com.penguin.app.model.SyncStatus;
import com.penguin.app.transfer.NearbyConnectionsManager;
import com.penguin.app.transfer.TransferManager;
import com.penguin.app.util.PermissionHelper;
import com.penguin.app.util.QRCodeUtil;
import com.penguin.app.util.RoomCodeGenerator;

import java.util.UUID;

/**
 * Screen where a participant joins a session by scanning the host's QR code
 * or entering a 6-character room code.
 */
public class JoinSessionActivity extends AppCompatActivity implements NearbyConnectionsManager.NearbyEventListener {

    private ActivityJoinSessionBinding binding;
    private String targetRoomCode;
    private boolean isConnecting = false;

    // ZXing QR Code Scanner Launcher
    private final ActivityResultLauncher<ScanOptions> qrScanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String extractedCode = QRCodeUtil.extractRoomCode(result.getContents());
                    if (extractedCode != null) {
                        binding.etRoomCode.setText(extractedCode);
                        joinWithRoomCode(extractedCode);
                    } else {
                        Toast.makeText(this, R.string.err_invalid_room_code, Toast.LENGTH_SHORT).show();
                    }
                }
            });

    // Camera Permission Launcher for QR scanner
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchQRScanner();
                } else {
                    Toast.makeText(this, "Camera permission is needed to scan QR codes", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityJoinSessionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        NearbyConnectionsManager.getInstance(this).addListener(this);

        setupListeners();
        handleIntentDeepLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntentDeepLink(intent);
    }

    private void handleIntentDeepLink(Intent intent) {
        if (intent != null && intent.getData() != null) {
            Uri data = intent.getData();
            String code = QRCodeUtil.extractRoomCode(data.toString());
            if (code != null) {
                binding.etRoomCode.setText(code);
                joinWithRoomCode(code);
            }
        }
    }

    private void setupListeners() {
        binding.btnScanQR.setOnClickListener(v -> {
            if (PermissionHelper.hasCameraPermission(this)) {
                launchQRScanner();
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        binding.btnJoinManual.setOnClickListener(v -> {
            String codeInput = binding.etRoomCode.getText() != null
                    ? binding.etRoomCode.getText().toString().trim()
                    : "";

            if (!RoomCodeGenerator.isValidRoomCode(codeInput)) {
                binding.layoutRoomCode.setError(getString(R.string.err_invalid_room_code));
                return;
            }
            binding.layoutRoomCode.setError(null);

            joinWithRoomCode(RoomCodeGenerator.normalize(codeInput));
        });
    }

    private void launchQRScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Align QR code inside viewfinder");
        options.setBeepEnabled(false);
        options.setOrientationLocked(true);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        qrScanLauncher.launch(options);
    }

    private void joinWithRoomCode(String roomCode) {
        this.targetRoomCode = roomCode;
        isConnecting = true;

        binding.layoutConnecting.setVisibility(View.VISIBLE);
        binding.tvConnectingStatus.setText("Searching for nearby host with code " + roomCode + "…");
        binding.btnJoinManual.setEnabled(false);
        binding.btnScanQR.setEnabled(false);

        TransferManager.getInstance(this).startJoiningSession(roomCode);
    }

    // ==========================================
    // Nearby Event Callbacks
    // ==========================================

    @Override
    public void onHostDiscovered(String endpointId, String roomCode) {
        runOnUiThread(() -> {
            binding.tvConnectingStatus.setText("Host discovered! Connecting…");
        });
    }

    @Override
    public void onPeerConnected(NearbyConnectionsManager.PeerInfo peer) {
        if (!isConnecting || targetRoomCode == null) return;

        runOnUiThread(() -> {
            String myDeviceId = PenguinApplication.getInstance().getAppDeviceId();
            String myName = PenguinApplication.getInstance().getUserName();
            String generatedSessionId = UUID.randomUUID().toString();

            Session session = new Session(
                    generatedSessionId,
                    "Session " + targetRoomCode,
                    targetRoomCode,
                    peer.deviceId,
                    System.currentTimeMillis(),
                    0,
                    true,
                    2,
                    false
            );

            SessionMember myMember = new SessionMember(
                    generatedSessionId,
                    myDeviceId,
                    myName,
                    "ME",
                    true,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    false
            );

            SessionMember hostMember = new SessionMember(
                    generatedSessionId,
                    peer.deviceId,
                    peer.displayName,
                    peer.endpointId,
                    true,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    false
            );

            AppDatabase.databaseWriteExecutor.execute(() -> {
                PenguinApplication.getInstance().getDatabase().sessionDao().deactivateAllSessions(System.currentTimeMillis());
                PenguinApplication.getInstance().getDatabase().sessionDao().insertSession(session);
                PenguinApplication.getInstance().getDatabase().sessionDao().insertOrUpdateMember(myMember);
                PenguinApplication.getInstance().getDatabase().sessionDao().insertOrUpdateMember(hostMember);

                runOnUiThread(() -> {
                    NearbyConnectionsManager.getInstance(this).stopDiscovery();
                    TransferManager.getInstance(this).setActiveSession(session);

                    Intent intent = new Intent(JoinSessionActivity.this, SessionActivity.class);
                    intent.putExtra(SessionActivity.EXTRA_SESSION_ID, generatedSessionId);
                    startActivity(intent);
                    finish();
                });
            });
        });
    }

    @Override
    public void onDiscoveryFailed(String reason) {
        runOnUiThread(() -> {
            isConnecting = false;
            binding.layoutConnecting.setVisibility(View.GONE);
            binding.btnJoinManual.setEnabled(true);
            binding.btnScanQR.setEnabled(true);
            Toast.makeText(this, getString(R.string.err_session_not_found), Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onPeerDisconnected(String endpointId, NearbyConnectionsManager.PeerInfo peer) {}

    @Override
    public void onPhotoReceived(SharedPhoto photo) {}

    @Override
    public void onPhotoTransferStatusChanged(String photoId, SyncStatus status) {}

    @Override
    public void onSessionEndedByHost() {}

    @Override
    public void onMemberLeft(String deviceId) {}

    @Override
    protected void onDestroy() {
        NearbyConnectionsManager.getInstance(this).removeListener(this);
        super.onDestroy();
    }
}
