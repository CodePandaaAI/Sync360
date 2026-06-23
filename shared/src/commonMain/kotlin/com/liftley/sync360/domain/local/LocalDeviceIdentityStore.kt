package com.liftley.sync360.domain.local

interface LocalDeviceIdentityStore {
    fun getOrCreateDeviceUuid(): String
}