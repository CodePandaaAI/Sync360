package com.liftley.sync360

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedLogicDesktopTest {

    @Test
    fun debugNetwork() {
        println("--- START NETWORK DEBUG ---")
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val ni = interfaces.nextElement()
            println("Interface: name=${ni.name}, displayName=${ni.displayName}, isUp=${ni.isUp}, isLoopback=${ni.isLoopback}, isVirtual=${ni.isVirtual}")
            val addresses = ni.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                println("  Addr: ${addr.hostAddress} (ipv4=${addr is java.net.Inet4Address}, loopback=${addr.isLoopbackAddress})")
            }
        }
        println("--- END NETWORK DEBUG ---")
    }
}