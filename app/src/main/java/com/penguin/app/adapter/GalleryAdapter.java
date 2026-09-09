package com.penguin.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.penguin.app.R;
import com.penguin.app.databinding.ItemGalleryPhotoBinding;
import com.penguin.app.model.SharedPhoto;
import com.penguin.app.model.SyncStatus;

import java.io.File;
import java.util.Objects;

/**
 * RecyclerView adapter for displaying shared photos in a 3-column grid with DiffUtil and Glide.
 */
public class GalleryAdapter extends ListAdapter<SharedPhoto, GalleryAdapter.PhotoViewHolder> {

    public interface OnPhotoClickListener {
        void onPhotoClick(SharedPhoto photo);
        void onRetryClick(SharedPhoto photo);
    }

    private final OnPhotoClickListener listener;

    public GalleryAdapter(OnPhotoClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<SharedPhoto> DIFF_CALLBACK = new DiffUtil.ItemCallback<SharedPhoto>() {
        @Override
        public boolean areItemsTheSame(@NonNull SharedPhoto oldItem, @NonNull SharedPhoto newItem) {
            return oldItem.getPhotoId().equals(newItem.getPhotoId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull SharedPhoto oldItem, @NonNull SharedPhoto newItem) {
            return oldItem.equals(newItem);
        }
    };

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemGalleryPhotoBinding binding = ItemGalleryPhotoBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new PhotoViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        SharedPhoto photo = getItem(position);
        holder.bind(photo, listener);
    }

    public static class PhotoViewHolder extends RecyclerView.ViewHolder {
        private final ItemGalleryPhotoBinding binding;

        public PhotoViewHolder(ItemGalleryPhotoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(SharedPhoto photo, OnPhotoClickListener listener) {
            if (photo == null) return;

            // Load local image file using Glide
            if (photo.getLocalFilePath() != null) {
                File file = new File(photo.getLocalFilePath());
                Glide.with(binding.ivPhoto.getContext())
                        .load(file)
                        .placeholder(R.color.surface_elevated)
                        .error(R.drawable.ic_warning)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .centerCrop()
                        .into(binding.ivPhoto);
            }

            // Owner name
            String owner = photo.getOwnerName();
            if (owner != null && !owner.trim().isEmpty()) {
                binding.layoutOwnerTag.setVisibility(View.VISIBLE);
                binding.tvOwnerName.setText(owner);
            } else {
                binding.layoutOwnerTag.setVisibility(View.GONE);
            }

            // Sync Status Indicator Dot & Retry Overlay
            SyncStatus status = photo.getSyncStatus();
            if (status == null) status = SyncStatus.QUEUED;

            switch (status) {
                case SYNCING:
                    binding.viewSyncDot.setBackgroundResource(R.drawable.bg_dot_syncing);
                    binding.overlayRetry.setVisibility(View.GONE);
                    break;
                case SYNCED:
                    binding.viewSyncDot.setBackgroundResource(R.drawable.bg_dot_synced);
                    binding.overlayRetry.setVisibility(View.GONE);
                    break;
                case FAILED:
                    binding.viewSyncDot.setBackgroundResource(R.drawable.bg_dot_failed);
                    binding.overlayRetry.setVisibility(View.VISIBLE);
                    break;
                case QUEUED:
                case CANCELLED:
                default:
                    binding.viewSyncDot.setBackgroundResource(R.drawable.bg_dot_queued);
                    binding.overlayRetry.setVisibility(View.GONE);
                    break;
            }

            // Click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    if (photo.getSyncStatus() == SyncStatus.FAILED) {
                        listener.onRetryClick(photo);
                    } else {
                        listener.onPhotoClick(photo);
                    }
                }
            });

            binding.overlayRetry.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRetryClick(photo);
                }
            });
        }
    }
}
