package com.liftley.sync360.domain.model

data class NearbyDevice(
    val id: String,
    val deviceName: String,
    val deviceType: String,
    val protocolVersion: String,
    val hostAddresses: List<String>,
    val port: Int,
    val fileTransferPort: Int,
    val serviceName: String,
    val serviceType: String
)