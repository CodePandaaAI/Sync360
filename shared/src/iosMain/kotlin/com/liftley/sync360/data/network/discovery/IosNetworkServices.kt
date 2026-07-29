package com.liftley.sync360.data.network.discovery

import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.RegistrationStatus
import com.liftley.sync360.domain.service.NetworkServices
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSLock
import platform.darwin.DNSServiceBrowse
import platform.darwin.DNSServiceGetAddrInfo
import platform.darwin.DNSServiceRef
import platform.darwin.DNSServiceRefDeallocate
import platform.darwin.DNSServiceRefVar
import platform.darwin.DNSServiceRegister
import platform.darwin.DNSServiceResolve
import platform.darwin.DNSServiceSetDispatchQueue
import platform.darwin.TXTRecordCreate
import platform.darwin.TXTRecordDeallocate
import platform.darwin.TXTRecordGetBytesPtr
import platform.darwin.TXTRecordGetLength
import platform.darwin.TXTRecordGetValuePtr
import platform.darwin.TXTRecordRef
import platform.darwin.TXTRecordSetValue
import platform.darwin.dispatch_queue_create
import platform.darwin.kDNSServiceErr_NoError
import platform.darwin.kDNSServiceFlagsAdd
import platform.darwin.kDNSServiceProtocol_IPv4
import platform.darwin.kDNSServiceProtocol_IPv6
import platform.posix.NI_MAXHOST
import platform.posix.NI_NUMERICHOST
import platform.posix.getnameinfo
import platform.posix.sockaddr

