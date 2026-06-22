package com.liftley.sync360.features.sync.data.network

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal class RawTransferGrantStore(
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private val grants = mutableMapOf<String, Grant>()

    fun register(transferId: String, transferToken: String, fileCount: Int) = synchronized(grants) {
        grants[transferId] = Grant(
            token = transferToken,
            fileCount = fileCount,
            expiresAtMillis = nowMillis() + RawTcpFileTransferConfig.TOKEN_TTL_MILLIS
        )
    }

    fun validateAndConsume(transferId: String, transferToken: String, fileIndex: Int): Boolean =
        synchronized(grants) {
            val grant = grants[transferId] ?: return@synchronized false
            if (nowMillis() > grant.expiresAtMillis) {
                grants.remove(transferId)
                return@synchronized false
            }
            if (grant.token != transferToken || fileIndex !in 0 until grant.fileCount) {
                return@synchronized false
            }
            grant.consumedFileIndexes.add(fileIndex)
        }

    fun revoke(transferId: String) = synchronized(grants) {
        grants.remove(transferId)
        Unit
    }

    fun clear() = synchronized(grants) {
        grants.clear()
    }

    private data class Grant(
        val token: String,
        val fileCount: Int,
        val expiresAtMillis: Long,
        val consumedFileIndexes: MutableSet<Int> = mutableSetOf()
    )
}
