package com.liftley.sync360.data.network.discovery.windows

import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.RegistrationStatus
import com.liftley.sync360.domain.service.NetworkServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong

class WindowsNetworkServices(
    private val localDeviceInfoProvider: LocalDeviceInfoProvider
) : NetworkServices {
    private val dnsApi = WindowsDnsSdApi()
    private val callbackLookup = MethodHandles.lookup()
    private val browseCallback = nativeCallback("onNativeBrowseResult")
    private val resolveCallback = nativeCallback("onNativeResolveResult")
    private val registrationCallback = nativeCallback("onNativeRegistrationResult")

    private val _nearbyDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    override val nearbyDevices: StateFlow<List<NearbyDevice>> = _nearbyDevices.asStateFlow()

    private val _discoveryServiceStatus = MutableStateFlow(DiscoveryStatus.Idle)
    override val discoveryServiceStatus: StateFlow<DiscoveryStatus> =
        _discoveryServiceStatus.asStateFlow()

    private val _registrationServiceStatus = MutableStateFlow(RegistrationStatus.Idle)
    override val registrationServiceStatus: StateFlow<RegistrationStatus> =
        _registrationServiceStatus.asStateFlow()

    private var browseOperation: BrowseOperation? = null
    private var registrationOperation: RegistrationOperation? = null
    private val resolveOperationIds = AtomicLong()
    private val resolveOperationsByService = mutableMapOf<String, ResolveOperation>()
    private val resolveOperationsById = mutableMapOf<Long, ResolveOperation>()
    private val resolvedDevicesByServiceKey = mutableMapOf<String, NearbyDevice>()
    // Keep native request memory alive after terminal callbacks because the
    // Windows callback is still unwinding when Kotlin receives it.
    private val retiredNativeArenas = mutableListOf<Arena>()
    private var pendingRepair: PendingRepair? = null

    override suspend fun startNetworkServices(
        httpServerPort: Int,
        fileTransferPort: Int
    ) {
        synchronized(this) {
            startDiscoveryService()
            startRegistrationService(httpServerPort, fileTransferPort)
        }
    }

    override suspend fun repairNetworkServices(
        httpServerPort: Int,
        fileTransferPort: Int
    ) {
        synchronized(this) {
            val discoveryIsStable =
                discoveryServiceStatus.value == DiscoveryStatus.Idle ||
                    discoveryServiceStatus.value == DiscoveryStatus.Running
            val registrationIsStable =
                registrationServiceStatus.value == RegistrationStatus.Idle ||
                    registrationServiceStatus.value == RegistrationStatus.Running

            if (!discoveryIsStable || !registrationIsStable) return

            pendingRepair = PendingRepair(httpServerPort, fileTransferPort)

            if (discoveryServiceStatus.value == DiscoveryStatus.Running) {
                stopDiscoveryServices()
            }
            if (pendingRepair == null) return

            if (registrationServiceStatus.value == RegistrationStatus.Running) {
                stopRegistrationService()
            }
            if (pendingRepair == null) return

            continuePendingRepairIfReady()
        }
    }

    override fun restartDiscoveryServices() {
        synchronized(this) {
            if (discoveryServiceStatus.value != DiscoveryStatus.Idle) return
            if (registrationServiceStatus.value != RegistrationStatus.Running) return

            clearResolvedDevices()
            startDiscoveryService()
        }
    }

    override fun stopDiscoveryServices() {
        synchronized(this) {
            if (discoveryServiceStatus.value != DiscoveryStatus.Running) return

            val operation = browseOperation ?: run {
                _discoveryServiceStatus.value = DiscoveryStatus.Idle
                clearResolvedDevices()
                continuePendingRepairIfReady()
                return
            }

            _discoveryServiceStatus.value = DiscoveryStatus.Stopping
            clearResolvedDevices()
            cancelResolveOperations()

            val result = runCatching {
                dnsApi.cancelBrowse(operation.cancel)
            }.getOrElse { exception ->
                logFailure("Could not stop Windows DNS-SD discovery", exception)
                ERROR_CANCELLED
            }

            if (result != ERROR_SUCCESS) {
                _discoveryServiceStatus.value = DiscoveryStatus.Running
                cancelPendingRepair()
                logStatus("DnsServiceBrowseCancel", result)
            }
        }
    }

    private fun startDiscoveryService() {
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle) return

        _discoveryServiceStatus.value = DiscoveryStatus.Starting
        val arena = Arena.ofShared()
        val request = arena.allocateZeroed(WindowsDnsLayouts.BROWSE_REQUEST_SIZE).apply {
            set(ValueLayout.JAVA_INT, WindowsDnsLayouts.VERSION_OFFSET, DNS_REQUEST_VERSION_1)
            set(ValueLayout.JAVA_INT, WindowsDnsLayouts.INTERFACE_INDEX_OFFSET, ALL_INTERFACES)
            set(
                ValueLayout.ADDRESS,
                WindowsDnsLayouts.BROWSE_QUERY_NAME_OFFSET,
                arena.allocateWideString(SERVICE_QUERY)
            )
            set(
                ValueLayout.ADDRESS,
                WindowsDnsLayouts.BROWSE_CALLBACK_OFFSET,
                browseCallback
            )
        }
        val cancel = arena.allocateZeroed(WindowsDnsLayouts.CANCEL_SIZE)
        val operation = BrowseOperation(arena, request, cancel)
        browseOperation = operation

        val result = runCatching {
            dnsApi.browse(request, cancel)
        }.getOrElse { exception ->
            logFailure("Could not start Windows DNS-SD discovery", exception)
            ERROR_CANCELLED
        }

        if (result == DNS_REQUEST_PENDING) {
            if (discoveryServiceStatus.value == DiscoveryStatus.Starting) {
                _discoveryServiceStatus.value = DiscoveryStatus.Running
            }
        } else {
            browseOperation = null
            operation.arena.close()
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
            clearResolvedDevices()
            cancelPendingRepair()
            logStatus("DnsServiceBrowse", result)
        }
    }

    @Suppress("unused")
    private fun onNativeBrowseResult(
        status: Int,
        queryContext: MemorySegment,
        records: MemorySegment
    ) {
        runCatching {
            handleBrowseResult(status, records)
        }.onFailure { exception ->
            logFailure("Windows DNS-SD browse callback failed", exception)
        }
    }

    @Synchronized
    private fun handleBrowseResult(status: Int, records: MemorySegment) {
        if (status == ERROR_CANCELLED) {
            freeDnsRecords(records)
            browseOperation?.arena?.let(::retireNativeArena)
            browseOperation = null
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
            clearResolvedDevices()
            continuePendingRepairIfReady()
            return
        }

        if (status != ERROR_SUCCESS) {
            freeDnsRecords(records)
            browseOperation?.arena?.let(::retireNativeArena)
            browseOperation = null
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
            clearResolvedDevices()
            cancelResolveOperations()
            cancelPendingRepair()
            logStatus("Windows DNS-SD browse callback", status)
            return
        }

        if (!discoveryIsActive()) {
            freeDnsRecords(records)
            return
        }

        try {
            var recordPointer = records
            while (!recordPointer.isNullPointer()) {
                val record = recordPointer.reinterpret(WindowsDnsLayouts.RECORD_READABLE_SIZE)
                val recordType = record.get(
                    ValueLayout.JAVA_SHORT,
                    WindowsDnsLayouts.RECORD_TYPE_OFFSET
                ).toInt() and 0xFFFF

                if (recordType == DNS_TYPE_PTR) {
                    val serviceName = record.get(
                        ValueLayout.ADDRESS,
                        WindowsDnsLayouts.RECORD_DATA_OFFSET
                    ).readWideString()
                    if (!serviceName.isNullOrBlank()) {
                        val ttl = record.get(
                            ValueLayout.JAVA_INT,
                            WindowsDnsLayouts.RECORD_TTL_OFFSET
                        )
                        if (ttl == 0) {
                            removeService(serviceName)
                        } else {
                            startResolveService(serviceName)
                        }
                    }
                }

                recordPointer = record.get(
                    ValueLayout.ADDRESS,
                    WindowsDnsLayouts.RECORD_NEXT_OFFSET
                )
            }
        } finally {
            freeDnsRecords(records)
        }
    }

    private fun startResolveService(serviceName: String) {
        val serviceKey = serviceName.normalizedServiceKey()
        if (resolveOperationsByService.containsKey(serviceKey)) return

        val operationId = resolveOperationIds.incrementAndGet()
        val arena = Arena.ofShared()
        val context = arena.allocate(ValueLayout.JAVA_LONG).apply {
            set(ValueLayout.JAVA_LONG, 0, operationId)
        }
        val request = arena.allocateZeroed(WindowsDnsLayouts.RESOLVE_REQUEST_SIZE).apply {
            set(ValueLayout.JAVA_INT, WindowsDnsLayouts.VERSION_OFFSET, DNS_REQUEST_VERSION_1)
            set(ValueLayout.JAVA_INT, WindowsDnsLayouts.INTERFACE_INDEX_OFFSET, ALL_INTERFACES)
            set(
                ValueLayout.ADDRESS,
                WindowsDnsLayouts.RESOLVE_QUERY_NAME_OFFSET,
                arena.allocateWideString(serviceName)
            )
            set(
                ValueLayout.ADDRESS,
                WindowsDnsLayouts.RESOLVE_CALLBACK_OFFSET,
                resolveCallback
            )
            set(
                ValueLayout.ADDRESS,
                WindowsDnsLayouts.RESOLVE_CONTEXT_OFFSET,
                context
            )
        }
        val cancel = arena.allocateZeroed(WindowsDnsLayouts.CANCEL_SIZE)
        val operation = ResolveOperation(
            id = operationId,
            serviceName = serviceName,
            arena = arena,
            request = request,
            cancel = cancel
        )
        resolveOperationsByService[serviceKey] = operation
        resolveOperationsById[operationId] = operation

        val result = runCatching {
            dnsApi.resolve(request, cancel)
        }.getOrElse { exception ->
            logFailure("Could not resolve $serviceName through Windows DNS-SD", exception)
            ERROR_CANCELLED
        }

        if (result != DNS_REQUEST_PENDING) {
            removeResolveOperation(operation, retireArena = false)
            logStatus("DnsServiceResolve", result)
        }
    }

    @Suppress("unused")
    private fun onNativeResolveResult(
        status: Int,
        queryContext: MemorySegment,
        instance: MemorySegment
    ) {
        if (queryContext.isNullPointer()) {
            if (!instance.isNullPointer()) {
                dnsApi.freeInstance(instance)
            }
            return
        }

        runCatching {
            val operationId = queryContext
                .reinterpret(ValueLayout.JAVA_LONG.byteSize())
                .get(ValueLayout.JAVA_LONG, 0)
            handleResolveResult(operationId, status, instance)
        }.onFailure { exception ->
            logFailure("Windows DNS-SD resolve callback failed", exception)
        }
    }

    @Synchronized
    private fun handleResolveResult(
        operationId: Long,
        status: Int,
        instancePointer: MemorySegment
    ) {
        try {
            val operation = resolveOperationsById[operationId] ?: return

            if (status == ERROR_CANCELLED) {
                removeResolveOperation(operation, retireArena = true)
                return
            }

            if (status != ERROR_SUCCESS) {
                removeResolveOperation(operation, retireArena = true)
                logStatus("Windows DNS-SD resolve callback", status)
                return
            }

            val activeOperation = resolveOperationsByService[
                operation.serviceName.normalizedServiceKey()
            ]
            if (activeOperation?.id != operation.id) return

            if (!discoveryIsActive()) return

            if (instancePointer.isNullPointer()) return
            val instance = instancePointer.reinterpret(WindowsDnsLayouts.INSTANCE_READABLE_SIZE)
            val nearbyDevice = instance.toNearbyDevice() ?: return
            val localDeviceId = localDeviceInfoProvider.getLocalDeviceInfo().deviceId
            if (nearbyDevice.id == localDeviceId) return

            val interfaceIndex = instance.get(
                ValueLayout.JAVA_INT,
                WindowsDnsLayouts.INSTANCE_INTERFACE_INDEX_OFFSET
            )
            val resolvedKey =
                "${operation.serviceName.normalizedServiceKey()}|$interfaceIndex"
            resolvedDevicesByServiceKey[resolvedKey] = nearbyDevice
            publishMergedDevices()
        } finally {
            if (!instancePointer.isNullPointer()) {
                dnsApi.freeInstance(instancePointer)
            }
        }
    }

    private fun startRegistrationService(
        httpServerPort: Int,
        fileTransferPort: Int
    ) {
        if (registrationServiceStatus.value != RegistrationStatus.Idle) return

        _registrationServiceStatus.value = RegistrationStatus.Starting
        val localDevice = localDeviceInfoProvider.getLocalDeviceInfo()
        val properties = linkedMapOf(
            "deviceUuid" to localDevice.deviceId,
            "deviceName" to localDevice.deviceName,
            "deviceType" to localDevice.deviceType,
            "protocolVersion" to localDevice.protocolVersion,
            "fileTransferPort" to fileTransferPort.toString()
        )
        val serviceInstance = Arena.ofConfined().use { temporaryArena ->
            val keys = temporaryArena.allocatePointerArray(properties.keys)
            val values = temporaryArena.allocatePointerArray(properties.values)
            runCatching {
                dnsApi.constructInstance(
                    serviceName = temporaryArena.allocateWideString(
                        "${localDevice.deviceName} Sync360.$SERVICE_QUERY"
                    ),
                    hostName = temporaryArena.allocateWideString(localHostName()),
                    port = httpServerPort.toShort(),
                    propertyCount = properties.size,
                    keys = keys,
                    values = values
                )
            }.getOrElse { exception ->
                logFailure("Could not create the Windows DNS-SD service", exception)
                MemorySegment.NULL
            }
        }

        if (serviceInstance.isNullPointer()) {
            _registrationServiceStatus.value = RegistrationStatus.Idle
            cancelPendingRepair()
            return
        }

        val arena = Arena.ofShared()
        val request = arena.allocateZeroed(WindowsDnsLayouts.REGISTER_REQUEST_SIZE).apply {
            set(ValueLayout.JAVA_INT, WindowsDnsLayouts.VERSION_OFFSET, DNS_REQUEST_VERSION_1)
            set(ValueLayout.JAVA_INT, WindowsDnsLayouts.INTERFACE_INDEX_OFFSET, ALL_INTERFACES)
            set(
                ValueLayout.ADDRESS,
                WindowsDnsLayouts.REGISTER_INSTANCE_OFFSET,
                serviceInstance
            )
            set(
                ValueLayout.ADDRESS,
                WindowsDnsLayouts.REGISTER_CALLBACK_OFFSET,
                registrationCallback
            )
        }
        val operation = RegistrationOperation(
            arena = arena,
            request = request,
            serviceInstance = serviceInstance,
            action = RegistrationAction.Registering
        )
        registrationOperation = operation

        val result = runCatching {
            dnsApi.register(request)
        }.getOrElse { exception ->
            logFailure("Could not register the Windows DNS-SD service", exception)
            ERROR_CANCELLED
        }

        if (result != DNS_REQUEST_PENDING) {
            releaseRegistrationOperation(retireArena = false)
            _registrationServiceStatus.value = RegistrationStatus.Idle
            cancelPendingRepair()
            logStatus("DnsServiceRegister", result)
        }
    }

    @Suppress("unused")
    private fun onNativeRegistrationResult(
        status: Int,
        queryContext: MemorySegment,
        instance: MemorySegment
    ) {
        runCatching {
            handleRegistrationResult(status, instance)
        }.onFailure { exception ->
            logFailure("Windows DNS-SD registration callback failed", exception)
        }
    }

    @Synchronized
    private fun handleRegistrationResult(
        status: Int,
        instancePointer: MemorySegment
    ) {
        try {
            val operation = registrationOperation ?: return

            when (operation.action) {
                RegistrationAction.Registering -> {
                    if (status == ERROR_SUCCESS) {
                        _registrationServiceStatus.value = RegistrationStatus.Running
                    } else {
                        releaseRegistrationOperation(retireArena = true)
                        _registrationServiceStatus.value = RegistrationStatus.Idle
                        cancelPendingRepair()
                        logStatus("Windows DNS-SD registration callback", status)
                    }
                }

                RegistrationAction.Deregistering -> {
                    if (status == ERROR_SUCCESS || status == ERROR_CANCELLED) {
                        releaseRegistrationOperation(retireArena = true)
                        _registrationServiceStatus.value = RegistrationStatus.Idle
                        continuePendingRepairIfReady()
                    } else {
                        operation.action = RegistrationAction.Registering
                        _registrationServiceStatus.value = RegistrationStatus.Running
                        cancelPendingRepair()
                        logStatus("Windows DNS-SD deregistration callback", status)
                    }
                }

            }
        } finally {
            if (!instancePointer.isNullPointer()) {
                dnsApi.freeInstance(instancePointer)
            }
        }
    }

    private fun stopRegistrationService() {
        if (registrationServiceStatus.value != RegistrationStatus.Running) return

        val operation = registrationOperation ?: run {
            _registrationServiceStatus.value = RegistrationStatus.Idle
            continuePendingRepairIfReady()
            return
        }

        _registrationServiceStatus.value = RegistrationStatus.Stopping
        operation.action = RegistrationAction.Deregistering

        val result = runCatching {
            dnsApi.deregister(operation.request)
        }.getOrElse { exception ->
            logFailure("Could not deregister the Windows DNS-SD service", exception)
            ERROR_CANCELLED
        }

        if (result != DNS_REQUEST_PENDING) {
            operation.action = RegistrationAction.Registering
            _registrationServiceStatus.value = RegistrationStatus.Running
            cancelPendingRepair()
            logStatus("DnsServiceDeRegister", result)
        }
    }

    private fun MemorySegment.toNearbyDevice(): NearbyDevice? {
        val properties = readProperties()
        val deviceUuid = properties["deviceUuid"] ?: return null
        val deviceName = properties["deviceName"] ?: return null
        val deviceType = properties["deviceType"] ?: return null
        val protocolVersion = properties["protocolVersion"] ?: return null
        val fileTransferPort = properties["fileTransferPort"]
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: return null
        val httpPort = get(
            ValueLayout.JAVA_SHORT,
            WindowsDnsLayouts.INSTANCE_PORT_OFFSET
        ).toInt() and 0xFFFF
        if (httpPort <= 0) return null

        val interfaceIndex = get(
            ValueLayout.JAVA_INT,
            WindowsDnsLayouts.INSTANCE_INTERFACE_INDEX_OFFSET
        )
        val addresses = buildList {
            get(
                ValueLayout.ADDRESS,
                WindowsDnsLayouts.INSTANCE_IPV4_OFFSET
            ).readAddress(IPV4_ADDRESS_SIZE, interfaceIndex)?.let(::add)
            get(
                ValueLayout.ADDRESS,
                WindowsDnsLayouts.INSTANCE_IPV6_OFFSET
            ).readAddress(IPV6_ADDRESS_SIZE, interfaceIndex)?.let(::add)
        }.distinct()
        if (addresses.isEmpty()) return null

        val fullServiceName = get(
            ValueLayout.ADDRESS,
            WindowsDnsLayouts.INSTANCE_NAME_OFFSET
        ).readWideString() ?: return null
        val normalizedServiceName = fullServiceName.trimEnd('.')
        val serviceSuffix = ".$SERVICE_QUERY"
        val serviceName = if (
            normalizedServiceName.endsWith(serviceSuffix, ignoreCase = true)
        ) {
            normalizedServiceName.dropLast(serviceSuffix.length)
        } else {
            normalizedServiceName
        }

        return NearbyDevice(
            id = deviceUuid,
            deviceName = deviceName,
            deviceType = deviceType,
            protocolVersion = protocolVersion,
            hostAddresses = addresses,
            port = httpPort,
            fileTransferPort = fileTransferPort,
            serviceName = serviceName,
            serviceType = ANDROID_STYLE_SERVICE_TYPE
        )
    }

    private fun MemorySegment.readProperties(): Map<String, String> {
        val propertyCount = get(
            ValueLayout.JAVA_INT,
            WindowsDnsLayouts.INSTANCE_PROPERTY_COUNT_OFFSET
        ).coerceIn(0, MAX_PROPERTY_COUNT)
        if (propertyCount == 0) return emptyMap()

        val keys = get(
            ValueLayout.ADDRESS,
            WindowsDnsLayouts.INSTANCE_KEYS_OFFSET
        )
        val values = get(
            ValueLayout.ADDRESS,
            WindowsDnsLayouts.INSTANCE_VALUES_OFFSET
        )
        if (keys.isNullPointer() || values.isNullPointer()) return emptyMap()

        val arraySize = propertyCount.toLong() * WindowsDnsLayouts.POINTER_BYTE_SIZE
        val keyArray = keys.reinterpret(arraySize)
        val valueArray = values.reinterpret(arraySize)

        return buildMap {
            repeat(propertyCount) { index ->
                val key = keyArray
                    .getAtIndex(ValueLayout.ADDRESS, index.toLong())
                    .readWideString()
                val value = valueArray
                    .getAtIndex(ValueLayout.ADDRESS, index.toLong())
                    .readWideString()
                if (!key.isNullOrBlank() && value != null) {
                    put(key, value)
                }
            }
        }
    }

    private fun Arena.allocatePointerArray(values: Collection<String>): MemorySegment {
        val pointers = allocate(ValueLayout.ADDRESS, values.size.toLong())
        values.forEachIndexed { index, value ->
            pointers.setAtIndex(
                ValueLayout.ADDRESS,
                index.toLong(),
                allocateWideString(value)
            )
        }
        return pointers
    }

    private fun MemorySegment.readAddress(size: Int, interfaceIndex: Int): String? {
        if (isNullPointer()) return null

        return runCatching {
            val bytes = reinterpret(size.toLong()).toArray(ValueLayout.JAVA_BYTE)
            val address = InetAddress.getByAddress(bytes)

            if (
                address is Inet6Address &&
                address.isLinkLocalAddress &&
                interfaceIndex > 0
            ) {
                Inet6Address.getByAddress(null, bytes, interfaceIndex).hostAddress
            } else {
                address.hostAddress
            }
        }.getOrNull()
    }

    private fun removeService(serviceName: String) {
        val serviceKey = serviceName.normalizedServiceKey()
        resolvedDevicesByServiceKey.keys
            .filter { key -> key.startsWith("$serviceKey|") }
            .forEach(resolvedDevicesByServiceKey::remove)

        resolveOperationsByService[serviceKey]?.let(::cancelResolveOperation)
        publishMergedDevices()
    }

    private fun cancelResolveOperations() {
        resolveOperationsByService.values
            .toList()
            .forEach(::cancelResolveOperation)
    }

    private fun cancelResolveOperation(operation: ResolveOperation) {
        resolveOperationsByService.remove(operation.serviceName.normalizedServiceKey())
        val result = runCatching {
            dnsApi.cancelResolve(operation.cancel)
        }.getOrElse { exception ->
            logFailure("Could not cancel a Windows DNS-SD resolve operation", exception)
            ERROR_CANCELLED
        }

        if (result != ERROR_SUCCESS) {
            logStatus("DnsServiceResolveCancel", result)
        }
    }

    private fun removeResolveOperation(
        operation: ResolveOperation,
        retireArena: Boolean
    ) {
        val serviceKey = operation.serviceName.normalizedServiceKey()
        if (resolveOperationsByService[serviceKey]?.id == operation.id) {
            resolveOperationsByService.remove(serviceKey)
        }
        resolveOperationsById.remove(operation.id)
        if (retireArena) {
            retireNativeArena(operation.arena)
        } else {
            operation.arena.close()
        }
    }

    private fun clearResolvedDevices() {
        resolvedDevicesByServiceKey.clear()
        _nearbyDevices.value = emptyList()
    }

    private fun publishMergedDevices() {
        _nearbyDevices.value = resolvedDevicesByServiceKey.values
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

    private fun releaseRegistrationOperation(retireArena: Boolean) {
        val operation = registrationOperation ?: return
        registrationOperation = null
        dnsApi.freeInstance(operation.serviceInstance)
        if (retireArena) {
            retireNativeArena(operation.arena)
        } else {
            operation.arena.close()
        }
    }

    private fun retireNativeArena(arena: Arena) {
        retiredNativeArenas += arena
    }

    private fun continuePendingRepairIfReady() {
        val repair = pendingRepair ?: return
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle) return
        if (registrationServiceStatus.value != RegistrationStatus.Idle) return

        pendingRepair = null
        clearResolvedDevices()
        startDiscoveryService()
        startRegistrationService(
            httpServerPort = repair.httpServerPort,
            fileTransferPort = repair.fileTransferPort
        )
    }

    private fun cancelPendingRepair() {
        pendingRepair = null
    }

    private fun freeDnsRecords(records: MemorySegment) {
        if (!records.isNullPointer()) {
            dnsApi.freeRecordList(records)
        }
    }

    private fun localHostName(): String {
        val rawHostName = runCatching {
            InetAddress.getLocalHost().hostName
        }.getOrNull()
            ?.substringBefore('.')
            ?.replace(Regex("[^A-Za-z0-9-]"), "-")
            ?.trim('-')
            ?.take(63)
            ?.takeIf { it.isNotBlank() }
            ?: "sync360"

        return "$rawHostName.local"
    }

    private fun String.normalizedServiceKey(): String {
        return trimEnd('.').lowercase()
    }

    private fun nativeCallback(methodName: String): MemorySegment {
        val callback = callbackLookup.findVirtual(
            WindowsNetworkServices::class.java,
            methodName,
            NATIVE_CALLBACK_TYPE
        ).bindTo(this)
        return dnsApi.createCallback(callback)
    }

    private fun discoveryIsActive(): Boolean {
        return discoveryServiceStatus.value == DiscoveryStatus.Starting ||
            discoveryServiceStatus.value == DiscoveryStatus.Running
    }

    private fun MemorySegment.isNullPointer(): Boolean {
        return address() == 0L
    }

    private fun logStatus(operation: String, status: Int) {
        if (status != ERROR_SUCCESS && status != ERROR_CANCELLED) {
            System.err.println("$operation failed with Windows status $status")
        }
    }

    private fun logFailure(message: String, exception: Throwable) {
        System.err.println("$message: ${exception.message}")
        exception.printStackTrace()
    }

    private data class BrowseOperation(
        val arena: Arena,
        val request: MemorySegment,
        val cancel: MemorySegment
    )

    private data class ResolveOperation(
        val id: Long,
        val serviceName: String,
        val arena: Arena,
        val request: MemorySegment,
        val cancel: MemorySegment
    )

    private data class RegistrationOperation(
        val arena: Arena,
        val request: MemorySegment,
        val serviceInstance: MemorySegment,
        var action: RegistrationAction
    )

    private enum class RegistrationAction {
        Registering,
        Deregistering
    }

    private data class PendingRepair(
        val httpServerPort: Int,
        val fileTransferPort: Int
    )

    private companion object {
        val NATIVE_CALLBACK_TYPE: MethodType = MethodType.methodType(
            Void.TYPE,
            Int::class.javaPrimitiveType,
            MemorySegment::class.java,
            MemorySegment::class.java
        )

        const val SERVICE_QUERY = "_sync360._tcp.local"
        const val ANDROID_STYLE_SERVICE_TYPE = "_sync360._tcp."
        const val ALL_INTERFACES = 0
        const val DNS_REQUEST_VERSION_1 = 1
        const val DNS_REQUEST_PENDING = 9506
        const val DNS_TYPE_PTR = 12
        const val ERROR_SUCCESS = 0
        const val ERROR_CANCELLED = 1223
        const val IPV4_ADDRESS_SIZE = 4
        const val IPV6_ADDRESS_SIZE = 16
        const val MAX_PROPERTY_COUNT = 64
    }
}
