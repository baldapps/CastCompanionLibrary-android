package com.google.android.libraries.cast.companionlibrary.cast.ui;

import android.os.Bundle;

import com.google.android.libraries.cast.companionlibrary.cast.CastManagerBuilder;

import androidx.appcompat.app.AppCompatActivity;

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
