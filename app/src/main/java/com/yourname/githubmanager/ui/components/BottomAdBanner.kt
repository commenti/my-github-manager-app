// File: app/src/main/java/com/yourname/githubmanager/ui/components/BottomAdBanner.kt
package com.yourname.githubmanager.ui.components

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Composable that wraps AdMob's [AdView] as a fixed banner shown at the
 * bottom of the screen.
 *
 * Phase 1 rules enforced here:
 *  - Uses Google's official test banner ad unit ID so no real traffic is
 *    generated during development.
 *  - If the ad fails to load, the error is only logged — the app does NOT crash.
 *
 * Replace [TEST_BANNER_UNIT_ID] with the real ad unit ID before releasing.
 *
 * @param modifier Optional modifier. Defaults to full-width, wrap-height.
 */
@Composable
fun BottomAdBanner(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdView(context).apply {
                // Google's official test banner ad unit ID
                adUnitId = TEST_BANNER_UNIT_ID
                setAdSize(AdSize.BANNER)

                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d(TAG, "Banner ad loaded successfully.")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        // Intentionally only log — no crash, no UI change
                        Log.w(TAG, "Banner ad failed to load. Code=${error.code} Msg=${error.message}")
                    }

                    override fun onAdOpened() {
                        Log.d(TAG, "Banner ad opened.")
                    }

                    override fun onAdClicked() {
                        Log.d(TAG, "Banner ad clicked.")
                    }

                    override fun onAdClosed() {
                        Log.d(TAG, "Banner ad closed.")
                    }
                }

                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

private const val TAG = "BottomAdBanner"

/**
 * Google's official test banner ad unit ID.
 * Safe to commit — this ID only shows test ads and has no revenue implications.
 * Source: https://developers.google.com/admob/android/test-ads
 */
private const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
