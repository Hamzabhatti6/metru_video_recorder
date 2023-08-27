package com.hamza.metru.base

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MetruApplication: Application() {

    override fun onCreate() {
        super.onCreate()
    }
}