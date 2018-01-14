package com.google.android.libraries.cast.companionlibrary.cast;

import android.content.Context;
import android.support.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * You can add and manage multiple cast manager. A manager is added
 * calling build. The order is important. The first cast manager is used
 * as default inside the library. For example if you use the
 * VideoCastNotificationService, the first manager must be an instance
 * of VideoCastManager.
 */
@SuppressWarnings("unchecked")
public class CastManagerBuilder {

    private static CastManagerFactory factory;
    private static Map<String, BaseCastManager> castManagers = new LinkedHashMap<>();

    public static void setFactory(CastManagerFactory f) {
        factory = f;
    }

    public static void build(Context context, String key, CastConfiguration configuration) {
        castManagers.put(key, factory.build(context, configuration));
    }

    @NonNull
    public static <T extends BaseCastManager> T getCastManager() throws IllegalStateException {
        if (castManagers.size() == 0)
            throw new IllegalStateException("No cast managers configured");
        Map.Entry<String, BaseCastManager> entry = castManagers.entrySet().iterator().next();
        return (T) entry.getValue();
    }

    @NonNull
    public static <T extends BaseCastManager> T getCastManager(String key) throws IllegalStateException {
        if (castManagers.size() == 0)
            throw new IllegalStateException("No cast managers configured");
        return (T) castManagers.get(key);
    }
}
