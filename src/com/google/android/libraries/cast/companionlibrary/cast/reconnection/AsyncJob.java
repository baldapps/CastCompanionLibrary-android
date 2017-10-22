package com.google.android.libraries.cast.companionlibrary.cast.reconnection;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.os.Build;

import java.util.concurrent.Callable;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public abstract class AsyncJob implements Callable<Void> {

    private JobService service;
    private JobParameters parameters;

    public AsyncJob(JobService service, JobParameters p) {
        this.service = service;
        parameters = p;
    }

    protected Context getContext() {
        return service;
    }

    @Override
    public final Void call() throws Exception {
        service.jobFinished(parameters, work());
        return null;
    }

    protected abstract boolean work() throws Exception;
}
