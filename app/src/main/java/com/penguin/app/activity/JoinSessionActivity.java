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
import com.penguin.app.util.PortraitCaptureActivity;
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
    private String pendingRoomCode;
    private boolean isConnecting = false;

    private final android.os.Handler searchTimeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable searchingAdviceRunnable = () -> {
        if (isConnecting && binding != null) {
            binding.tvConnectingStatus.setText("Still searching for session " + (targetRoomCode != null ? targetRoomCode : "") + "…\nMake sure Bluetooth & Wi-Fi are ON and devices are close by.");
        }
    };
    private final Runnable searchTimeoutRunnable = () -> {
        if (isConnecting) {
            onDiscoveryFailed("Search timed out");
        }
    };

    // Nearby / Location Permission Launcher
    private final ActivityResultLauncher<String[]> nearbyPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (granted == null || !granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    if (pendingRoomCode != null && !pendingRoomCode.isEmpty()) {
                        String code = pendingRoomCode;
                        pendingRoomCode = null;
                        joinWithRoomCode(code);
                    }
                } else {
                    Toast.makeText(this, R.string.err_missing_permissions, Toast.LENGTH_SHORT).show();
                    pendingRoomCode = null;
                }
            });

    // ZXing QR Code Scanner Launcher
    private final ActivityResultLauncher<ScanOptions> qrScanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String extractedCode = QRCodeUtil.extractRoomCode(result.getContents());
                    if (extractedCode != null) {
                        binding.etRoomCode.setText(extractedCode);
                        validateNameAndJoin(extractedCode);
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

        String savedName = PenguinApplication.getInstance().getUserName();
        if (savedName != null) {
            binding.etJoinerName.setText(savedName);
        }

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
                validateNameAndJoin(code);
            }
        }
    }

    private void setupListeners() {
        binding.btnScanQR.setOnClickListener(v -> {
            String joinerName = binding.etJoinerName.getText() != null
                    ? binding.etJoinerName.getText().toString().trim()
                    : "";
            if (joinerName.isEmpty()) {
                binding.layoutJoinerName.setError(getString(R.string.err_name_empty));
                return;
            }
            binding.layoutJoinerName.setError(null);
            PenguinApplication.getInstance().setUserName(joinerName);

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

            validateNameAndJoin(RoomCodeGenerator.normalize(codeInput));
        });
    }

    private void validateNameAndJoin(String roomCode) {
        String joinerName = binding.etJoinerName.getText() != null
                ? binding.etJoinerName.getText().toString().trim()
                : "";
        if (joinerName.isEmpty()) {
            binding.layoutJoinerName.setError(getString(R.string.err_name_empty));
            return;
        }
        binding.layoutJoinerName.setError(null);
        PenguinApplication.getInstance().setUserName(joinerName);

        if (!PermissionHelper.hasNearbyPermissions(this)) {
            pendingRoomCode = roomCode;
            nearbyPermissionLauncher.launch(PermissionHelper.getRequiredNearbyPermissions());
            return;
        }

        joinWithRoomCode(roomCode);
    }

    private void launchQRScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan Penguin QR code (works from any angle)");
        options.setBeepEnabled(false);
        options.setOrientationLocked(true);
        options.setCaptureActivity(PortraitCaptureActivity.class);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        qrScanLauncher.launch(options);
    }

    private void joinWithRoomCode(String roomCode) {
        this.targetRoomCode = roomCode;
        isConnecting = true;

        cancelSearchTimers();

        binding.layoutConnecting.setVisibility(View.VISIBLE);
        binding.tvConnectingStatus.setText("Searching for nearby host with code " + roomCode + "…");
        binding.btnJoinManual.setEnabled(false);
        binding.btnScanQR.setEnabled(false);

        // Schedule progressive advice after 8s and timeout after 25s
        searchTimeoutHandler.postDelayed(searchingAdviceRunnable, 8000);
        searchTimeoutHandler.postDelayed(searchTimeoutRunnable, 25000);

        TransferManager.getInstance(this).startJoiningSession(roomCode);
    }

    private void cancelSearchTimers() {
        searchTimeoutHandler.removeCallbacks(searchingAdviceRunnable);
        searchTimeoutHandler.removeCallbacks(searchTimeoutRunnable);
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
        isConnecting = false;
        cancelSearchTimers();

        runOnUiThread(() -> {
            String myDeviceId = PenguinApplication.getInstance().getAppDeviceId();
            String myName = PenguinApplication.getInstance().getUserName();
            
            String canonicalSessionId = (peer.sessionId != null && !peer.sessionId.isEmpty())
                    ? peer.sessionId
                    : (NearbyConnectionsManager.getInstance(this).getCurrentSessionId() != null && !NearbyConnectionsManager.getInstance(this).getCurrentSessionId().isEmpty()
                            ? NearbyConnectionsManager.getInstance(this).getCurrentSessionId()
                            : UUID.randomUUID().toString());

            String canonicalSessionName = (peer.sessionName != null && !peer.sessionName.isEmpty())
                    ? peer.sessionName
                    : (NearbyConnectionsManager.getInstance(this).getCurrentSessionName() != null && !NearbyConnectionsManager.getInstance(this).getCurrentSessionName().isEmpty()
                            ? NearbyConnectionsManager.getInstance(this).getCurrentSessionName()
                            : "Session " + targetRoomCode);

            NearbyConnectionsManager.getInstance(this).setCurrentSessionId(canonicalSessionId);
            NearbyConnectionsManager.getInstance(this).setCurrentSessionName(canonicalSessionName);

            Session session = new Session(
                    canonicalSessionId,
                    canonicalSessionName,
                    targetRoomCode,
                    peer.deviceId,
                    System.currentTimeMillis(),
                    0,
                    true,
                    2,
                    false
            );

            SessionMember myMember = new SessionMember(
                    canonicalSessionId,
                    myDeviceId,
                    myName,
                    "ME",
                    true,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    false
            );

            SessionMember hostMember = new SessionMember(
                    canonicalSessionId,
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
                PenguinApplication.getInstance().getDatabase().sessionDao().deletePlaceholderMembers(canonicalSessionId);
                PenguinApplication.getInstance().getDatabase().sessionDao().insertOrUpdateMember(myMember);
                if (peer.deviceId != null && !peer.deviceId.startsWith("PEER-") && !peer.deviceId.equals(myDeviceId)) {
                    PenguinApplication.getInstance().getDatabase().sessionDao().insertOrUpdateMember(hostMember);
                }

                runOnUiThread(() -> {
                    NearbyConnectionsManager.getInstance(this).stopDiscovery();
                    TransferManager.getInstance(this).setActiveSession(session);

                    Intent intent = new Intent(JoinSessionActivity.this, SessionActivity.class);
                    intent.putExtra(SessionActivity.EXTRA_SESSION_ID, canonicalSessionId);
                    startActivity(intent);
                    finish();
                });
            });
        });
    }

    @Override
    public void onDiscoveryFailed(String reason) {
        cancelSearchTimers();
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
        cancelSearchTimers();
        NearbyConnectionsManager.getInstance(this).removeListener(this);
        super.onDestroy();
    }
}
