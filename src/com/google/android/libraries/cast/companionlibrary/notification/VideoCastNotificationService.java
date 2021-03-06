/*
 * Copyright (C) 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.libraries.cast.companionlibrary.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.libraries.cast.companionlibrary.R;
import com.google.android.libraries.cast.companionlibrary.cast.CastConfiguration;
import com.google.android.libraries.cast.companionlibrary.cast.CastManagerBuilder;
import com.google.android.libraries.cast.companionlibrary.cast.MediaQueue;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumerImpl;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.CastException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.NoConnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.TransientNetworkDisconnectionException;
import com.google.android.libraries.cast.companionlibrary.remotecontrol.VideoIntentReceiver;
import com.google.android.libraries.cast.companionlibrary.utils.LogUtils;
import com.google.android.libraries.cast.companionlibrary.utils.Utils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;


import static com.google.android.libraries.cast.companionlibrary.utils.LogUtils.LOGD;
import static com.google.android.libraries.cast.companionlibrary.utils.LogUtils.LOGE;

/**
 * A service to provide status bar Notifications when we are casting. For JB+ versions,
 * notification area supports a number of actions such as play/pause toggle or an "x" button to
 * disconnect but that for GB, these actions are not supported that due to the framework
 * limitations.
 */
public class VideoCastNotificationService extends Service {

    private static final String TAG = LogUtils.makeLogTag(VideoCastNotificationService.class);

    public static final String ACTION_FORWARD = "com.google.android.libraries.cast.companionlibrary.action.forward";
    public static final String ACTION_REWIND = "com.google.android.libraries.cast.companionlibrary.action.rewind";
    public static final String ACTION_TOGGLE_PLAYBACK = "com.google.android.libraries.cast.companionlibrary.action" +
            ".toggleplayback";
    public static final String ACTION_PLAY_NEXT = "com.google.android.libraries.cast.companionlibrary.action.playnext";
    public static final String ACTION_PLAY_PREV = "com.google.android.libraries.cast.companionlibrary.action.playprev";
    public static final String ACTION_STOP = "com.google.android.libraries.cast.companionlibrary.action.stop";
    public static final String ACTION_VISIBILITY = "com.google.android.libraries.cast.companionlibrary.action" + "" +
            ".notificationvisibility";
    public static final String EXTRA_FORWARD_STEP_MS = "ccl_extra_forward_step_ms";
    protected static final int NOTIFICATION_ID = 1;
    public static final String NOTIFICATION_VISIBILITY = "visible";

