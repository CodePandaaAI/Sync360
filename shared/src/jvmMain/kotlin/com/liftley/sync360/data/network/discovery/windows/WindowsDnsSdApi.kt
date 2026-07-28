package com.liftley.sync360.data.network.discovery.windows

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.charset.StandardCharsets.UTF_16LE

internal class WindowsDnsSdApi {
    init {
        require(ValueLayout.ADDRESS.byteSize() == 8L) {
            "Windows DNS-SD requires a 64-bit Desktop JVM"
        }
    }

    private val arena = Arena.ofShared()
    private val linker = Linker.nativeLinker()
    private val symbols = SymbolLookup.libraryLookup("dnsapi", arena)

    private val browse = downcall("DnsServiceBrowse", REQUEST_DESCRIPTOR)
    private val browseCancel = downcall("DnsServiceBrowseCancel", CANCEL_DESCRIPTOR)
    private val resolve = downcall("DnsServiceResolve", REQUEST_DESCRIPTOR)
    private val resolveCancel = downcall("DnsServiceResolveCancel", CANCEL_DESCRIPTOR)
    private val constructInstance = downcall(
        "DnsServiceConstructInstance",
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_SHORT,
            ValueLayout.JAVA_SHORT,
            ValueLayout.JAVA_SHORT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS
        )
    )
    private val register = downcall("DnsServiceRegister", REQUEST_DESCRIPTOR)
    private val deregister = downcall("DnsServiceDeRegister", REQUEST_DESCRIPTOR)
    private val freeInstance = downcall(
        "DnsServiceFreeInstance",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    )
    private val freeRecordList = downcall(
        "DnsRecordListFree",
        FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT
        )
    )

    fun createCallback(callback: MethodHandle): MemorySegment =
        linker.upcallStub(callback, CALLBACK_DESCRIPTOR, arena)

    fun browse(request: MemorySegment, cancel: MemorySegment): Int =
        browse.invokeWithArguments(request, cancel) as Int

    fun cancelBrowse(cancel: MemorySegment): Int =
        browseCancel.invokeWithArguments(cancel) as Int

    fun resolve(request: MemorySegment, cancel: MemorySegment): Int =
        resolve.invokeWithArguments(request, cancel) as Int

    fun cancelResolve(cancel: MemorySegment): Int =
        resolveCancel.invokeWithArguments(cancel) as Int

    fun constructInstance(
        serviceName: MemorySegment,
        hostName: MemorySegment,
        port: Short,
        propertyCount: Int,
        keys: MemorySegment,
        values: MemorySegment
    ): MemorySegment {
        return constructInstance.invokeWithArguments(
            serviceName,
            hostName,
            MemorySegment.NULL,
            MemorySegment.NULL,
            port,
            0.toShort(),
            0.toShort(),
            propertyCount,
            keys,
            values
        ) as MemorySegment
    }

    fun register(request: MemorySegment): Int =
        register.invokeWithArguments(request, MemorySegment.NULL) as Int

    fun deregister(request: MemorySegment): Int =
        deregister.invokeWithArguments(request, MemorySegment.NULL) as Int

    fun freeInstance(instance: MemorySegment) {
        freeInstance.invokeWithArguments(instance)
    }

    fun freeRecordList(records: MemorySegment) {
        freeRecordList.invokeWithArguments(records, DNS_FREE_RECORD_LIST)
    }

    private fun downcall(
        name: String,
        descriptor: FunctionDescriptor
    ): MethodHandle {
        val symbol = symbols.find(name).orElseThrow {
            UnsatisfiedLinkError("$name is unavailable in dnsapi.dll")
        }
        return linker.downcallHandle(symbol, descriptor)
    }

    private companion object {
        val REQUEST_DESCRIPTOR: FunctionDescriptor = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS
        )
        val CANCEL_DESCRIPTOR: FunctionDescriptor = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS
        )
        val CALLBACK_DESCRIPTOR: FunctionDescriptor =
            FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS
            )

        const val DNS_FREE_RECORD_LIST = 1
    }
}

internal object WindowsDnsLayouts {
    private val pointerSize = ValueLayout.ADDRESS.byteSize()
    private val pointerAlignment = ValueLayout.ADDRESS.byteAlignment()

    const val VERSION_OFFSET = 0L
    const val INTERFACE_INDEX_OFFSET = 4L

