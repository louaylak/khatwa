package com.khatwa.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Interstitial ads. Two placements only:
 *  1. App open  -> ad shown once before the UI appears (skippable when it allows).
 *  2. Finish activity -> ad shown after saving, BEFORE opening the summary screen.
 * NEVER on "Start" — starting a walk is always instant.
 *
 * IMPORTANT — read before publishing:
 * While you are testing on your own phone, USE_TEST_ADS must stay `true`.
 * Clicking or even repeatedly loading your own real ads gets your AdMob
 * account limited or banned for invalid traffic. Set it to `false` only in
 * the build you publish, and never tap your own ads after that.
 */
object Ads {

    const val USE_TEST_ADS = true

    private const val REAL_INTERSTITIAL = "ca-app-pub-6787264530091827/4903970750"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712" // Google sample id

    private val unitId: String
        get() = if (USE_TEST_ADS) TEST_INTERSTITIAL else REAL_INTERSTITIAL

    @Volatile private var loadedAd: InterstitialAd? = null
    @Volatile private var initialized = false
    @Volatile private var loading = false

    fun init(ctx: Context) {
        if (initialized) return
        initialized = true
        Thread {
            try {
                MobileAds.initialize(ctx.applicationContext) { }
            } catch (_: Exception) { }
        }.start()
        preload(ctx)
    }

    fun preload(ctx: Context) {
        if (loadedAd != null || loading) return
        loading = true
        try {
            InterstitialAd.load(
                ctx.applicationContext, unitId, AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        loadedAd = ad; loading = false
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loadedAd = null; loading = false
                    }
                }
            )
        } catch (_: Exception) {
            loading = false
        }
    }

    /**
     * Shows the interstitial if one is ready, then calls [onDone].
     * If no ad is ready (offline, no fill, not loaded yet) the app continues
     * IMMEDIATELY — an ad failure must never block the user.
     */
    fun showThen(activity: Activity?, onDone: () -> Unit) {
        val ad = loadedAd
        if (activity == null || ad == null) {
            activity?.let { preload(it) }
            onDone()
            return
        }
        loadedAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload(activity)
                onDone()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preload(activity)
                onDone()
            }
        }
        try {
            ad.show(activity)
        } catch (_: Exception) {
            onDone()
        }
    }

    /**
     * App-open gate: waits up to [timeoutMs] for the first interstitial to load,
     * shows it, and suspends until it is dismissed. Returns fast when there is
     * no network or no fill, so a bad connection never traps the user on splash.
     */
    suspend fun awaitOpenAd(activity: Activity, timeoutMs: Long = 4500) {
        preload(activity)
        withTimeoutOrNull(timeoutMs) {
            while (loadedAd == null) delay(150)
        } ?: return
        val done = CompletableDeferred<Unit>()
        showThen(activity) { done.complete(Unit) }
        done.await()
    }
}

/** Unwraps the Activity behind a Compose LocalContext. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
