// File: app/src/main/java/com/yourname/githubmanager/GitHubManagerApp.kt
package com.yourname.githubmanager

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.yourname.githubmanager.utils.CrashHandler
import com.google.android.gms.ads.initialization.InitializationStatus

/**
 * Application class.
 * Responsibilities in Phase 1:
 *  - Initialize the Google Mobile Ads (AdMob) SDK once at app start.
 *  - All other SDK / DI initializations are deferred to future phases.
 */
class GitHubManagerApp : Application() {

    companion object {
        private const val TAG = "GitHubManagerApp"
    }

    override fun onCreate() {
        super.onCreate()
        CrashHandler.init(this)
        initAdMob()
    }

    /**
     * Initialize AdMob SDK using the current API.
     * Test device configuration and deprecated constants have been removed.
     */
    private fun initAdMob() {
        MobileAds.initialize(this) { status: InitializationStatus ->
            Log.d(TAG, "AdMob initialized. Status: ${status.adapterStatusMap}")
        }
    }
}
