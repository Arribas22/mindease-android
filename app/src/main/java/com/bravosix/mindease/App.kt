package com.bravosix.mindease

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.google.android.gms.ads.MobileAds

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Inicializar AdMob en background para no bloquear el arranque
        MobileAds.initialize(this) {
            RewardedAdManager.preload(this)
        }
        BillingManager.init(this)
        LlmManager.get(this).init()
        WellnessNotificationWorker.schedule(this)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            WellnessNotificationWorker.CHANNEL_ID,
            "Bienestar Mental",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Recordatorios y consejos de bienestar mental impulsados por IA"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
