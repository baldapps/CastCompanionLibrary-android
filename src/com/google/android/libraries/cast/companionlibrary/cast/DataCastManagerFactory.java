package com.google.android.libraries.cast.companionlibrary.cast;

import android.content.Context;

public class DataCastManagerFactory implements CastManagerFactory {

    private DataCastManager dataCastManager;

    @Override
    public BaseCastManager build(Context context, CastConfiguration configuration) {
        if (dataCastManager == null)
            dataCastManager = new DataCastManager(context, configuration);
        return dataCastManager;
    }
}
