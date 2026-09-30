package com.liftley.sync360.data.network.discovery

import android.content.Context
import android.net.nsd.DiscoveryRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions
import android.util.Log
import androidx.annotation.RequiresExtension
import com.liftley.sync360.domain.local.LocalDeviceIdentityStore
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.RegistrationStatus
import com.liftley.sync360.domain.service.NetworkServices
import com.liftley.sync360.domain.toNearbyDeviceAndroidImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AndroidNetworkServices(context: Context, identityStore: LocalDeviceIdentityStore) :
    NetworkServices {
    private val nsdManager = requireNotNull(context.getSystemService(NsdManager::class.java))
    private val mainExecutor = context.mainExecutor

    private val myDeviceUuid = identityStore.getOrCreateDeviceUuid()
    private val _nearbyDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    override val nearbyDevices: StateFlow<List<NearbyDevice>> = _nearbyDevices.asStateFlow()

    private val _discoveryServiceStatus = MutableStateFlow(DiscoveryStatus.Idle)
    override val discoveryServiceStatus: StateFlow<DiscoveryStatus> =
        _discoveryServiceStatus.asStateFlow()

    // Modern Discovery Service Holder (One Complete Object)
    private var modernDiscoveryCallback: NsdManager.ServiceInfoCallback? = null

    // Legacy Discovery Service Holder
    private var legacyDiscoveryCallback: NsdManager.DiscoveryListener? = null

    // Legacy Discovery and Resolving system should resolve one discovered service at once, or
    // it may break/crash or errors will occur, that's why we give Android only one service to
    // resolve at a time by using queue, we wait until one resolves or fails to resolve, then
    // after anything happens we resolve next in the queue @isLegacyPendingResolutionQueue,
    // Also @isLegacyResolverCurrentlyResolving turns true when resoling and false when resolved

    private var legacyPendingResolutionsQueue: ArrayDeque<NsdServiceInfo> = ArrayDeque()

    private var isLegacyResolverCurrentlyResolving: Boolean = false

    // Universal Registration
    private var registrationListener: NsdManager.RegistrationListener? = null

    private val _registrationServiceStatus = MutableStateFlow(RegistrationStatus.Idle)
    override val registrationServiceStatus: StateFlow<RegistrationStatus> =
        _registrationServiceStatus.asStateFlow()


    override suspend fun startDiscovery() {
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle && discoveryServiceStatus.value != DiscoveryStatus.FailedToStart) return

        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 22) {
            startModernDiscovery()
        } else {
            startLegacyDiscovery()
        }

    }

    override suspend fun stopDiscovery() {
        Log.d("Discovery Cleanup Engine", "Stopping and Cleaning Discovery and Advertising")
        if (discoveryServiceStatus.value == DiscoveryStatus.FailedToStart) {
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
            _nearbyDevices.value = emptyList()
            legacyPendingResolutionsQueue.clear()
            return
        }
        if (discoveryServiceStatus.value != DiscoveryStatus.Running && discoveryServiceStatus.value != DiscoveryStatus.CleanupFailed) return

        _discoveryServiceStatus.value = DiscoveryStatus.Stopping

        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 22) {
            runCatching {
                modernDiscoveryCallback?.let {
                    nsdManager.unregisterServiceInfoCallback(it)
                }
                _nearbyDevices.value = emptyList()
            }.onFailure {
                _discoveryServiceStatus.value = DiscoveryStatus.CleanupFailed

                Log.d("Discovery", "Could not Unregister ServiceInfoCallback | Cleanup Failed")
            }
        } else {
            runCatching {
                legacyDiscoveryCallback?.let {
                    nsdManager.stopServiceDiscovery(it)
                }

                _nearbyDevices.value = emptyList()
            }.onFailure {
                _discoveryServiceStatus.value = DiscoveryStatus.CleanupFailed

                Log.d("Legacy Discovery", "Could not Stop Discovery | Cleanup Failed")
            }
        }

        Log.d("Discovery Cleanup Engine", "Discovery cleanup ran but not guaranteed it worked")
    }

    @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 22)
    private fun startModernDiscovery() {
        // If discovery is in any state from which it shouldn't be called then return(i.e running)
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle && discoveryServiceStatus.value != DiscoveryStatus.FailedToStart) return

        // Change state to starting so UI knows to show appropriate ui + any changes that should
        // happen
        _discoveryServiceStatus.value = DiscoveryStatus.Starting

        val discoveryRequest = DiscoveryRequest.Builder(SERVICE_TYPE)
            .setNetwork(null)
            .build()

        modernDiscoveryCallback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                // Android accepted the request call, then asynchronously rejected registration.
                modernDiscoveryCallback = null

                _discoveryServiceStatus.value = DiscoveryStatus.FailedToStart

                Log.d("Discovery", "Service failed to start | Error: $errorCode")
            }

            override fun onServiceInfoCallbackUnregistered() {
                modernDiscoveryCallback = null

                _discoveryServiceStatus.value = DiscoveryStatus.Idle

                _nearbyDevices.value = emptyList()

                Log.d("Discovery", "Successfully Unregistered Discovery Callback")
            }

            override fun onServiceLost() {
                Log.d("Discovery", "onServiceLost without parameter: Info not available")
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d("Discovery", "Service Lost with parameter: $serviceInfo")

                val cleanList =
                    nearbyDevices.value.filterNot { it.serviceName == serviceInfo.serviceName }

                _nearbyDevices.update {
                    cleanList
                }
            }

            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                val resolvedDevice = serviceInfo.toNearbyDeviceAndroidImpl() ?: return
                if (resolvedDevice.id == myDeviceUuid) return

                val cleanList = nearbyDevices.value.filterNot { it.id == resolvedDevice.id }

                _nearbyDevices.update {
                    cleanList + resolvedDevice
                }

                Log.d("Discovery", "New service found or new update for existing: $resolvedDevice")
            }
        }

        runCatching {
            modernDiscoveryCallback?.let {
                nsdManager.registerServiceInfoCallback(
                    discoveryRequest,
                    mainExecutor,
                    it
                )
            }

            _discoveryServiceStatus.value = DiscoveryStatus.Running
        }.onFailure {
            modernDiscoveryCallback = null

            _discoveryServiceStatus.value = DiscoveryStatus.FailedToStart

            Log.d("Discovery", "Service failed to start | Error: ${it.message}")
        }
    }


    private fun startLegacyDiscovery() {
        // If discovery is in any state from which it shouldn't be called then return(i.e running)
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle && discoveryServiceStatus.value != DiscoveryStatus.FailedToStart) return

        // Change state to starting so UI knows to show appropriate ui + any changes that should
        // happen
        _discoveryServiceStatus.value = DiscoveryStatus.Starting

        legacyDiscoveryCallback = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) {
                _discoveryServiceStatus.value = DiscoveryStatus.Running

                Log.d("Legacy Discovery", "Discovery has started")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                legacyDiscoveryCallback = null

                _discoveryServiceStatus.value = DiscoveryStatus.Idle

                _nearbyDevices.value = emptyList()

                legacyPendingResolutionsQueue.clear()

                Log.d("Legacy Discovery", "Discovery has stopped")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                Log.d("Legacy Discovery", "Service Found with parameter: $serviceInfo")

                if (serviceInfo == null) return

                legacyPendingResolutionsQueue.addLast(serviceInfo)

                resolveNextLegacyService()
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                Log.d("Legacy Discovery", "Service Lost with parameter: $serviceInfo")

                if (serviceInfo == null) return

                val cleanList =
                    nearbyDevices.value.filterNot { it.serviceName == serviceInfo.serviceName }

                _nearbyDevices.update {
                    cleanList
                }
            }

            override fun onStartDiscoveryFailed(
                serviceType: String?,
                errorCode: Int
            ) {
                legacyDiscoveryCallback = null

                _discoveryServiceStatus.value = DiscoveryStatus.FailedToStart

                Log.d(
                    "Legacy Discovery",
                    "Discovery failed to start: $serviceType | Error: $errorCode"
                )
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                _discoveryServiceStatus.value = DiscoveryStatus.CleanupFailed

                Log.d(
                    "Legacy Discovery",
                    "Discovery Failed to stop: $serviceType | Error: $errorCode"
                )
            }
        }

        runCatching {
            nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                legacyDiscoveryCallback
            )
        }.onFailure {
            legacyDiscoveryCallback = null

            _discoveryServiceStatus.value = DiscoveryStatus.FailedToStart

            Log.d("Legacy Discovery", "Discovery failed to start | Error: ${it.message}")
        }
    }

    @Suppress("Deprecation")
    fun resolveNextLegacyService() {
        if (isLegacyResolverCurrentlyResolving) return

        val nextServiceToResolve = legacyPendingResolutionsQueue.removeFirstOrNull() ?: return

        isLegacyResolverCurrentlyResolving = true

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(
                serviceInfo: NsdServiceInfo?,
                errorCode: Int
            ) {
                Log.d(
                    "Legacy Resolver",
                    "Resolve Failed: $serviceInfo | Error: $errorCode"
                )

                // Turns isLegacyResolverCurrentlyResolving to false and again calls this same
                // function for resolving next service

                finishLegacyResolution()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                Log.d(
                    "Legacy Resolver",
                    "Resolve Success: $serviceInfo"
                )

                // Turns isLegacyResolverCurrentlyResolving to false and again calls this same
                // function for resolving next service
                finishLegacyResolution()

                if (serviceInfo == null) return

                val resolvedDevice = serviceInfo.toNearbyDeviceAndroidImpl() ?: return

                if (resolvedDevice.id == myDeviceUuid) return

                val cleanList = nearbyDevices.value.filterNot { it.id == resolvedDevice.id }

                _nearbyDevices.update {
                    cleanList + resolvedDevice
                }
            }
        }

        runCatching {
            nsdManager.resolveService(nextServiceToResolve, resolveListener)
        }.onFailure {
            Log.d(
                "Legacy Resolver",
                "Inside runCatching's onFailure Block, Error: ${it.message}"
            )

            finishLegacyResolution()
        }
    }

    private fun finishLegacyResolution() {
        isLegacyResolverCurrentlyResolving = false
        resolveNextLegacyService()
    }

    override suspend fun startAdvertising(
        httpServerPort: Int,
        fileTransferPort: Int
    ) {
        if (registrationServiceStatus.value != RegistrationStatus.Idle && registrationServiceStatus.value != RegistrationStatus.FailedToStart) return

        _registrationServiceStatus.value = RegistrationStatus.Starting

        val serviceInfo = getMyDeviceServiceInfo(
            httpServerPort = httpServerPort,
            fileTransferPort = fileTransferPort
        )

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(
                serviceInfo: NsdServiceInfo?,
                errorCode: Int
            ) {
                registrationListener = null

                _registrationServiceStatus.value = RegistrationStatus.FailedToStart

                Log.d(
                    "Advertising",
                    "Advertising failed to start | ServiceInfo: $serviceInfo | Error: $errorCode"
                )
            }

            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                _registrationServiceStatus.value = RegistrationStatus.Running

                Log.d("Advertising", "Advertised Successfully | ServiceInfo: $serviceInfo")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                registrationListener = null

                _registrationServiceStatus.value = RegistrationStatus.Idle

                Log.d(
                    "Advertising",
                    "Advertising Unregistered Successfully | ServiceInfo: $serviceInfo"
                )
            }

            override fun onUnregistrationFailed(
                serviceInfo: NsdServiceInfo?,
                errorCode: Int
            ) {
                _registrationServiceStatus.value = RegistrationStatus.CleanupFailed

                Log.d(
                    "Advertising",
                    "Advertising failed to stop/unregister | ServiceInfo: $serviceInfo"
                )
            }

        }

        runCatching {
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener
            )
        }.onFailure {
            registrationListener = null

            _registrationServiceStatus.value = RegistrationStatus.FailedToStart

            Log.d("Resolver", "Resolver failed to start | Error: ${it.message}")
        }
    }

    override suspend fun stopAdvertising() {
        if (registrationServiceStatus.value == RegistrationStatus.FailedToStart) {
            _registrationServiceStatus.value = RegistrationStatus.Idle
            return
        }
        if (registrationServiceStatus.value != RegistrationStatus.Running &&
            registrationServiceStatus.value != RegistrationStatus.CleanupFailed
        ) return

        _registrationServiceStatus.value = RegistrationStatus.Stopping

        runCatching {
            registrationListener?.let {
                nsdManager.unregisterService(it)
            }
        }.onFailure {
            _registrationServiceStatus.value = RegistrationStatus.CleanupFailed
        }

        Log.d("Advertising Cleanup Engine", "Advertising cleanup ran but not guaranteed it worked")
    }

    private fun getMyDeviceServiceInfo(
        httpServerPort: Int,
        fileTransferPort: Int
    ): NsdServiceInfo {
        val manufacturer = Build.MANUFACTURER.trim().replaceFirstChar { it.titlecase() }
        val model = Build.MODEL.trim()
        val name = if (model.startsWith(
                manufacturer,
                ignoreCase = true
            )
        ) model else "$manufacturer $model"

        return NsdServiceInfo().apply {
            serviceType = SERVICE_TYPE
            serviceName = "${Build.MODEL} Sync360"
            port = httpServerPort
            setAttribute("deviceUuid", myDeviceUuid)
            setAttribute("deviceName", name)
            setAttribute("deviceType", "Android")
            setAttribute("protocolVersion", "1")
            setAttribute("fileTransferPort", fileTransferPort.toString())
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_sync360._tcp"
    }
}