@OptIn(ExperimentalForeignApi::class)
class IosNetworkServices(
    private val localDeviceInfoProvider: LocalDeviceInfoProvider
) : NetworkServices {
    private val localDevice = localDeviceInfoProvider.getLocalDeviceInfo()
    private val stateLock = NSLock()

    // This Koin singleton and its native callbacks intentionally share the app-process lifetime.
    private val callbackContext = StableRef.create(this)
    private val callbackQueue = checkNotNull(
        dispatch_queue_create(
            "com.liftley.sync360.bonjour",
            null
        )
    )

    private val _nearbyDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    override val nearbyDevices: StateFlow<List<NearbyDevice>> = _nearbyDevices.asStateFlow()

    private val _discoveryServiceStatus = MutableStateFlow(DiscoveryStatus.Idle)
    override val discoveryServiceStatus: StateFlow<DiscoveryStatus> =
        _discoveryServiceStatus.asStateFlow()

    private val _registrationServiceStatus = MutableStateFlow(RegistrationStatus.Idle)
    override val registrationServiceStatus: StateFlow<RegistrationStatus> =
        _registrationServiceStatus.asStateFlow()

    private var browseRef: DNSServiceRef? = null
    private var registrationRef: DNSServiceRef? = null
    private val serviceDetailsByKey = mutableMapOf<String, ServiceDetails>()
    private val resolveRefsByKey = mutableMapOf<String, DNSServiceRef>()
    private val addressRefsByKey = mutableMapOf<String, DNSServiceRef>()
    private var pendingRepair: PendingRepair? = null

    override suspend fun startNetworkServices(
        httpServerPort: Int,
        fileTransferPort: Int
    ) {
        locked {
            startDiscoveryService()
            startRegistrationService(httpServerPort, fileTransferPort)
        }
    }

    override suspend fun repairNetworkServices(
        httpServerPort: Int,
        fileTransferPort: Int
    ) {
        locked {
            if (!servicesAreStable()) return@locked

            pendingRepair = PendingRepair(httpServerPort, fileTransferPort)
            if (discoveryServiceStatus.value == DiscoveryStatus.Running) {
                stopDiscoveryService()
            }
            if (registrationServiceStatus.value == RegistrationStatus.Running) {
                stopRegistrationService()
            }
            continuePendingRepairIfReady()
        }
    }

    override fun restartDiscoveryServices() {
        locked {
            if (
                discoveryServiceStatus.value != DiscoveryStatus.Idle ||
                registrationServiceStatus.value != RegistrationStatus.Running
            ) {
                return@locked
            }

            clearDiscoveredServices()
            startDiscoveryService()
        }
    }

    override fun stopDiscoveryServices() {
        locked {
            if (discoveryServiceStatus.value == DiscoveryStatus.Running) {
                stopDiscoveryService()
            }
        }
    }

    private fun startDiscoveryService() {
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle) return

        _discoveryServiceStatus.value = DiscoveryStatus.Starting

        memScoped {
            val newBrowseRef = alloc<DNSServiceRefVar>()
            newBrowseRef.value = null
            val result = DNSServiceBrowse(
                sdRef = newBrowseRef.ptr,
                flags = 0u,
                interfaceIndex = 0u,
                regtype = SERVICE_TYPE,
                domain = null,
                callBack = browseCallback,
                context = callbackContext.asCPointer()
            )
            val serviceRef = newBrowseRef.value

            if (result != kDNSServiceErr_NoError || serviceRef == null) {
                serviceRef?.let { DNSServiceRefDeallocate(it) }
                _discoveryServiceStatus.value = DiscoveryStatus.Idle
                clearDiscoveredServices()
                cancelPendingRepair()
                return
            }

            browseRef = serviceRef
            val queueResult = DNSServiceSetDispatchQueue(serviceRef, callbackQueue)
            if (queueResult != kDNSServiceErr_NoError) {
                browseRef = null
                DNSServiceRefDeallocate(serviceRef)
                _discoveryServiceStatus.value = DiscoveryStatus.Idle
                clearDiscoveredServices()
                cancelPendingRepair()
                return
            }
        }

        _discoveryServiceStatus.value = DiscoveryStatus.Running
    }

    private fun stopDiscoveryService() {
        _discoveryServiceStatus.value = DiscoveryStatus.Stopping

        browseRef?.let { DNSServiceRefDeallocate(it) }
        browseRef = null
        clearDiscoveredServices()

        _discoveryServiceStatus.value = DiscoveryStatus.Idle
        continuePendingRepairIfReady()
    }

    private fun startRegistrationService(
        httpServerPort: Int,
        fileTransferPort: Int
    ) {
        if (registrationServiceStatus.value != RegistrationStatus.Idle) return

        _registrationServiceStatus.value = RegistrationStatus.Starting
        val properties = linkedMapOf(
            "deviceUuid" to localDevice.deviceId,
            "deviceName" to localDevice.deviceName,
            "deviceType" to localDevice.deviceType,
            "protocolVersion" to localDevice.protocolVersion,
            "fileTransferPort" to fileTransferPort.toString()
        )

        memScoped {
            val txtRecord = alloc<TXTRecordRef>()
            TXTRecordCreate(txtRecord.ptr, 0u, null)

            try {
                properties.forEach { (key, value) ->
                    val valueBytes = value.encodeToByteArray()
                    check(valueBytes.size <= UByte.MAX_VALUE.toInt()) {
                        "Bonjour TXT value is too large: $key"
                    }

                    val result = valueBytes.usePinned { pinnedValue ->
                        TXTRecordSetValue(
                            txtRecord = txtRecord.ptr,
                            key = key,
                            valueSize = valueBytes.size.toUByte(),
                            value = pinnedValue.addressOf(0)
                        )
                    }
                    check(result == kDNSServiceErr_NoError) {
                        "Could not add Bonjour TXT value: $key ($result)"
                    }
                }

                val newRegistrationRef = alloc<DNSServiceRefVar>()
                newRegistrationRef.value = null
                val registerResult = DNSServiceRegister(
                    sdRef = newRegistrationRef.ptr,
                    flags = 0u,
                    interfaceIndex = 0u,
                    name = "${localDevice.deviceName} Sync360",
                    regtype = SERVICE_TYPE,
                    domain = null,
                    host = null,
                    port = swapPortByteOrder(httpServerPort.toUShort()),
                    txtLen = TXTRecordGetLength(txtRecord.ptr),
                    txtRecord = TXTRecordGetBytesPtr(txtRecord.ptr),
                    callBack = registrationCallback,
                    context = callbackContext.asCPointer()
                )
                val serviceRef = newRegistrationRef.value

                if (registerResult != kDNSServiceErr_NoError || serviceRef == null) {
                    serviceRef?.let { DNSServiceRefDeallocate(it) }
                    _registrationServiceStatus.value = RegistrationStatus.Idle
                    cancelPendingRepair()
                    return
                }

                registrationRef = serviceRef
                val queueResult = DNSServiceSetDispatchQueue(serviceRef, callbackQueue)
                if (queueResult != kDNSServiceErr_NoError) {
                    registrationRef = null
                    DNSServiceRefDeallocate(serviceRef)
                    _registrationServiceStatus.value = RegistrationStatus.Idle
                    cancelPendingRepair()
                }
            } catch (exception: Throwable) {
                _registrationServiceStatus.value = RegistrationStatus.Idle
                cancelPendingRepair()
                println("Could not register iOS Bonjour service: ${exception.message}")
            } finally {
                TXTRecordDeallocate(txtRecord.ptr)
            }
        }
    }

    private fun stopRegistrationService() {
        _registrationServiceStatus.value = RegistrationStatus.Stopping

        registrationRef?.let { DNSServiceRefDeallocate(it) }
        registrationRef = null

        _registrationServiceStatus.value = RegistrationStatus.Idle
        continuePendingRepairIfReady()
    }

    private fun handleRegistrationResult(errorCode: Int) {
        locked {
            if (registrationServiceStatus.value != RegistrationStatus.Starting) return@locked

            if (errorCode == kDNSServiceErr_NoError && registrationRef != null) {
                _registrationServiceStatus.value = RegistrationStatus.Running
            } else {
                registrationRef?.let { DNSServiceRefDeallocate(it) }
                registrationRef = null
                _registrationServiceStatus.value = RegistrationStatus.Idle
                cancelPendingRepair()
            }
        }
    }

    private fun handleBrowseResult(
        flags: UInt,
        interfaceIndex: UInt,
        errorCode: Int,
        serviceName: String?,
        regtype: String?,
        domain: String?
    ) {
        locked {
            if (!discoveryIsActive()) return@locked

            if (errorCode != kDNSServiceErr_NoError) {
                browseRef?.let { DNSServiceRefDeallocate(it) }
                browseRef = null
                clearDiscoveredServices()
                _discoveryServiceStatus.value = DiscoveryStatus.Idle
                cancelPendingRepair()
                return@locked
            }

            val name = serviceName ?: return@locked
            val type = regtype ?: return@locked
            val serviceDomain = domain ?: return@locked
            val serviceKey = serviceKey(name, type, serviceDomain, interfaceIndex)

            if ((flags and kDNSServiceFlagsAdd) != 0u) {
                startResolve(
                    serviceKey = serviceKey,
                    serviceName = name,
                    regtype = type,
                    domain = serviceDomain,
                    interfaceIndex = interfaceIndex
                )
            } else {
                removeService(serviceKey)
            }
        }
    }

    private fun startResolve(
        serviceKey: String,
        serviceName: String,
        regtype: String,
        domain: String,
        interfaceIndex: UInt
    ) {
        removeResolveOperation(serviceKey)
        removeAddressOperation(serviceKey)
        serviceDetailsByKey.remove(serviceKey)
        publishDiscoveredDevices()

        memScoped {
            val newResolveRef = alloc<DNSServiceRefVar>()
            newResolveRef.value = null
            val result = DNSServiceResolve(
                sdRef = newResolveRef.ptr,
                flags = 0u,
                interfaceIndex = interfaceIndex,
                name = serviceName,
                regtype = regtype,
                domain = domain,
                callBack = resolveCallback,
                context = callbackContext.asCPointer()
            )
            val serviceRef = newResolveRef.value
            if (result != kDNSServiceErr_NoError || serviceRef == null) {
                serviceRef?.let { DNSServiceRefDeallocate(it) }
                return
            }

            serviceDetailsByKey[serviceKey] = ServiceDetails(
                serviceName = serviceName,
                serviceType = regtype,
                interfaceIndex = interfaceIndex
            )
            resolveRefsByKey[serviceKey] = serviceRef

            if (DNSServiceSetDispatchQueue(serviceRef, callbackQueue) != kDNSServiceErr_NoError) {
                removeResolveOperation(serviceKey)
                serviceDetailsByKey.remove(serviceKey)
            }
        }
    }

    private fun handleResolveResult(
        serviceRef: DNSServiceRef?,
        interfaceIndex: UInt,
        errorCode: Int,
        hostTarget: String?,
        networkPort: UShort,
        txtLength: UShort,
        txtRecord: CPointer<UByteVar>?
    ) {
        locked {
            if (!discoveryIsActive() || serviceRef == null) return@locked
            val serviceKey = resolveRefsByKey.entries
                .firstOrNull { (_, ref) -> ref == serviceRef }
                ?.key
                ?: return@locked

            if (errorCode != kDNSServiceErr_NoError || hostTarget == null) {
                removeService(serviceKey)
                return@locked
            }

            val properties = REQUIRED_TXT_KEYS.associateWith { key ->
                readTxtValue(txtLength, txtRecord, key)
            }
            val deviceUuid = properties["deviceUuid"] ?: run {
                removeService(serviceKey)
                return@locked
            }
            val deviceName = properties["deviceName"] ?: run {
                removeService(serviceKey)
                return@locked
            }
            val deviceType = properties["deviceType"] ?: run {
                removeService(serviceKey)
                return@locked
            }
            val protocolVersion = properties["protocolVersion"] ?: run {
                removeService(serviceKey)
                return@locked
            }
            val fileTransferPort = properties["fileTransferPort"]
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: run {
                    removeService(serviceKey)
                    return@locked
                }
            val httpPort = swapPortByteOrder(networkPort).toInt()
            if (httpPort <= 0) {
                removeService(serviceKey)
                return@locked
            }

            val previous = serviceDetailsByKey[serviceKey] ?: return@locked
            serviceDetailsByKey[serviceKey] = previous.copy(
                deviceId = deviceUuid,
                deviceName = deviceName,
                deviceType = deviceType,
                protocolVersion = protocolVersion,
                httpPort = httpPort,
                fileTransferPort = fileTransferPort,
                hostAddresses = emptyList()
            )
            publishDiscoveredDevices()

            startAddressLookup(
                serviceKey = serviceKey,
                hostname = hostTarget,
                interfaceIndex = interfaceIndex
            )
        }
    }

    private fun startAddressLookup(
        serviceKey: String,
        hostname: String,
        interfaceIndex: UInt
    ) {
        removeAddressOperation(serviceKey)

        memScoped {
            val newAddressRef = alloc<DNSServiceRefVar>()
            newAddressRef.value = null
            val result = DNSServiceGetAddrInfo(
                sdRef = newAddressRef.ptr,
                flags = 0u,
                interfaceIndex = interfaceIndex,
                protocol = kDNSServiceProtocol_IPv4 or kDNSServiceProtocol_IPv6,
                hostname = hostname,
                callBack = addressCallback,
                context = callbackContext.asCPointer()
            )
            val serviceRef = newAddressRef.value
            if (result != kDNSServiceErr_NoError || serviceRef == null) {
                serviceRef?.let { DNSServiceRefDeallocate(it) }
                return
            }

            addressRefsByKey[serviceKey] = serviceRef
            if (DNSServiceSetDispatchQueue(serviceRef, callbackQueue) != kDNSServiceErr_NoError) {
                removeAddressOperation(serviceKey)
            }
        }
    }

    private fun handleAddressResult(
        serviceRef: DNSServiceRef?,
        flags: UInt,
        errorCode: Int,
        address: CPointer<sockaddr>?,
        ttl: UInt
    ) {
        locked {
            if (!discoveryIsActive() || serviceRef == null) return@locked
            val serviceKey = addressRefsByKey.entries
                .firstOrNull { (_, ref) -> ref == serviceRef }
                ?.key
                ?: return@locked

            if (errorCode != kDNSServiceErr_NoError) {
                removeService(serviceKey)
                return@locked
            }
            if (address == null) return@locked
            val hostAddress = address.toNumericHost() ?: return@locked
            val serviceDetails = serviceDetailsByKey[serviceKey] ?: return@locked
            val addressWasAdded = (flags and kDNSServiceFlagsAdd) != 0u && ttl > 0u
            val addresses = if (addressWasAdded) {
                serviceDetails.hostAddresses + hostAddress
            } else {
                serviceDetails.hostAddresses - hostAddress
            }

            serviceDetailsByKey[serviceKey] = serviceDetails.copy(
                hostAddresses = addresses.distinct()
            )
            publishDiscoveredDevices()
        }
    }

    private fun publishDiscoveredDevices() {
        _nearbyDevices.value = serviceDetailsByKey.values
            .mapNotNull(ServiceDetails::toNearbyDevice)
            .filterNot { device -> device.id == localDevice.deviceId }
            .groupBy { device -> device.id }
            .values
            .map { matchingDevices ->
                val firstDevice = matchingDevices.first()
                firstDevice.copy(
                    hostAddresses = matchingDevices
                        .flatMap { device -> device.hostAddresses }
                        .distinct()
                )
            }
            .sortedBy { device -> device.deviceName.lowercase() }
    }

    private fun removeService(serviceKey: String) {
        removeResolveOperation(serviceKey)
        removeAddressOperation(serviceKey)
        serviceDetailsByKey.remove(serviceKey)
        publishDiscoveredDevices()
    }

    private fun removeResolveOperation(serviceKey: String) {
        resolveRefsByKey.remove(serviceKey)?.let { DNSServiceRefDeallocate(it) }
    }

    private fun removeAddressOperation(serviceKey: String) {
        addressRefsByKey.remove(serviceKey)?.let { DNSServiceRefDeallocate(it) }
    }

    private fun clearDiscoveredServices() {
        resolveRefsByKey.values.forEach { DNSServiceRefDeallocate(it) }
        addressRefsByKey.values.forEach { DNSServiceRefDeallocate(it) }
        resolveRefsByKey.clear()
        addressRefsByKey.clear()
        serviceDetailsByKey.clear()
        _nearbyDevices.value = emptyList()
    }

    private fun continuePendingRepairIfReady() {
        val repair = pendingRepair ?: return
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle) return
        if (registrationServiceStatus.value != RegistrationStatus.Idle) return

        pendingRepair = null
        clearDiscoveredServices()
        startDiscoveryService()
        startRegistrationService(
            httpServerPort = repair.httpServerPort,
            fileTransferPort = repair.fileTransferPort
        )
    }

    private fun cancelPendingRepair() {
        pendingRepair = null
    }

    private fun servicesAreStable(): Boolean {
        val discoveryStable =
            discoveryServiceStatus.value == DiscoveryStatus.Idle ||
                discoveryServiceStatus.value == DiscoveryStatus.Running
        val registrationStable =
            registrationServiceStatus.value == RegistrationStatus.Idle ||
                registrationServiceStatus.value == RegistrationStatus.Running
        return discoveryStable && registrationStable
    }

    private fun discoveryIsActive(): Boolean {
        return discoveryServiceStatus.value == DiscoveryStatus.Starting ||
            discoveryServiceStatus.value == DiscoveryStatus.Running
    }

    private fun readTxtValue(
        txtLength: UShort,
        txtRecord: CPointer<UByteVar>?,
        key: String
    ): String? {
        if (txtRecord == null) return null

        return memScoped {
            val valueLength = alloc<UByteVar>()
            val valuePointer = TXTRecordGetValuePtr(
                txtLen = txtLength,
                txtRecord = txtRecord,
                key = key,
                valueLen = valueLength.ptr
            ) ?: return@memScoped null

            valuePointer
                .reinterpret<ByteVar>()
                .readBytes(valueLength.value.toInt())
                .decodeToString()
        }
    }

    private fun CPointer<sockaddr>.toNumericHost(): String? {
        return memScoped {
            val hostBuffer = allocArray<ByteVar>(NI_MAXHOST)
            val result = getnameinfo(
                this@toNumericHost,
                pointed.sa_len.convert(),
                hostBuffer,
                NI_MAXHOST.convert(),
                null,
                0u,
                NI_NUMERICHOST
            )
            if (result == 0) hostBuffer.toKString() else null
        }
    }

    private fun serviceKey(
        serviceName: String,
        regtype: String,
        domain: String,
        interfaceIndex: UInt
    ): String {
        return "$serviceName|$regtype|$domain|$interfaceIndex".lowercase()
    }

    private fun swapPortByteOrder(port: UShort): UShort {
        val value = port.toInt()
        return (
            ((value and 0x00FF) shl 8) or
                ((value and 0xFF00) ushr 8)
            ).toUShort()
    }

    private inline fun <T> locked(block: () -> T): T {
        stateLock.lock()
        return try {
            block()
        } finally {
            stateLock.unlock()
        }
    }

    private data class PendingRepair(
        val httpServerPort: Int,
        val fileTransferPort: Int
    )

    private data class ServiceDetails(
        val serviceName: String,
        val serviceType: String,
        val interfaceIndex: UInt,
        val deviceId: String? = null,
        val deviceName: String? = null,
        val deviceType: String? = null,
        val protocolVersion: String? = null,
        val httpPort: Int? = null,
        val fileTransferPort: Int? = null,
        val hostAddresses: List<String> = emptyList()
    ) {
        fun toNearbyDevice(): NearbyDevice? {
            val resolvedDeviceId = deviceId ?: return null
            val resolvedDeviceName = deviceName ?: return null
            val resolvedDeviceType = deviceType ?: return null
            val resolvedProtocolVersion = protocolVersion ?: return null
            val resolvedHttpPort = httpPort ?: return null
            val resolvedFileTransferPort = fileTransferPort ?: return null
            if (hostAddresses.isEmpty()) return null

            return NearbyDevice(
                id = resolvedDeviceId,
                deviceName = resolvedDeviceName,
                deviceType = resolvedDeviceType,
                protocolVersion = resolvedProtocolVersion,
                hostAddresses = hostAddresses,
                port = resolvedHttpPort,
                fileTransferPort = resolvedFileTransferPort,
                serviceName = serviceName,
                serviceType = "${serviceType.trimEnd('.')}."
            )
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_sync360._tcp"
        val REQUIRED_TXT_KEYS = listOf(
            "deviceUuid",
            "deviceName",
            "deviceType",
            "protocolVersion",
            "fileTransferPort"
        )

        val browseCallback = staticCFunction {
                _: DNSServiceRef?,
                flags: UInt,
                interfaceIndex: UInt,
                errorCode: Int,
                serviceName: CPointer<ByteVar>?,
                regtype: CPointer<ByteVar>?,
                domain: CPointer<ByteVar>?,
                context: COpaquePointer? ->
            context?.asStableRef<IosNetworkServices>()?.get()?.handleBrowseResult(
                flags = flags,
                interfaceIndex = interfaceIndex,
                errorCode = errorCode,
                serviceName = serviceName?.toKString(),
                regtype = regtype?.toKString(),
                domain = domain?.toKString()
            )
            Unit
        }

        val resolveCallback = staticCFunction {
                serviceRef: DNSServiceRef?,
                _: UInt,
                interfaceIndex: UInt,
                errorCode: Int,
                _: CPointer<ByteVar>?,
                hostTarget: CPointer<ByteVar>?,
                port: UShort,
                txtLength: UShort,
                txtRecord: CPointer<UByteVar>?,
                context: COpaquePointer? ->
            context?.asStableRef<IosNetworkServices>()?.get()?.handleResolveResult(
                serviceRef = serviceRef,
                interfaceIndex = interfaceIndex,
                errorCode = errorCode,
                hostTarget = hostTarget?.toKString(),
                networkPort = port,
                txtLength = txtLength,
                txtRecord = txtRecord
            )
            Unit
        }

        val addressCallback = staticCFunction {
                serviceRef: DNSServiceRef?,
                flags: UInt,
                _: UInt,
                errorCode: Int,
                _: CPointer<ByteVar>?,
                address: CPointer<sockaddr>?,
                ttl: UInt,
                context: COpaquePointer? ->
            context?.asStableRef<IosNetworkServices>()?.get()?.handleAddressResult(
                serviceRef = serviceRef,
                flags = flags,
                errorCode = errorCode,
                address = address,
                ttl = ttl
            )
            Unit
        }

        val registrationCallback = staticCFunction {
                _: DNSServiceRef?,
                _: UInt,
                errorCode: Int,
                _: CPointer<ByteVar>?,
                _: CPointer<ByteVar>?,
                _: CPointer<ByteVar>?,
                context: COpaquePointer? ->
            context?.asStableRef<IosNetworkServices>()?.get()
                ?.handleRegistrationResult(errorCode)
            Unit
        }
    }
}
