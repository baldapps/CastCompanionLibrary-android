package com.google.android.libraries.cast.companionlibrary.cast.reconnection;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.os.Build;

import com.google.android.libraries.cast.companionlibrary.R;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class AsyncJobFactory {

    private AsyncJobFactory() {
    }

    public static AsyncJob getAsyncJob(ReconnectionJobService service, JobParameters parameters) {
        if (parameters.getJobId() == service.getResources().getInteger(R.integer.job_reconnect_id)) {

        } else if (parameters.getJobId() == service.getResources().getInteger(R.integer.job_clear_persistent_info_id)) {

        }
        return null;
    }
}