    private static final long TEN_SECONDS_MILLIS = TimeUnit.SECONDS.toMillis(10);
    private static final long THIRTY_SECONDS_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private boolean mIsPlaying;
    private Class<?> mTargetActivity;
    private int mOldStatus = -1;
    protected Notification mNotification;
    private boolean mVisible;
    protected VideoCastManager mCastManager;
    private VideoCastConsumerImpl mConsumer;
    private int mDimensionInPixels;
    private boolean mHasNext;
    private boolean mHasPrev;
    private List<Integer> mNotificationActions;
    private int[] mNotificationCompactActionsArray;
    private long mForwardTimeInMillis;
    private MediaInfo mediaInfo;
    private boolean isServiceIdle;
    private SimpleTarget<Bitmap> bitmapTarget = new SimpleTarget<Bitmap>() {
        @Override
        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
            try {
                build(mediaInfo, resource, mIsPlaying);
            } catch (CastException | NoConnectionException | TransientNetworkDisconnectionException e) {
                LOGE(TAG, "Failed to set notification", e);
            } finally {
                //Clean reference to media info we took waiting for the callback
                mediaInfo = null;
            }
            if (mVisible && (mNotification != null)) {
                serviceActive();
            }
        }
    };

    @Override
    public void onCreate() {
        LOGD(TAG, "cast notification created");
        super.onCreate();
        mCastManager = CastManagerBuilder.getCastManager();
        mDimensionInPixels = Utils.convertDpToPixel(VideoCastNotificationService.this, getResources().getDimension(R
                .dimen.ccl_notification_image_size));
        readPersistedData();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setUpNotification();
            startForeground(NOTIFICATION_ID, mNotification);
        }
        if (!mCastManager.isConnected() && !mCastManager.isConnecting()) {
            mCastManager.reconnectSessionIfPossible();
        }
        MediaQueue mediaQueue = mCastManager.getMediaQueue();
        if (mediaQueue != null) {
            int position = mediaQueue.getCurrentItemPosition();
            int size = mediaQueue.getCount();
            mHasNext = position < (size - 1);
            mHasPrev = position > 0;
        }
        mConsumer = new VideoCastConsumerImpl() {
            @Override
            public void onApplicationDisconnected(int errorCode) {
                LOGD(TAG, "onApplicationDisconnected() was reached, stopping the notification service");
                stopSelf();
            }

            @Override
            public void onDisconnected() {
                LOGD(TAG, "onDisconnected stopping service");
                stopSelf();
            }

            @Override
            public void onRemoteMediaPlayerStatusUpdated() {
                int mediaStatus = mCastManager.getPlaybackStatus();
                VideoCastNotificationService.this.onRemoteMediaPlayerStatusUpdated(mediaStatus);
            }

            @Override
            public void onUiVisibilityChanged(boolean visible) {
                LOGD(TAG, "on ui visibility change now is visible: " + visible);
                mVisible = !visible;

                if (mNotification == null) {
                    try {
                        setUpNotification(mCastManager.getRemoteMediaInformation());
                    } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                        LOGE(TAG, "onStartCommand() failed to get media", e);
                    }
                }
                if (mVisible && mNotification != null) {
                    serviceActive();
                } else {
                    serviceIdle();
                }
            }

            @Override
            public void onMediaQueueUpdated(List<MediaQueueItem> queueItems, MediaQueueItem item, int repeatMode,
                                            boolean shuffle) {
                int size = 0;
                int position = 0;
                if (queueItems != null) {
                    size = queueItems.size();
                    position = queueItems.indexOf(item);
                }
                mHasNext = position < (size - 1);
                mHasPrev = position > 0;
            }
        };
        mCastManager.addVideoCastConsumer(mConsumer);
        mNotificationActions = mCastManager.getCastConfiguration().getNotificationActions();
        List<Integer> notificationCompactActions = mCastManager.getCastConfiguration().getNotificationCompactActions();
        if (notificationCompactActions != null) {
            mNotificationCompactActionsArray = new int[notificationCompactActions.size()];
            for (int i = 0; i < notificationCompactActions.size(); i++) {
                mNotificationCompactActionsArray[i] = notificationCompactActions.get(i);
            }
        }
        mForwardTimeInMillis = TimeUnit.SECONDS.toMillis(mCastManager.getCastConfiguration().getForwardStep());
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LOGD(TAG, "onStartCommand");
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_VISIBILITY.equals(action)) {
                mVisible = intent.getBooleanExtra(NOTIFICATION_VISIBILITY, false);
                LOGD(TAG, "onStartCommand(): Action: ACTION_VISIBILITY " + mVisible);
                onRemoteMediaPlayerStatusUpdated(mCastManager.getPlaybackStatus());
                if (mNotification == null) {
                    try {
                        setUpNotification(mCastManager.getRemoteMediaInformation());
                    } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                        LOGE(TAG, "onStartCommand() failed to get media", e);
                    }
                }
                if (mVisible && mNotification != null) {
                    serviceActive();
                } else {
                    serviceIdle();
                }
            }
        }

        return Service.START_STICKY;
    }

    private void setUpNotification() {
        String castingTo = getResources().getString(R.string.ccl_casting_to_device, mCastManager.getDeviceName());
        createNotificationChannel(this);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "cast").setSmallIcon(R.drawable
                .ic_stat_action_notification)
                .setContentTitle(getString(R.string.ccl_notification_waiting))
                .setContentText(castingTo)
                .setContentIntent(getContentIntent(null))
                .setColor(ContextCompat.getColor(this, R.color.ccl_notification_color))
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(getDisconnectAction())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        mNotification = builder.build();
    }

    private void setUpNotification(final MediaInfo info) throws TransientNetworkDisconnectionException,
            NoConnectionException {
        if (info == null) {
            return;
        }
        mediaInfo = info;
        if (!info.getMetadata().hasImages()) {
            try {
                build(info, null, mIsPlaying);
            } catch (CastException e) {
                LOGE(TAG, "Failed to build notification", e);
            }
        } else {
            Uri imgUri = info.getMetadata().getImages().get(0).getUrl();
            RequestOptions options = new RequestOptions().override(mDimensionInPixels, mDimensionInPixels).centerCrop();
            Glide.with(getApplicationContext()).asBitmap().load(imgUri).apply(options).into(bitmapTarget);
        }
    }

    /**
     * Removes the existing notification.
     */
    private void removeNotification() {
        NotificationManager mng = ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
        if (mng != null)
            mng.cancel(NOTIFICATION_ID);
    }

    private void updateNotification() {
        NotificationManager mng = ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
        if (mng != null)
            mng.notify(NOTIFICATION_ID, mNotification);
    }

    protected void onRemoteMediaPlayerStatusUpdated(int mediaStatus) {
        if (mOldStatus == mediaStatus) {
            // not need to make any updates here
            return;
        }
        mOldStatus = mediaStatus;
        LOGD(TAG, "onRemoteMediaPlayerStatusUpdated() reached with status: " + mediaStatus);
        try {
            switch (mediaStatus) {
                case MediaStatus.PLAYER_STATE_BUFFERING: // (== 4)
                    mIsPlaying = false;
                    setUpNotification(mCastManager.getRemoteMediaInformation());
                    break;
                case MediaStatus.PLAYER_STATE_PLAYING: // (== 2)
                    mIsPlaying = true;
                    setUpNotification(mCastManager.getRemoteMediaInformation());
                    break;
                case MediaStatus.PLAYER_STATE_PAUSED: // (== 3)
                    mIsPlaying = false;
                    setUpNotification(mCastManager.getRemoteMediaInformation());
                    break;
                case MediaStatus.PLAYER_STATE_IDLE: // (== 1)
                    mIsPlaying = false;
                    if (!mCastManager.shouldRemoteUiBeVisible(mediaStatus, mCastManager.getIdleReason())) {
                        serviceIdle();
                    } else {
                        setUpNotification(mCastManager.getRemoteMediaInformation());
                    }
                    break;
                case MediaStatus.PLAYER_STATE_UNKNOWN: // (== 0)
                    mIsPlaying = false;
                    serviceIdle();
                    break;
                default:
                    break;
            }
        } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
            LOGE(TAG, "Failed to update the playback status due to network issues", e);
        }
    }

    /**
     * On android 8+ the notification is kept always, so we just
     * need to update its content. On older versions we should restore
     * the foreground status and the notification will be posted
     * again.
     */
    private void serviceActive() {
        if (mNotification == null)
            return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            /*
             * If service was idle we can start foreground, if it
             * was already active update notification.
             */
            if (isServiceIdle) {
                isServiceIdle = false;
                startForeground(NOTIFICATION_ID, mNotification);
            } else
                updateNotification();
        } else {
            isServiceIdle = false;
            updateNotification();
        }
    }

    /**
     * On android 8+ the notification is kept always, so we just
     * need to reset its content to the default idle state.
     * On older versions we remove the foreground status and
     * the notification is removed.
     */
    private void serviceIdle() {
        isServiceIdle = true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            stopForeground(true);
        } else {
            setUpNotification();
            updateNotification();
        }
    }

    /*
     * (non-Javadoc)
     * @see android.app.Service#onDestroy()
     */
    @Override
    public void onDestroy() {
        LOGD(TAG, "on destroy called");
        Glide.with(getApplicationContext()).clear(bitmapTarget);
        removeNotification();
        if (mCastManager != null && mConsumer != null) {
            mCastManager.removeVideoCastConsumer(mConsumer);
            mCastManager = null;
        }
    }

    public static void createNotificationChannel(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("cast", context.getString(R.string.cast_channel),
                    NotificationManager.IMPORTANCE_LOW);
            channel.enableVibration(false);
            channel.enableLights(false);
            channel.setShowBadge(false);
            if (manager != null)
                manager.createNotificationChannel(channel);
        }
    }

    /**
     * Build the MediaStyle notification. The action that are added to this notification are
     * selected by the client application from a pre-defined set of actions
     *
     * @see CastConfiguration.Builder#addNotificationAction(int, boolean)
     **/
    protected void build(MediaInfo info, Bitmap bitmap, boolean isPlaying) throws CastException,
            TransientNetworkDisconnectionException, NoConnectionException {

        // Media metadata
        MediaMetadata metadata = info.getMetadata();
        String castingTo = getResources().getString(R.string.ccl_casting_to_device, mCastManager.getDeviceName());

        createNotificationChannel(this);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "cast").setSmallIcon(R.drawable
                .ic_stat_action_notification)
                .setContentTitle(metadata.getString(MediaMetadata.KEY_TITLE))
                .setContentText(castingTo)
                .setContentIntent(getContentIntent(info))
                .setLargeIcon(bitmap)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(mNotificationCompactActionsArray)
                        .setMediaSession(mCastManager.getMediaSessionCompatToken()))
                .setOngoing(true)
                .setShowWhen(false)
                .setColorized(true)
                .setColor(ContextCompat.getColor(this, R.color.ccl_notification_color))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        for (Integer notificationType : mNotificationActions) {
            switch (notificationType) {
                case CastConfiguration.NOTIFICATION_ACTION_DISCONNECT:
                    builder.addAction(getDisconnectAction());
                    break;
                case CastConfiguration.NOTIFICATION_ACTION_PLAY_PAUSE:
                    builder.addAction(getPlayPauseAction(info, isPlaying));
                    break;
                case CastConfiguration.NOTIFICATION_ACTION_SKIP_NEXT:
                    builder.addAction(getSkipNextAction());
                    break;
                case CastConfiguration.NOTIFICATION_ACTION_SKIP_PREVIOUS:
                    builder.addAction(getSkipPreviousAction());
                    break;
                case CastConfiguration.NOTIFICATION_ACTION_FORWARD:
                    builder.addAction(getForwardAction(mForwardTimeInMillis));
                    break;
                case CastConfiguration.NOTIFICATION_ACTION_REWIND:
                    builder.addAction(getRewindAction(mForwardTimeInMillis));
                    break;
            }
        }

        mNotification = builder.build();

    }

    /**
     * Returns the {@link NotificationCompat.Action} for forwarding the current media by
     * {@code millis} milliseconds.
     */
    protected NotificationCompat.Action getForwardAction(long millis) {
        Intent intent = new Intent(this, VideoIntentReceiver.class);
        intent.setAction(ACTION_FORWARD);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_FORWARD_STEP_MS, (int) millis);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        int iconResourceId = R.drawable.ic_notification_forward_48dp;
        if (millis == TEN_SECONDS_MILLIS) {
            iconResourceId = R.drawable.ic_notification_forward_10_24dp;
        } else if (millis == THIRTY_SECONDS_MILLIS) {
            iconResourceId = R.drawable.ic_notification_forward_30_24dp;
        }

        return new NotificationCompat.Action.Builder(iconResourceId, getString(R.string.ccl_forward), pendingIntent)
                .build();
    }

    /**
     * Returns the {@link NotificationCompat.Action} for rewinding the current media by
     * {@code millis} milliseconds.
     */
    protected NotificationCompat.Action getRewindAction(long millis) {
        Intent intent = new Intent(this, VideoIntentReceiver.class);
        intent.setAction(ACTION_REWIND);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_FORWARD_STEP_MS, (int) -millis);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        int iconResourceId = R.drawable.ic_notification_rewind_24dp;
        if (millis == TEN_SECONDS_MILLIS) {
            iconResourceId = R.drawable.ic_notification_rewind10_24dp;
        } else if (millis == THIRTY_SECONDS_MILLIS) {
            iconResourceId = R.drawable.ic_notification_rewind30_24dp;
        }
        return new NotificationCompat.Action.Builder(iconResourceId, getString(R.string.ccl_rewind), pendingIntent)
                .build();
    }

    /**
     * Returns the {@link NotificationCompat.Action} for skipping to the next item in the queue. If
     * we are already at the end of the queue, we show a dimmed version of the icon for this action
     * and won't send any {@link PendingIntent}
     */
    protected NotificationCompat.Action getSkipNextAction() {
        PendingIntent pendingIntent = null;
        int iconResourceId = R.drawable.ic_notification_skip_next_semi_24dp;
        if (mHasNext) {
            Intent intent = new Intent(this, VideoIntentReceiver.class);
            intent.setAction(ACTION_PLAY_NEXT);
            intent.setPackage(getPackageName());
            pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        }

        return new NotificationCompat.Action.Builder(iconResourceId, getString(R.string.ccl_skip_next),
                pendingIntent).build();
    }

    /**
     * Returns the {@link NotificationCompat.Action} for skipping to the previous item in the queue.
     * If we are already at the beginning of the queue, we show a dimmed version of the icon for
     * this action and won't send any {@link PendingIntent}
     */
    protected NotificationCompat.Action getSkipPreviousAction() {
        PendingIntent pendingIntent = null;
        int iconResourceId = R.drawable.ic_notification_skip_prev_semi_24dp;
        if (mHasPrev) {
            Intent intent = new Intent(this, VideoIntentReceiver.class);
            intent.setAction(ACTION_PLAY_PREV);
            intent.setPackage(getPackageName());
            pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        }

        return new NotificationCompat.Action.Builder(iconResourceId, getString(R.string.ccl_skip_previous),
                pendingIntent)
                .build();
    }

    /**
     * Returns the {@link NotificationCompat.Action} for toggling play/pause/stop of the currently
     * playing item.
     */
    protected NotificationCompat.Action getPlayPauseAction(MediaInfo info, boolean isPlaying) {
        int pauseOrStopResourceId;
        if (info.getStreamType() == MediaInfo.STREAM_TYPE_LIVE) {
            pauseOrStopResourceId = R.drawable.ic_notification_stop_24dp;
        } else {
            pauseOrStopResourceId = R.drawable.ic_notification_pause_24dp;
        }
        int pauseOrPlayTextResourceId = isPlaying ? R.string.ccl_pause : R.string.ccl_play;
        int pauseOrPlayResourceId = isPlaying ? pauseOrStopResourceId : R.drawable.ic_notification_play_24dp;
        Intent intent = new Intent(this, VideoIntentReceiver.class);
        intent.setAction(ACTION_TOGGLE_PLAYBACK);
        intent.setPackage(getPackageName());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        return new NotificationCompat.Action.Builder(pauseOrPlayResourceId, getString(pauseOrPlayTextResourceId),
                pendingIntent)
                .build();
    }

    /**
     * Returns the {@link NotificationCompat.Action} for disconnecting this app from the cast
     * device.
     */
    protected NotificationCompat.Action getDisconnectAction() {
        Intent intent = new Intent(this, VideoIntentReceiver.class);
        intent.setAction(ACTION_STOP);
        intent.setPackage(getPackageName());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        return new NotificationCompat.Action.Builder(R.drawable.ic_notification_disconnect_24dp, getString(R.string
                .ccl_disconnect), pendingIntent)
                .build();
    }

    /**
     * Returns the {@link PendingIntent} for showing the full screen cast controller page. We also
     * build an appropriate "back stack" so that when user is sent to that full screen controller,
     * clicking on the Back button would allow navigation into the app.
     */
    protected PendingIntent getContentIntent(MediaInfo mediaInfo) {
        Intent contentIntent = new Intent(this, mTargetActivity);
        Bundle mediaWrapper = null;
        if (mediaInfo != null) {
            mediaWrapper = Utils.mediaInfoToBundle(mediaInfo);
            contentIntent.putExtra(VideoCastManager.EXTRA_MEDIA, mediaWrapper);
        }
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(mTargetActivity);
        stackBuilder.addNextIntent(contentIntent);
        if (mediaWrapper != null && stackBuilder.getIntentCount() > 1) {
            Intent i = stackBuilder.editIntentAt(1);
            if (i != null)
                i.putExtra(VideoCastManager.EXTRA_MEDIA, mediaWrapper);
        }
        return stackBuilder.getPendingIntent(NOTIFICATION_ID, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /*
     * Reads application ID and target activity from preference storage.
     */
    private void readPersistedData() {
        mTargetActivity = mCastManager.getCastConfiguration().getTargetActivity();
        if (mTargetActivity == null) {
            mTargetActivity = VideoCastManager.DEFAULT_TARGET_ACTIVITY;
        }
    }
}
