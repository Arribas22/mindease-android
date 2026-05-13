package com.bravosix.mindease

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Gestiona los anuncios de vídeo recompensado.
 * Ganar un vídeo = +[Prefs.TOKENS_PER_AD] tokens de chat.
 *
 * IMPORTANTE: sustituir REWARDED_UNIT e INTERSTITIAL_UNIT por IDs de producción
 * antes del lanzamiento.
 */
object RewardedAdManager {

    // TODO: Reemplazar por IDs de producción de AdMob
    private const val REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917" // Test ID

    private var ad: RewardedAd? = null
    private var loading = false

    fun preload(ctx: Context) {
        if (ad != null || loading) return
        loading = true
        RewardedAd.load(
            ctx, REWARDED_UNIT, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) { ad = loaded; loading = false }
                override fun onAdFailedToLoad(error: LoadAdError) { ad = null; loading = false }
            }
        )
    }

    fun isReady(): Boolean = ad != null

    /**
     * Muestra el vídeo recompensado.
     * Al completarlo, el usuario recibe [tokensAmount] tokens.
     * [onReward]: invocado con los tokens añadidos.
     * [onUnavailable]: invocado si el anuncio no está cargado todavía.
     */
    fun show(
        activity: Activity,
        tokensAmount: Int = Prefs.TOKENS_PER_AD,
        onReward: (tokensAdded: Int) -> Unit,
        onUnavailable: () -> Unit,
        // Legacy params ignored, kept for source compatibility
        feature: Prefs.Feature = Prefs.Feature.UNLIMITED_CHAT,
    ) {
        val current = ad
        if (current == null) {
            preload(activity)
            onUnavailable()
            return
        }
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                preload(activity)
            }
        }
        current.show(activity, OnUserEarnedRewardListener {
            Prefs.addTokens(activity, tokensAmount)
            onReward(tokensAmount)
        })
    }
}

