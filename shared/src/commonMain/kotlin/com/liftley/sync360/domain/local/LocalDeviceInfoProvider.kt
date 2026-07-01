package com.liftley.sync360.domain.local

import com.liftley.sync360.data.local.model.LocalDeviceInfo

interface LocalDeviceInfoProvider {
    fun getLocalDeviceInfo(): LocalDeviceInfo
}