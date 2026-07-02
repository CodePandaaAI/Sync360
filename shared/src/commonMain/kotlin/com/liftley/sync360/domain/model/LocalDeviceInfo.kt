package com.liftley.sync360.domain.model

data class LocalDeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val protocolVersion: String
)