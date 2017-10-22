package com.google.android.libraries.cast.companionlibrary.cast.reconnection;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.SparseArray;

import com.google.android.libraries.cast.companionlibrary.R;
import com.google.android.libraries.cast.companionlibrary.cast.BaseCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.CastManagerBuilder;
import com.google.android.libraries.cast.companionlibrary.utils.Utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ReconnectionJobService extends JobService {

    private ExecutorService executorService;
    private SparseArray<Future<?>> tasks;

    public static void startService(Context context) {
        BaseCastManager castManager = CastManagerBuilder.getCastManager();
        String ssid = Utils.getWifiSsid(context);
        castManager.getPreferenceAccessor()
                .saveBooleanToPreference(BaseCastManager.PREFS_KEY_WIFI_STATUS, ssid != null);
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
        JobInfo info = new JobInfo.Builder(context.getResources()
                .getInteger(R.integer.job_reconnect_id), new ComponentName(context, ReconnectionJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .build();
        jobScheduler.schedule(info);
        info = new JobInfo.Builder(context.getResources()
                .getInteger(R.integer.job_clear_persistent_info_id), new ComponentName(context,
                ReconnectionJobService.class))
                .setMinimumLatency(ClearInfoJob.getRemainingTime())
                .build();
        jobScheduler.schedule(info);
    }

    static void startWifiService(Context context) {
        BaseCastManager castManager = CastManagerBuilder.getCastManager();
        String ssid = Utils.getWifiSsid(context);
        castManager.getPreferenceAccessor()
                .saveBooleanToPreference(BaseCastManager.PREFS_KEY_WIFI_STATUS, ssid != null);
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
        JobInfo info = new JobInfo.Builder(context.getResources()
                .getInteger(R.integer.job_reconnect_id), new ComponentName(context, ReconnectionJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .build();
        jobScheduler.schedule(info);
    }

    static void startClearService(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
        JobInfo info = new JobInfo.Builder(context.getResources()
                .getInteger(R.integer.job_clear_persistent_info_id), new ComponentName(context,
                ReconnectionJobService.class))
                .setMinimumLatency(ClearInfoJob.getRemainingTime())
                .build();
        jobScheduler.schedule(info);
    }

    public static void stopService(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(context.getResources().getInteger(R.integer.job_reconnect_id));
        jobScheduler.cancel(context.getResources().getInteger(R.integer.job_clear_persistent_info_id));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
        tasks = new SparseArray<>();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
    }

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        AsyncJob j = AsyncJobFactory.getAsyncJob(this, jobParameters);
        if (j == null) {
            return tasks.size() > 0;
        }
        tasks.put(jobParameters.getJobId(), executorService.submit(j));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Future<?> f = tasks.get(jobParameters.getJobId());
        if (f != null) {
            f.cancel(true);
            tasks.remove(jobParameters.getJobId());
        }
        return true;
    }
}
