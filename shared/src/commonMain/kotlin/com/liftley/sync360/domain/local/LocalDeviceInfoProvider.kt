package com.liftley.sync360.domain.local

import com.liftley.sync360.domain.model.LocalDeviceInfo

interface LocalDeviceInfoProvider {
    fun getLocalDeviceInfo(): LocalDeviceInfo
}