    const val BROWSE_QUERY_NAME_OFFSET = 8L
    val BROWSE_CALLBACK_OFFSET = BROWSE_QUERY_NAME_OFFSET + pointerSize
    private val browseContextOffset = BROWSE_CALLBACK_OFFSET + pointerSize
    val BROWSE_REQUEST_SIZE =
        align(browseContextOffset + pointerSize, pointerAlignment)

    const val RESOLVE_QUERY_NAME_OFFSET = 8L
    val RESOLVE_CALLBACK_OFFSET = RESOLVE_QUERY_NAME_OFFSET + pointerSize
    val RESOLVE_CONTEXT_OFFSET = RESOLVE_CALLBACK_OFFSET + pointerSize
    val RESOLVE_REQUEST_SIZE =
        align(RESOLVE_CONTEXT_OFFSET + pointerSize, pointerAlignment)

    const val REGISTER_INSTANCE_OFFSET = 8L
    val REGISTER_CALLBACK_OFFSET = REGISTER_INSTANCE_OFFSET + pointerSize
    private val registerContextOffset = REGISTER_CALLBACK_OFFSET + pointerSize
    private val registerCredentialsOffset = registerContextOffset + pointerSize
    private val registerUnicastOffset = registerCredentialsOffset + pointerSize
    val REGISTER_REQUEST_SIZE = align(
        registerUnicastOffset + ValueLayout.JAVA_INT.byteSize(),
        pointerAlignment
    )

    const val RECORD_NEXT_OFFSET = 0L
    val RECORD_TYPE_OFFSET = pointerSize * 2
    private val recordDataLengthOffset =
        RECORD_TYPE_OFFSET + ValueLayout.JAVA_SHORT.byteSize()
    private val recordFlagsOffset =
        recordDataLengthOffset + ValueLayout.JAVA_SHORT.byteSize()
    val RECORD_TTL_OFFSET =
        recordFlagsOffset + ValueLayout.JAVA_INT.byteSize()
    private val recordReservedOffset =
        RECORD_TTL_OFFSET + ValueLayout.JAVA_INT.byteSize()
    val RECORD_DATA_OFFSET = align(
        recordReservedOffset + ValueLayout.JAVA_INT.byteSize(),
        pointerAlignment
    )
    val RECORD_READABLE_SIZE = RECORD_DATA_OFFSET + pointerSize

    const val INSTANCE_NAME_OFFSET = 0L
    val INSTANCE_IPV4_OFFSET = pointerSize * 2
    val INSTANCE_IPV6_OFFSET = pointerSize * 3
    val INSTANCE_PORT_OFFSET = pointerSize * 4
    private val instancePriorityOffset =
        INSTANCE_PORT_OFFSET + ValueLayout.JAVA_SHORT.byteSize()
    private val instanceWeightOffset =
        instancePriorityOffset + ValueLayout.JAVA_SHORT.byteSize()
    val INSTANCE_PROPERTY_COUNT_OFFSET = align(
        instanceWeightOffset + ValueLayout.JAVA_SHORT.byteSize(),
        ValueLayout.JAVA_INT.byteAlignment()
    )
    val INSTANCE_KEYS_OFFSET = align(
        INSTANCE_PROPERTY_COUNT_OFFSET + ValueLayout.JAVA_INT.byteSize(),
        pointerAlignment
    )
    val INSTANCE_VALUES_OFFSET = INSTANCE_KEYS_OFFSET + pointerSize
    val INSTANCE_INTERFACE_INDEX_OFFSET = INSTANCE_VALUES_OFFSET + pointerSize
    val INSTANCE_READABLE_SIZE = align(
        INSTANCE_INTERFACE_INDEX_OFFSET + ValueLayout.JAVA_INT.byteSize(),
        pointerAlignment
    )

    val CANCEL_SIZE = pointerSize
    val NATIVE_ALIGNMENT = pointerAlignment
    val POINTER_BYTE_SIZE = pointerSize

    private fun align(value: Long, alignment: Long): Long {
        return (value + alignment - 1) / alignment * alignment
    }
}

internal fun Arena.allocateZeroed(size: Long): MemorySegment {
    return allocate(size, WindowsDnsLayouts.NATIVE_ALIGNMENT).apply {
        fill(0)
    }
}

internal fun Arena.allocateWideString(value: String): MemorySegment {
    return allocateFrom(value, UTF_16LE)
}

internal fun MemorySegment.readWideString(): String? {
    if (address() == 0L) return null

    return runCatching {
        reinterpret(MAX_WIDE_STRING_BYTES).getString(0, UTF_16LE)
    }.getOrNull()
}

private const val MAX_WIDE_STRING_BYTES = 2_048L
