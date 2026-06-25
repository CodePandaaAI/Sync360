package com.liftley.sync360.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.domain.local.DiscoveryStatus
import com.liftley.sync360.domain.repository.NetworkServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class SendScreenViewModel(private val networkServices: NetworkServices) : ViewModel() {
    init {
        viewModelScope.launch {
            networkServices.startNetworkServices()

            delay(15000.milliseconds)

            stopDiscoveryServices()
        }
    }

    val nearbyDevices = networkServices.nearbyDevices

    val discoveryServiceStatus = networkServices.discoveryServiceStatus

    fun stopDiscoveryServices() {
        when (discoveryServiceStatus.value) {
            DiscoveryStatus.Idle -> {}
            DiscoveryStatus.Stopping -> {}
            DiscoveryStatus.Starting -> {}
            DiscoveryStatus.Running -> {
                networkServices.stopDiscoveryServices()
            }
        }
    }

    fun restartDiscoveryServices() {
        viewModelScope.launch {
            when (discoveryServiceStatus.value) {
                DiscoveryStatus.Idle -> {
                    networkServices.restartDiscoveryServices()
                    delay(15000.milliseconds)

                    stopDiscoveryServices()
                }

                DiscoveryStatus.Stopping -> {}
                DiscoveryStatus.Starting -> {}
                DiscoveryStatus.Running -> {
                    stopDiscoveryServices()

                    discoveryServiceStatus.first {
                        it == DiscoveryStatus.Idle
                    }

                    networkServices.restartDiscoveryServices()

                    delay(15000.milliseconds)

                    stopDiscoveryServices()
                }
            }
        }
    }
}