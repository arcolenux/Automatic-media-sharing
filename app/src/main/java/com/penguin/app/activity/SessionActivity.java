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
import com.penguin.app.util.UserNameHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Main active session screen showing Auto-Share switch, peer connectivity state,
 * and 3-column shared photo gallery.
 */
public class SessionActivity extends AppCompatActivity implements
        GalleryAdapter.OnPhotoClickListener,
        NearbyConnectionsManager.NearbyEventListener {

    public static final String EXTRA_SESSION_ID = "extra_session_id";
    private static final String PREFS_NAME = "penguin_session_prefs";
    private static final String KEY_AUTO_SHARE_PREFIX = "auto_share_enabled_";

    private ActivitySessionBinding binding;
    private GalleryAdapter galleryAdapter;
    private String sessionId;
    private Session currentSession;
    private List<SessionMember> currentMembers;
    private boolean isAutoShareEnabled = false;
    private boolean isExitingSession = false;

    private Toast activeJoinToast;
    private final List<String> pendingJoinedNames = new ArrayList<>();
    private final android.os.Handler joinToastHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable joinToastRunnable = this::flushJoinToast;

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
        if (sessionId == null || sessionId.isEmpty()) {
            // Fallback: check database for current active session
            AppDatabase.databaseWriteExecutor.execute(() -> {
                Session activeSession = PenguinApplication.getInstance().getDatabase().sessionDao().getActiveSession();
                if (activeSession != null && activeSession.isActive()) {
                    runOnUiThread(() -> {
                        this.sessionId = activeSession.getSessionId();
                        observeDatabase();
                    });
                } else {
                    runOnUiThread(this::navigateToMainActivity);
                }
            });
        }

        setupRecyclerView();
        setupListeners();
        setupBackPressHandling();
        if (sessionId != null && !sessionId.isEmpty()) {
            observeDatabase();
        }

        NearbyConnectionsManager.getInstance(this).addListener(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getStringExtra(EXTRA_SESSION_ID) != null) {
            String newSessionId = intent.getStringExtra(EXTRA_SESSION_ID);
            if (!newSessionId.equals(this.sessionId)) {
                this.sessionId = newSessionId;
                observeDatabase();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentSession != null && currentSession.isActive()) {
            ensureNearbyConnectionActive(currentSession);
        }
    }

    private void ensureNearbyConnectionActive(Session session) {
        if (session == null || !session.isActive()) return;
        NearbyConnectionsManager nearbyManager = NearbyConnectionsManager.getInstance(this);
        if (session.isHost()) {
            if (!nearbyManager.isAdvertising()) {
                nearbyManager.startAdvertising(session);
            }
        } else {
            if (!nearbyManager.isDiscovering() && !nearbyManager.isConnectedToAnyPeer()) {
                nearbyManager.startDiscovery(session.getRoomCode());
            }
        }
    }

    private void setupBackPressHandling() {
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // When in an active session, back press minimizes the app to phone home screen
                // rather than going back to Create/Join session screen.
                moveTaskToBack(true);
            }
        });
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

        // Member Count Pill Click -> Show Session Members List Dialog
        binding.pillMemberCount.setOnClickListener(v -> showMembersListDialog());

        // Edit Profile Name in Session
        binding.btnEditMyName.setOnClickListener(v -> {
            UserNameHelper.showEditNameDialog(this, newName -> {
                if (sessionId != null) {
                    String myDeviceId = PenguinApplication.getInstance().getAppDeviceId();
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        PenguinApplication.getInstance().getDatabase().sessionDao()
                                .updateMemberDisplayName(sessionId, myDeviceId, newName);
                        NearbyConnectionsManager.getInstance(SessionActivity.this).broadcastCurrentMemberRoster();
                    });
                }
            });
        });

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
            if (session == null) {
                return;
            }
            if (!session.isActive()) {
                // Session is ended or inactive -> navigate to MainActivity smoothly
                navigateToMainActivity();
                return;
            }
            this.currentSession = session;
            TransferManager.getInstance(this).setActiveSession(session);
            ensureNearbyConnectionActive(session);

            binding.tvSessionTitle.setText(session.getSessionName());
            binding.pillRoomCode.setText(session.getRoomCode());

            if (session.isHost()) {
                binding.btnSessionAction.setContentDescription(getString(R.string.end_session));
            } else {
                binding.btnSessionAction.setContentDescription(getString(R.string.leave));
            }

            // Restore Auto-Share Switch state
            boolean savedAutoShare = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getBoolean(KEY_AUTO_SHARE_PREFIX + sessionId, false);
            this.isAutoShareEnabled = savedAutoShare;
            if (binding.switchAutoShare.isChecked() != savedAutoShare) {
                binding.switchAutoShare.setChecked(savedAutoShare);
            }
            updateAutoShareUI(savedAutoShare);

            // Keep background foreground service running for the entire active session
            MediaDetectionService.start(this, session.getSessionId(), session.getSessionName(), savedAutoShare);
        });

        // 2. Observe Session Members
        db.sessionDao().getMembersForSessionLive(sessionId).observe(this, members -> {
            this.currentMembers = members;
            int totalMembers = members != null ? members.size() : 1;

            String memberText = totalMembers == 1
                    ? "1 " + getString(R.string.member_singular)
                    : totalMembers + " " + getString(R.string.members_plural);
            binding.pillMemberCount.setText(memberText);

            updateConnectivityBanner(members);
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
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_SHARE_PREFIX + sessionId, true)
                .apply();

        if (!binding.switchAutoShare.isChecked()) {
            binding.switchAutoShare.setChecked(true);
        }

        updateAutoShareUI(true);

        // Request battery optimization exemption for background continuity
        if (!PermissionHelper.isIgnoringBatteryOptimizations(this)) {
            PermissionHelper.requestIgnoreBatteryOptimizations(this);
        }

        String sessionName = currentSession != null ? currentSession.getSessionName() : "Active Session";
        MediaDetectionService.start(this, sessionId, sessionName, true);
    }

    private void disableAutoShare() {
        isAutoShareEnabled = false;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_SHARE_PREFIX + sessionId, false)
                .apply();

        if (binding.switchAutoShare.isChecked()) {
            binding.switchAutoShare.setChecked(false);
        }

        updateAutoShareUI(false);
        MediaDetectionService.setAutoShareEnabled(this, false);
    }

    private void updateAutoShareUI(boolean isEnabled) {
        if (isEnabled) {
            binding.tvAutoShareDesc.setText(R.string.auto_share_desc_on);
            binding.ivAutoShareIcon.setColorFilter(getColor(R.color.primary_accent));
            binding.cardAutoShare.setStrokeColor(getColor(R.color.outline_focused));
        } else {
            binding.tvAutoShareDesc.setText(R.string.auto_share_desc_off);
            binding.ivAutoShareIcon.setColorFilter(getColor(R.color.text_tertiary));
            binding.cardAutoShare.setStrokeColor(getColor(R.color.outline));
        }
    }

    private void updateConnectivityBanner(List<SessionMember> members) {
        String myDeviceId = PenguinApplication.getInstance().getAppDeviceId();
        java.util.Set<String> connectedDeviceIds =
                NearbyConnectionsManager.getInstance(this).getConnectedDeviceIds();

        List<String> awayMemberNames = new java.util.ArrayList<>();
        int totalOtherMembers = 0;

        if (members != null) {
            for (SessionMember m : members) {
                if (m.getDeviceId() != null && !m.getDeviceId().equals(myDeviceId) && !m.isHasLeft()) {
                    totalOtherMembers++;
                    boolean isNearby = connectedDeviceIds.contains(m.getDeviceId())
                            || (NearbyConnectionsManager.getInstance(this).isConnectedToAnyPeer() && m.isNearby());
                    if (!isNearby) {
                        String rawName = m.getDisplayName() != null && !m.getDisplayName().isEmpty()
                                ? m.getDisplayName()
                                : "Peer " + m.getDeviceId().substring(Math.max(0, m.getDeviceId().length() - 4));
                        String effectiveName = PenguinApplication.getInstance().getEffectiveMemberName(m.getDeviceId(), rawName);
                        awayMemberNames.add(effectiveName);
                    }
                }
            }
        }

        if (totalOtherMembers == 0) {
            binding.layoutNearbyStatusBanner.setVisibility(View.VISIBLE);
            binding.tvBannerTitle.setText(R.string.waiting_for_nearby_title);
            binding.tvBannerDesc.setText(R.string.waiting_for_nearby_desc);
            binding.ivBannerIcon.setImageResource(R.drawable.ic_sync);
        } else if (awayMemberNames.isEmpty()) {
            binding.layoutNearbyStatusBanner.setVisibility(View.GONE);
        } else {
            binding.layoutNearbyStatusBanner.setVisibility(View.VISIBLE);
            if (awayMemberNames.size() == totalOtherMembers && connectedDeviceIds.isEmpty()) {
                String awayNamesStr = android.text.TextUtils.join(", ", awayMemberNames);
                binding.tvBannerTitle.setText(getString(R.string.out_of_range_multiple, awayNamesStr));
                binding.tvBannerDesc.setText("Move closer to other participants to resume syncing.");
            } else if (awayMemberNames.size() == 1) {
                binding.tvBannerTitle.setText(getString(R.string.out_of_range_single, awayMemberNames.get(0)));
                binding.tvBannerDesc.setText(R.string.out_of_range_desc);
            } else {
                String awayNamesStr = android.text.TextUtils.join(", ", awayMemberNames);
                binding.tvBannerTitle.setText(getString(R.string.out_of_range_multiple, awayNamesStr));
                binding.tvBannerDesc.setText(R.string.out_of_range_desc);
            }
            binding.ivBannerIcon.setImageResource(R.drawable.ic_group);
        }
    }

    private void showMembersListDialog() {
        if (currentSession == null) return;

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        com.penguin.app.databinding.DialogMembersListBinding dialogBinding =
                com.penguin.app.databinding.DialogMembersListBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            int dialogWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            dialog.getWindow().setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        dialogBinding.tvDialogMembersTitle.setText("Session Members");
        int count = currentMembers != null ? currentMembers.size() : 0;
        dialogBinding.tvDialogMembersSubtitle.setText(count + (count == 1 ? " participant" : " participants"));

        String myDeviceId = PenguinApplication.getInstance().getAppDeviceId();
        java.util.Set<String> connectedDeviceIds =
                NearbyConnectionsManager.getInstance(this).getConnectedDeviceIds();

        dialogBinding.layoutMembersListContainer.removeAllViews();

        if (currentMembers != null) {
            for (SessionMember member : currentMembers) {
                View itemView = getLayoutInflater().inflate(R.layout.item_session_member_dialog, dialogBinding.layoutMembersListContainer, false);

                TextView tvName = itemView.findViewById(R.id.tvMemberName);
                TextView tvOriginalName = itemView.findViewById(R.id.tvOriginalName);
                TextView tvStatus = itemView.findViewById(R.id.tvMemberStatus);
                TextView pillStatus = itemView.findViewById(R.id.pillMemberStatus);
                TextView badgeHost = itemView.findViewById(R.id.badgeHost);
                View dotStatus = itemView.findViewById(R.id.dotStatus);
                ImageView btnEditMemberNickname = itemView.findViewById(R.id.btnEditMemberNickname);

                boolean isMe = member.getDeviceId() != null && member.getDeviceId().equals(myDeviceId);
                String rawName = member.getDisplayName() != null && !member.getDisplayName().isEmpty()
                        ? member.getDisplayName()
                        : "Peer " + (member.getDeviceId() != null ? member.getDeviceId().substring(Math.max(0, member.getDeviceId().length() - 4)) : "");

                String effectiveName = isMe ? rawName : PenguinApplication.getInstance().getEffectiveMemberName(member.getDeviceId(), rawName);
                String customNickname = isMe ? null : PenguinApplication.getInstance().getPeerCustomNickname(member.getDeviceId());

                tvName.setText(isMe ? effectiveName + " (You)" : effectiveName);

                if (!isMe && customNickname != null && !customNickname.isEmpty()) {
                    tvOriginalName.setVisibility(View.VISIBLE);
                    tvOriginalName.setText("Original: " + rawName);
                } else {
                    tvOriginalName.setVisibility(View.GONE);
                }

                if (isMe) {
                    btnEditMemberNickname.setVisibility(View.GONE);
                } else {
                    btnEditMemberNickname.setVisibility(View.VISIBLE);
                    View.OnClickListener editNicknameListener = v -> {
                        UserNameHelper.showEditPeerNicknameDialog(SessionActivity.this, member.getDeviceId(), rawName, savedNickname -> {
                            dialog.dismiss();
                            showMembersListDialog();
                            updateConnectivityBanner(currentMembers);
                        });
                    };
                    btnEditMemberNickname.setOnClickListener(editNicknameListener);
                    itemView.setOnClickListener(editNicknameListener);
                }

                boolean isHost = currentSession != null && currentSession.getHostDeviceId() != null
                        && currentSession.getHostDeviceId().equals(member.getDeviceId());
                badgeHost.setVisibility(isHost ? View.VISIBLE : View.GONE);

                boolean isNearby = isMe || connectedDeviceIds.contains(member.getDeviceId());

                if (member.isHasLeft()) {
                    tvStatus.setText("Left session");
                    pillStatus.setText("Left");
                    pillStatus.setTextColor(getColor(R.color.status_queued));
                    dotStatus.setBackgroundResource(R.drawable.bg_dot_queued);
                } else if (isNearby) {
                    tvStatus.setText(isMe ? "Active on this device" : "In proximity (Connected)");
                    pillStatus.setText("Nearby");
                    pillStatus.setTextColor(getColor(R.color.status_synced));
                    dotStatus.setBackgroundResource(R.drawable.bg_dot_synced);
                } else {
                    tvStatus.setText("Out of radio range");
                    pillStatus.setText("Away");
                    pillStatus.setTextColor(getColor(R.color.status_syncing));
                    dotStatus.setBackgroundResource(R.drawable.bg_dot_syncing);
                }

                dialogBinding.layoutMembersListContainer.addView(itemView);
            }
        }

        dialogBinding.btnDialogMembersClose.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnDialogDone.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // ==========================================
    // Dialogs & Exit Handling
    // ==========================================

    private void navigateToMainActivity() {
        disableAutoShare();
        MediaDetectionService.stop(this);
        Intent intent = new Intent(SessionActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void showEndSessionDialog() {
        Dialog dialog = new Dialog(this);
        DialogEndSessionBinding dialogBinding = DialogEndSessionBinding.inflate(getLayoutInflater());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogBinding.getRoot());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            int dialogWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.88);
            dialog.getWindow().setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnConfirmEnd.setOnClickListener(v -> {
            dialog.dismiss();
            endSession();
        });

        dialog.show();
    }

    private void endSession() {
        if (isExitingSession) return;
        isExitingSession = true;
        disableAutoShare();
        MediaDetectionService.stop(this);
        TransferManager.getInstance(this).endCurrentSession();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            PenguinApplication.getInstance().getDatabase().sessionDao()
                    .deactivateAllSessions(System.currentTimeMillis());

            runOnUiThread(() -> {
                Intent intent = new Intent(SessionActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        });
    }

    private void showLeaveSessionDialog() {
        Dialog dialog = new Dialog(this);
        DialogLeaveSessionBinding dialogBinding = DialogLeaveSessionBinding.inflate(getLayoutInflater());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogBinding.getRoot());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            int dialogWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.88);
            dialog.getWindow().setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        dialogBinding.btnStay.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnConfirmLeave.setOnClickListener(v -> {
            dialog.dismiss();
            leaveSession();
        });

        dialog.show();
    }

    private void leaveSession() {
        if (isExitingSession) return;
        isExitingSession = true;
        disableAutoShare();
        MediaDetectionService.stop(this);
        NearbyConnectionsManager.getInstance(this).sendLeaveToAll();
        TransferManager.getInstance(this).leaveCurrentSession();

        String myDeviceId = PenguinApplication.getInstance().getAppDeviceId();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            PenguinApplication.getInstance().getDatabase().sessionDao()
                    .markMemberLeft(sessionId, myDeviceId, System.currentTimeMillis());
            PenguinApplication.getInstance().getDatabase().sessionDao()
                    .deactivateAllSessions(System.currentTimeMillis());

            runOnUiThread(() -> {
                Intent intent = new Intent(SessionActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
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
            int dialogWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.88);
            dialog.getWindow().setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
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
            String displayName = PenguinApplication.getInstance().getEffectiveMemberName(peer.deviceId, peer.displayName);
            if (displayName != null && !displayName.trim().isEmpty() && !pendingJoinedNames.contains(displayName)) {
                pendingJoinedNames.add(displayName);
            }
            joinToastHandler.removeCallbacks(joinToastRunnable);
            joinToastHandler.postDelayed(joinToastRunnable, 1200);
            updateConnectivityBanner(currentMembers);
        });
    }

    private void flushJoinToast() {
        if (isFinishing() || isDestroyed() || pendingJoinedNames.isEmpty()) {
            return;
        }

        String message;
        int count = pendingJoinedNames.size();
        if (count == 1) {
            message = getString(R.string.member_joined_toast, pendingJoinedNames.get(0));
        } else if (count == 2) {
            message = pendingJoinedNames.get(0) + " and " + pendingJoinedNames.get(1) + " joined the session";
        } else if (count == 3) {
            message = pendingJoinedNames.get(0) + ", " + pendingJoinedNames.get(1) + ", and " + pendingJoinedNames.get(2) + " joined the session";
        } else {
            message = pendingJoinedNames.get(0) + ", " + pendingJoinedNames.get(1) + ", " + pendingJoinedNames.get(2) + ", and " + (count - 3) + " others joined the session";
        }
        pendingJoinedNames.clear();

        if (activeJoinToast != null) {
            activeJoinToast.cancel();
        }
        activeJoinToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        activeJoinToast.show();
    }

    @Override
    public void onPeerDisconnected(String endpointId, NearbyConnectionsManager.PeerInfo peer) {
        runOnUiThread(() -> {
            updateConnectivityBanner(currentMembers);
        });
    }

    @Override
    public void onPhotoReceived(SharedPhoto photo) {
        runOnUiThread(() -> {
            String ownerName = PenguinApplication.getInstance().getEffectiveMemberName(photo.getOwnerDeviceId(), photo.getOwnerName());
            Toast.makeText(this, "New photo received from " + ownerName, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onPhotoTransferStatusChanged(String photoId, SyncStatus status) {}

    @Override
    public void onSessionEndedByHost() {
        runOnUiThread(() -> {
            MediaDetectionService.stop(this);
            Toast.makeText(this, "Session ended by the host", Toast.LENGTH_LONG).show();
            navigateToMainActivity();
        });
    }

    @Override
    public void onMemberLeft(String deviceId) {
        runOnUiThread(() -> {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                if (sessionId != null) {
                    SessionMember member = PenguinApplication.getInstance().getDatabase().sessionDao()
                            .getMember(sessionId, deviceId);
                    if (member != null) {
                        String rawName = member.getDisplayName() != null ? member.getDisplayName() : "A member";
                        String effectiveName = PenguinApplication.getInstance().getEffectiveMemberName(deviceId, rawName);
                        runOnUiThread(() -> {
                            Toast.makeText(SessionActivity.this, getString(R.string.member_left_toast, effectiveName), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
            updateConnectivityBanner(currentMembers);
        });
    }

    @Override
    public void onDiscoveryFailed(String reason) {}

    @Override
    public void onHostDiscovered(String endpointId, String roomCode) {}

    @Override
    protected void onDestroy() {
        joinToastHandler.removeCallbacks(joinToastRunnable);
        if (activeJoinToast != null) {
            activeJoinToast.cancel();
            activeJoinToast = null;
        }
        NearbyConnectionsManager.getInstance(this).removeListener(this);
        super.onDestroy();
    }
}
