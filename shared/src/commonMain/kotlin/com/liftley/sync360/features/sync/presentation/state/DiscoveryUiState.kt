package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.features.sync.domain.model.DeviceProfile

data class DiscoveryUiState(
    val nearbyDevices: List<DeviceProfile> = emptyList(),
    val isScanningForDevices: Boolean = false
)
