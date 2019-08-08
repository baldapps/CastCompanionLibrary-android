package com.google.android.libraries.cast.companionlibrary.cast.ui;

import android.app.Activity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import android.os.Bundle;
import androidx.annotation.CallSuper;

import com.google.android.libraries.cast.companionlibrary.cast.CastManagerBuilder;

public class BaseActivity extends Activity implements LifecycleOwner {

    private LifecycleRegistry lifecycleRegistry = new LifecycleRegistry(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLifecycle().addObserver(CastManagerBuilder.getCastManager());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getLifecycle().removeObserver(CastManagerBuilder.getCastManager());
    }

    @CallSuper
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        lifecycleRegistry.markState(Lifecycle.State.CREATED);
        super.onSaveInstanceState(outState);
    }

    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }
}
