package com.liftley.sync360.presentation.send.model

import com.liftley.sync360.domain.model.NearbyDevice

fun NearbyDevice.toNearbyDeviceUiModel(): NearbyDeviceUiModel {
    return NearbyDeviceUiModel(
        id = this.id,
        deviceName = this.deviceName,
        deviceType = this.deviceType,
        protocolVersion = this.protocolVersion,
        hostAddresses = this.hostAddresses,
        port = this.port,
        fileTransferPort = this.fileTransferPort,
        serviceName = this.serviceName,
        serviceType = this.serviceType
    )
}