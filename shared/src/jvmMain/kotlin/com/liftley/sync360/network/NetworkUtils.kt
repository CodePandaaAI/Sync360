package com.liftley.sync360.network

import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                // Ignore loopback, virtual/inactive interfaces, or common VM bridges
                if (networkInterface.isLoopback || 
                    !networkInterface.isUp || 
                    networkInterface.name.contains("virtual", ignoreCase = true) || 
                    networkInterface.displayName.contains("virtual", ignoreCase = true) || 
                    networkInterface.name.contains("docker", ignoreCase = true) || 
                    networkInterface.name.contains("vbox", ignoreCase = true) || 
                    networkInterface.name.contains("vmnet", ignoreCase = true)) {
                    continue
                }
                
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress) {
                        val hostAddress = address.hostAddress
                        // Select only IPv4 addresses (no colons)
                        if (hostAddress != null && hostAddress.indexOf(':') < 0) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("NetworkUtils: Error scanning local IPs - ${e.message}")
        }
        return "127.0.0.1"
    }
}
