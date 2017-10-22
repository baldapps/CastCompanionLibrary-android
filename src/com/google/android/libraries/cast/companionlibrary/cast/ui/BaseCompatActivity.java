package com.google.android.libraries.cast.companionlibrary.cast.ui;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.google.android.libraries.cast.companionlibrary.cast.CastManagerBuilder;

public class BaseCompatActivity extends AppCompatActivity {
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
}
