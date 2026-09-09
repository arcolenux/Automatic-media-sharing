package com.penguin.app.activity;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import com.penguin.app.PenguinApplication;
import com.penguin.app.R;
import com.penguin.app.adapter.GalleryAdapter;
import com.penguin.app.databinding.ActivitySessionBinding;
import com.penguin.app.databinding.DialogEndSessionBinding;
import com.penguin.app.databinding.DialogLeaveSessionBinding;
import com.penguin.app.databinding.DialogSessionInfoBinding;
import com.penguin.app.db.AppDatabase;
import com.penguin.app.model.Session;
import com.penguin.app.model.SessionMember;
import com.penguin.app.model.SharedPhoto;
import com.penguin.app.model.SyncStatus;
import com.penguin.app.service.MediaDetectionService;
import com.penguin.app.transfer.NearbyConnectionsManager;
import com.penguin.app.transfer.TransferManager;
import com.penguin.app.util.PermissionHelper;
import com.penguin.app.util.QRCodeUtil;

import java.util.List;

/**
 * Main active session screen showing Auto-Share switch, peer connectivity state,
 * and 3-column shared photo gallery.
 */
public class SessionActivity extends AppCompatActivity implements
        GalleryAdapter.OnPhotoClickListener,
        NearbyConnectionsManager.NearbyEventListener {

    public static final String EXTRA_SESSION_ID = "extra_session_id";

    private ActivitySessionBinding binding;
    private GalleryAdapter galleryAdapter;
    private String sessionId;
    private Session currentSession;
    private List<SessionMember> currentMembers;
    private boolean isAutoShareEnabled = false;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (granted == null || !granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    enableAutoShare();
                } else {
                    binding.switchAutoShare.setChecked(false);
                    Toast.makeText(this, R.string.err_missing_permissions, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySessionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        if (sessionId == null) {
            finish();
            return;
        }

        setupRecyclerView();
        setupListeners();
        observeDatabase();

        NearbyConnectionsManager.getInstance(this).addListener(this);
    }

    private void setupRecyclerView() {
        galleryAdapter = new GalleryAdapter(this);
        binding.rvGallery.setLayoutManager(new GridLayoutManager(this, 3));
        binding.rvGallery.setAdapter(galleryAdapter);
        binding.rvGallery.setHasFixedSize(true);
    }

    private void setupListeners() {
        // Auto-Share Switch Toggle
        binding.switchAutoShare.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (PermissionHelper.hasAllRequiredPermissions(this)) {
                    enableAutoShare();
                } else {
                    String[] missing = PermissionHelper.getMissingPermissions(this);
                    permissionLauncher.launch(missing);
                }
            } else {
                disableAutoShare();
            }
        });

        // Room Code Pill & QR Button Click -> Show Invitation Info Dialog
        binding.pillRoomCode.setOnClickListener(v -> showSessionInfoDialog());
        binding.btnSessionQR.setOnClickListener(v -> showSessionInfoDialog());

        // Action Button: End Session (Host) vs Leave Session (Participant)
        binding.btnSessionAction.setOnClickListener(v -> {
            if (currentSession != null && currentSession.isHost()) {
                showEndSessionDialog();
            } else {
                showLeaveSessionDialog();
            }
        });
    }

    private void observeDatabase() {
        AppDatabase db = PenguinApplication.getInstance().getDatabase();

        // 1. Observe Session Details
        db.sessionDao().getSessionByIdLive(sessionId).observe(this, session -> {
            if (session == null || !session.isActive()) {
                // Session is ended or inactive -> navigate to MainActivity
                finish();
                return;
            }
            this.currentSession = session;
            TransferManager.getInstance(this).setActiveSession(session);

            binding.tvSessionTitle.setText(session.getSessionName());
            binding.pillRoomCode.setText(session.getRoomCode());

            if (session.isHost()) {
                binding.btnSessionAction.setContentDescription(getString(R.string.end_session));
            } else {
                binding.btnSessionAction.setContentDescription(getString(R.string.leave));
            }
        });

        // 2. Observe Session Members
        db.sessionDao().getMembersForSessionLive(sessionId).observe(this, members -> {
            this.currentMembers = members;
            int totalMembers = members != null ? members.size() : 1;
            int nearbyCount = 0;
            if (members != null) {
                for (SessionMember m : members) {
                    if (m.isNearby()) nearbyCount++;
                }
            }

            String memberText = totalMembers == 1
                    ? "1 " + getString(R.string.member_singular)
                    : totalMembers + " " + getString(R.string.members_plural);
            binding.pillMemberCount.setText(memberText);

            updateConnectivityBanner(totalMembers, nearbyCount);
        });

        // 3. Observe Gallery Photos
        db.photoDao().getPhotosForSessionLive(sessionId).observe(this, photos -> {
            if (photos == null || photos.isEmpty()) {
                binding.layoutEmptyGallery.setVisibility(View.VISIBLE);
                binding.rvGallery.setVisibility(View.GONE);
                binding.tvPhotoCount.setText("0 shared");
            } else {
                binding.layoutEmptyGallery.setVisibility(View.GONE);
                binding.rvGallery.setVisibility(View.VISIBLE);
                binding.tvPhotoCount.setText(getString(R.string.photos_count_format, photos.size()));
                galleryAdapter.submitList(photos);
            }
        });
    }

    private void enableAutoShare() {
        isAutoShareEnabled = true;
        binding.tvAutoShareDesc.setText(R.string.auto_share_desc_on);
        binding.ivAutoShareIcon.setColorFilter(getColor(R.color.primary_accent));

        String sessionName = currentSession != null ? currentSession.getSessionName() : "Active Session";
        MediaDetectionService.start(this, sessionId, sessionName);
    }

    private void disableAutoShare() {
        isAutoShareEnabled = false;
        binding.tvAutoShareDesc.setText(R.string.auto_share_desc_off);
        binding.ivAutoShareIcon.setColorFilter(getColor(R.color.text_secondary));

        MediaDetectionService.stop(this);
    }

    private void updateConnectivityBanner(int totalMembers, int nearbyCount) {
        if (totalMembers <= 1 || nearbyCount == 0) {
            binding.layoutNearbyStatusBanner.setVisibility(View.VISIBLE);
            binding.tvBannerTitle.setText(R.string.waiting_for_nearby_title);
            binding.tvBannerDesc.setText(R.string.waiting_for_nearby_desc);
            binding.ivBannerIcon.setImageResource(R.drawable.ic_sync);
        } else if (nearbyCount < totalMembers) {
            binding.layoutNearbyStatusBanner.setVisibility(View.VISIBLE);
            binding.tvBannerTitle.setText(getString(R.string.members_nearby_format, nearbyCount, totalMembers));
            binding.tvBannerDesc.setText(R.string.waiting_for_nearby_desc);
            binding.ivBannerIcon.setImageResource(R.drawable.ic_group);
        } else {
            binding.layoutNearbyStatusBanner.setVisibility(View.GONE);
        }
    }

    // ==========================================
    // Dialogs
    // ==========================================

    private void showEndSessionDialog() {
        Dialog dialog = new Dialog(this);
        DialogEndSessionBinding dialogBinding = DialogEndSessionBinding.inflate(getLayoutInflater());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogBinding.getRoot());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnConfirmEnd.setOnClickListener(v -> {
            dialog.dismiss();
            endSession();
        });

        dialog.show();
    }

    private void endSession() {
        disableAutoShare();
        TransferManager.getInstance(this).endCurrentSession();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            PenguinApplication.getInstance().getDatabase().sessionDao()
                    .deactivateAllSessions(System.currentTimeMillis());

            runOnUiThread(this::finish);
        });
    }

    private void showLeaveSessionDialog() {
        Dialog dialog = new Dialog(this);
        DialogLeaveSessionBinding dialogBinding = DialogLeaveSessionBinding.inflate(getLayoutInflater());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogBinding.getRoot());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        dialogBinding.btnStay.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnConfirmLeave.setOnClickListener(v -> {
            dialog.dismiss();
            leaveSession();
        });

        dialog.show();
    }

    private void leaveSession() {
        disableAutoShare();
        TransferManager.getInstance(this).leaveCurrentSession();

        String myDeviceId = PenguinApplication.getInstance().getAppDeviceId();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            PenguinApplication.getInstance().getDatabase().sessionDao()
                    .markMemberLeft(sessionId, myDeviceId, System.currentTimeMillis());
            PenguinApplication.getInstance().getDatabase().sessionDao()
                    .deactivateAllSessions(System.currentTimeMillis());

            runOnUiThread(this::finish);
        });
    }

    private void showSessionInfoDialog() {
        if (currentSession == null) return;

        Dialog dialog = new Dialog(this);
        DialogSessionInfoBinding dialogBinding = DialogSessionInfoBinding.inflate(getLayoutInflater());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogBinding.getRoot());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        dialogBinding.tvDialogSessionName.setText(currentSession.getSessionName());
        dialogBinding.tvDialogRoomCode.setText(currentSession.getRoomCode());

        try {
            Bitmap qr = QRCodeUtil.generateQRCodeBitmap(currentSession.getRoomCode(), 360, 360);
            dialogBinding.ivDialogQRCode.setImageBitmap(qr);
        } catch (Exception ignored) {
        }

        dialogBinding.btnDialogCopyCode.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("PENGUIN Room Code", currentSession.getRoomCode());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Room code copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        dialogBinding.btnDialogClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // ==========================================
    // Gallery Adapter Click Callbacks
    // ==========================================

    @Override
    public void onPhotoClick(SharedPhoto photo) {
        // Open full photo preview or show details
    }

    @Override
    public void onRetryClick(SharedPhoto photo) {
        Toast.makeText(this, "Retrying photo transfer…", Toast.LENGTH_SHORT).show();
        TransferManager.getInstance(this).retryPhoto(photo.getPhotoId());
    }

    // ==========================================
    // Nearby Event Callbacks
    // ==========================================

    @Override
    public void onPeerConnected(NearbyConnectionsManager.PeerInfo peer) {
        runOnUiThread(() -> {
            Toast.makeText(this, peer.displayName + " joined nearby", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onPeerDisconnected(String endpointId, NearbyConnectionsManager.PeerInfo peer) {
        runOnUiThread(() -> {
            if (peer != null) {
                Toast.makeText(this, peer.displayName + " is temporarily out of range", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onPhotoReceived(SharedPhoto photo) {
        runOnUiThread(() -> {
            Toast.makeText(this, "New photo received from " + photo.getOwnerName(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onPhotoTransferStatusChanged(String photoId, SyncStatus status) {}

    @Override
    public void onSessionEndedByHost() {
        runOnUiThread(() -> {
            Toast.makeText(this, "Session ended by the host", Toast.LENGTH_LONG).show();
            finish();
        });
    }

    @Override
    public void onMemberLeft(String deviceId) {
        runOnUiThread(() -> {
            Toast.makeText(this, "A member left the session", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDiscoveryFailed(String reason) {}

    @Override
    public void onHostDiscovered(String endpointId, String roomCode) {}

    @Override
    protected void onDestroy() {
        NearbyConnectionsManager.getInstance(this).removeListener(this);
        super.onDestroy();
    }
}
