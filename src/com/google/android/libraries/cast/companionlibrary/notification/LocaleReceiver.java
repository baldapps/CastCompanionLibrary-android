package com.google.android.libraries.cast.companionlibrary.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class LocaleReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
            VideoCastNotificationService.createNotificationChannel(context);
        }
    }
}
