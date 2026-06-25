package com.liftley.sync360.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.domain.repository.NetworkServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class SendScreenViewModel(private val networkServices: NetworkServices) : ViewModel() {
    init {
        networkServices.startNetworkServices()
    }
    val nearbyDevices = networkServices.nearbyDevices

    fun stopDiscoveryServices() {
        networkServices.stopDiscoveryServices()
    }

    fun restartDiscoveryServices() {
        viewModelScope.launch {
            stopDiscoveryServices()
            delay(3000.milliseconds)
            networkServices.restartDiscoveryServices()
        }
    }
}