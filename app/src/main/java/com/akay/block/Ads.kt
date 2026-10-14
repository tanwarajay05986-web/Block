package com.akay.block

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

// AdMob. These are Google TEST ad units (safe to click). Put real ones here before publishing (Stage 10).
object Ads {
    const val INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    // set to false to turn all ads off (Revive then works for free)
    const val ENABLED = true

    // an interstitial appears on every 3rd result screen, and never closer than 45 seconds apart
    private const val EVERY = 3
    private const val GAP_MS = 45000L

    private var inter: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var interLoading = false
    private var rewLoading = false
    private var started = false
    private var count = 0
    private var lastShown = 0L
    private var showing = false

    fun init(act: Activity) {
        if (!ENABLED || started) return
        started = true
        Thread {
            try {
                MobileAds.initialize(act) {
                    act.runOnUiThread {
                        loadInter(act)
                        loadRewarded(act)
                    }
                }
            } catch (e: Throwable) {
                Log.e("Block", "Ads init failed", e)
            }
        }.start()
    }

    private fun loadInter(act: Activity) {
        if (!ENABLED || interLoading || inter != null) return
        interLoading = true
        try {
            InterstitialAd.load(
                act, INTERSTITIAL_ID, AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        inter = ad
                        interLoading = false
                    }

                    override fun onAdFailedToLoad(err: LoadAdError) {
                        inter = null
                        interLoading = false
                    }
                }
            )
        } catch (e: Throwable) {
            interLoading = false
        }
    }

    private fun loadRewarded(act: Activity) {
        if (!ENABLED || rewLoading || rewarded != null) return
        rewLoading = true
        try {
            RewardedAd.load(
                act, REWARDED_ID, AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewarded = ad
                        rewLoading = false
                    }

                    override fun onAdFailedToLoad(err: LoadAdError) {
                        rewarded = null
                        rewLoading = false
                    }
                }
            )
        } catch (e: Throwable) {
            rewLoading = false
        }
    }

    // shows an interstitial now and then, then runs `then`. When no ad is shown, `then` runs at once.
    fun maybeInterstitial(act: Activity, then: () -> Unit) {
        count++
        val now = System.currentTimeMillis()
        val ad = inter
        if (!ENABLED || showing || ad == null || count % EVERY != 0 || now - lastShown < GAP_MS) {
            if (ad == null) loadInter(act)
            then()
            return
        }
        inter = null
        showing = true
        var done = false
        fun finish() {
            if (done) return
            done = true
            showing = false
            lastShown = System.currentTimeMillis()
            loadInter(act)
            then()
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                finish()
            }

            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                finish()
            }
        }
        try {
            ad.show(act)
        } catch (e: Throwable) {
            finish()
        }
    }

    // rewarded ad for Revive: onReward only after the whole ad was watched
    fun showRewarded(act: Activity, onReward: () -> Unit, onFail: () -> Unit) {
        if (!ENABLED) {
            onReward()
            return
        }
        val ad = rewarded
        if (ad == null || showing) {
            loadRewarded(act)
            onFail()
            return
        }
        rewarded = null
        showing = true
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showing = false
                lastShown = System.currentTimeMillis()
                loadRewarded(act)
                if (earned) onReward() else onFail()
            }

            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                showing = false
                loadRewarded(act)
                onFail()
            }
        }
        try {
            ad.show(act) { earned = true }
        } catch (e: Throwable) {
            showing = false
            onFail()
        }
    }
}
