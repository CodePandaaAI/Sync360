package com.liftley.sync360.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.liftley.sync360.domain.repository.NetworkServices

class SendScreenViewModel(private val networkServices: NetworkServices) : ViewModel() {
    init {
        networkServices.startNetworkServices()
    }
    val nearbyDevices = networkServices.nearbyDevices

    fun stopNetworkServices() {

    }
}