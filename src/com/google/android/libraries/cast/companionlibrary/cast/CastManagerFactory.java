package com.google.android.libraries.cast.companionlibrary.cast;

import android.content.Context;

public interface CastManagerFactory {
    BaseCastManager build(Context context, CastConfiguration configuration);
}
