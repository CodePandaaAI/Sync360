package com.liftley.sync360.presentation.model

data class NearbyDeviceUiModel(
    val id: String,
    val deviceName: String,
    val deviceType: String,
    val protocolVersion: String,
    val hostAddresses: List<String>,
    val port: Int,
    val serviceName: String,
    val serviceType: String
)