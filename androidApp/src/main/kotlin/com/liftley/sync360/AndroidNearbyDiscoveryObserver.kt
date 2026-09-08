package com.liftley.sync360

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.liftley.sync360.data.NetworkServicesController

/** Keeps nearby discovery active while the app is visible, with time to return before stopping. */
internal class AndroidNearbyDiscoveryObserver(
    private val networkServicesController: NetworkServicesController
) : DefaultLifecycleObserver {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopDiscoveryAfterDelay = Runnable {
        networkServicesController.setDiscoveryAllowedByLifecycle(false)
    }

    fun observeAppVisibility() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        mainHandler.removeCallbacks(stopDiscoveryAfterDelay)
        networkServicesController.setDiscoveryAllowedByLifecycle(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        mainHandler.removeCallbacks(stopDiscoveryAfterDelay)
        mainHandler.postDelayed(stopDiscoveryAfterDelay, BACKGROUND_GRACE_MILLIS)
    }

    private companion object {
        // Begins after ProcessLifecycleOwner's own background-event delay.
        const val BACKGROUND_GRACE_MILLIS = 2_000L
    }
}
