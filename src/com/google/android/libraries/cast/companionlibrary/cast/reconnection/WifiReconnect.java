package com.google.android.libraries.cast.companionlibrary.cast.reconnection;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.google.android.libraries.cast.companionlibrary.cast.BaseCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.CastManagerBuilder;
import com.google.android.libraries.cast.companionlibrary.utils.Utils;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class WifiReconnect extends AsyncJob {

    private static final int RECONNECTION_ATTEMPT_PERIOD_S = 15;
    private Handler handler;

    public WifiReconnect(JobService service, JobParameters p) {
        super(service, p);
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected boolean work() throws Exception {
        final BaseCastManager castManager = CastManagerBuilder.getCastManager();
        final String ssid = Utils.getWifiSsid(getContext());
        castManager.getPreferenceAccessor()
                .saveBooleanToPreference(BaseCastManager.PREFS_KEY_WIFI_STATUS, ssid != null);
        if (ssid != null && !castManager.isConnected() && !castManager.isConnecting()) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    castManager.reconnectSessionIfPossible(RECONNECTION_ATTEMPT_PERIOD_S, ssid);
                }
            });
        }
        //reschedule
        ReconnectionJobService.startWifiService(getContext());
        return false;
    }
}
