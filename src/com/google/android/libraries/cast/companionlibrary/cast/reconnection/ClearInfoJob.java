package com.google.android.libraries.cast.companionlibrary.cast.reconnection;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.SystemClock;

import com.google.android.libraries.cast.companionlibrary.cast.BaseCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.CastManagerBuilder;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.NoConnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.TransientNetworkDisconnectionException;

import java.util.concurrent.TimeUnit;


import static android.content.ContentValues.TAG;
import static com.google.android.libraries.cast.companionlibrary.utils.LogUtils.LOGD;
import static com.google.android.libraries.cast.companionlibrary.utils.LogUtils.LOGE;

public class ClearInfoJob extends AsyncJob {

    private VideoCastManager castManager;

    public ClearInfoJob(JobService service, JobParameters p) {
        super(service, p);
        castManager = CastManagerBuilder.getCastManager();
    }

    @Override
    protected boolean work() throws Exception {
        if (!castManager.isConnected()) {
            castManager.clearMediaSession();
            castManager.clearPersistedConnectionInfo(BaseCastManager.CLEAR_ALL);
        } else {
            // since we are connected and our timer has gone off, lets update the time remaining
            // on the media (since media may have been paused) and reset the time left
            long timeLeft = getRemainingTime();
            // lets reset the counter
            castManager.getPreferenceAccessor()
                    .saveLongToPreference(BaseCastManager.PREFS_KEY_MEDIA_END, timeLeft + SystemClock.elapsedRealtime
                            ());
            //reschedule
            ReconnectionJobService.startClearService(getContext());
            LOGD(TAG, "handleTermination(): resetting the timer");
        }
        return false;
    }

    static long getRemainingTime() {
        VideoCastManager castManager = CastManagerBuilder.getCastManager();
        long timeLeft = TimeUnit.MINUTES.toMillis(3);
        try {
            timeLeft = castManager.isRemoteStreamLive() ? TimeUnit.MINUTES.toMillis(3) : castManager
                    .getMediaTimeRemaining();
        } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
            LOGE(TAG, "Failed to calculate the time left for media due to lack of connectivity", e);
        }
        return timeLeft;
    }
}
