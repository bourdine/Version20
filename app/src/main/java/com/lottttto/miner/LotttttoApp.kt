package com.lottttto.miner

import android.app.Application
import android.os.Build
import com.lottttto.miner.utils.NotificationUtils
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LotttttoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtils.createNotificationChannels(this)
        }
    }
}
