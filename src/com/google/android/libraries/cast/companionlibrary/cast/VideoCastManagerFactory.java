package com.google.android.libraries.cast.companionlibrary.cast;

import android.content.Context;

public class VideoCastManagerFactory implements CastManagerFactory {

    private VideoCastManager videoCastManager;

    @Override
    public BaseCastManager build(Context context, CastConfiguration configuration) {
        if (videoCastManager == null)
            videoCastManager = new VideoCastManager(context, configuration);
        return videoCastManager;
    }
}
