package com.liftley.sync360.domain

import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.liftley.sync360.domain.model.NearbyDevice
import kotlin.collections.toString

@Suppress("Deprecation")
fun NsdServiceInfo.toNearbyDeviceAndroidImpl(): NearbyDevice? {
    val deviceUuid = this.attributes["deviceUuid"]?.toString(Charsets.UTF_8) ?: return null
    val deviceName = this.attributes["deviceName"]?.toString(Charsets.UTF_8) ?: return null
    val deviceType = this.attributes["deviceType"]?.toString(Charsets.UTF_8) ?: return null
    val protocolVersion = this.attributes["protocolVersion"]?.toString(Charsets.UTF_8) ?: return null
    val fileTransferPort = attributes["fileTransferPort"]?.toString(Charsets.UTF_8)?.toIntOrNull() ?: return null

    val hostAddresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        this.hostAddresses.map { address ->
            address.hostAddress
        }
    } else {
        listOfNotNull(this.host?.hostAddress)
    }

    if (hostAddresses.isEmpty()) return null

    if (this.port <= 0) return null

    if (fileTransferPort <= 0) return null

    return NearbyDevice(
        id = deviceUuid,
        deviceName = deviceName,
        deviceType = deviceType,
        protocolVersion = protocolVersion,
        hostAddresses = hostAddresses,
        port = this.port,
        fileTransferPort = fileTransferPort,
        serviceName = this.serviceName,
        serviceType = this.serviceType,
    )
}