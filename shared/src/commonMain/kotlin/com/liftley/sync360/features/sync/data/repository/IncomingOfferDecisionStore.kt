package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.features.sync.domain.model.PendingIncomingOffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

internal class IncomingOfferDecisionStore {
    private val lock = Any()
    private var activeDecision: CompletableDeferred<IncomingOfferDecision>? = null
    private var activeOfferId: String? = null
    private val _quickSaveEnabled = MutableStateFlow(false)
    private val _pendingOffer = MutableStateFlow<PendingIncomingOffer?>(null)

    val quickSaveEnabled: StateFlow<Boolean> = _quickSaveEnabled
    val pendingOffer: StateFlow<PendingIncomingOffer?> = _pendingOffer

    fun setQuickSaveEnabled(enabled: Boolean) {
        _quickSaveEnabled.value = enabled
    }

    suspend fun awaitDecision(offer: PendingIncomingOffer): IncomingOfferDecision {
        val decision = synchronized(lock) {
            if (activeDecision?.isActive == true) return IncomingOfferDecision.Busy
            CompletableDeferred<IncomingOfferDecision>().also {
                activeDecision = it
                activeOfferId = offer.offerId
                _pendingOffer.value = offer
            }
        }
        val result = withTimeoutOrNull(INCOMING_DECISION_TIMEOUT_MILLIS) {
            decision.await()
        } ?: IncomingOfferDecision.TimedOut
        synchronized(lock) {
            if (activeOfferId == offer.offerId) {
                activeDecision = null
                activeOfferId = null
                _pendingOffer.value = null
            }
        }
        return result
    }

    fun accept(offerId: String): Boolean = complete(offerId, IncomingOfferDecision.Accept)

    fun decline(offerId: String): Boolean = complete(offerId, IncomingOfferDecision.Decline)

    private fun complete(offerId: String, decision: IncomingOfferDecision): Boolean = synchronized(lock) {
        val pending = activeDecision ?: return@synchronized false
        if (activeOfferId != offerId || !pending.isActive) return@synchronized false
        pending.complete(decision)
    }

    private companion object {
        const val INCOMING_DECISION_TIMEOUT_MILLIS = 25_000L
    }
}

internal enum class IncomingOfferDecision {
    Accept,
    Decline,
    TimedOut,
    Busy
}
