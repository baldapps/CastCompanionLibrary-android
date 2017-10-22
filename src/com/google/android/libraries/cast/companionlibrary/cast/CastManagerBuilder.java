package com.google.android.libraries.cast.companionlibrary.cast;

import android.content.Context;

public class CastManagerBuilder {

    private static CastManagerFactory factory;
    private static BaseCastManager castManager;

    public static void setFactory(CastManagerFactory f) {
        factory = f;
    }

    public static void build(Context context, CastConfiguration configuration) {
        castManager = factory.build(context, configuration);
    }

    public static <T extends BaseCastManager> T getCastManager() {
        return (T) castManager;
    }
}
