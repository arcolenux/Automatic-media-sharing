package com.penguin.app.util;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.penguin.app.PenguinApplication;
import com.penguin.app.R;

public class UserNameHelper {

    public interface OnNameUpdatedListener {
        void onNameUpdated(String newName);
    }

    public static void showEditNameDialog(Activity activity, OnNameUpdatedListener listener) {
        if (activity == null || activity.isFinishing()) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_edit_name, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.90),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        TextInputLayout layoutEditName = view.findViewById(R.id.layoutEditName);
        TextInputEditText etEditName = view.findViewById(R.id.etEditName);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancelName);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveName);

        String currentName = PenguinApplication.getInstance().getUserName();
        if (currentName != null) {
            etEditName.setText(currentName);
            etEditName.setSelection(currentName.length());
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String input = etEditName.getText() != null ? etEditName.getText().toString().trim() : "";
            if (input.isEmpty()) {
                layoutEditName.setError(activity.getString(R.string.err_name_empty));
                return;
            }

            layoutEditName.setError(null);
            PenguinApplication.getInstance().setUserName(input);
            Toast.makeText(activity, activity.getString(R.string.name_updated_toast, input), Toast.LENGTH_SHORT).show();

            dialog.dismiss();

            if (listener != null) {
                listener.onNameUpdated(input);
            }
        });

        dialog.show();
    }

    public static void showEditPeerNicknameDialog(Activity activity, String deviceId, String defaultDisplayName, OnNameUpdatedListener listener) {
        if (activity == null || activity.isFinishing() || deviceId == null) return;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_edit_member_nickname, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.90),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        TextView tvOriginal = view.findViewById(R.id.tvOriginalNameHint);
        TextInputLayout layoutNickname = view.findViewById(R.id.layoutNickname);
        TextInputEditText etNickname = view.findViewById(R.id.etNickname);
        MaterialButton btnReset = view.findViewById(R.id.btnResetNickname);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancelNickname);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveNickname);

        tvOriginal.setText("Original Name: " + (defaultDisplayName != null && !defaultDisplayName.isEmpty() ? defaultDisplayName : "Unknown"));

        String currentCustom = PenguinApplication.getInstance().getPeerCustomNickname(deviceId);
        if (currentCustom != null && !currentCustom.isEmpty()) {
            etNickname.setText(currentCustom);
            etNickname.setSelection(currentCustom.length());
            btnReset.setVisibility(View.VISIBLE);
        } else {
            if (defaultDisplayName != null) {
                etNickname.setText(defaultDisplayName);
                etNickname.setSelection(defaultDisplayName.length());
            }
            btnReset.setVisibility(View.GONE);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnReset.setOnClickListener(v -> {
            PenguinApplication.getInstance().setPeerCustomNickname(deviceId, null);
            Toast.makeText(activity, "Reset nickname to " + defaultDisplayName, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            if (listener != null) {
                listener.onNameUpdated(defaultDisplayName);
            }
        });

        btnSave.setOnClickListener(v -> {
            String input = etNickname.getText() != null ? etNickname.getText().toString().trim() : "";
            if (input.isEmpty()) {
                layoutNickname.setError("Please enter a name");
                return;
            }

            layoutNickname.setError(null);
            PenguinApplication.getInstance().setPeerCustomNickname(deviceId, input);
            Toast.makeText(activity, "Name set to " + input, Toast.LENGTH_SHORT).show();

            dialog.dismiss();

            if (listener != null) {
                listener.onNameUpdated(input);
            }
        });

        dialog.show();
    }
}
