package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.features.sync.domain.model.ClipboardEntry
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.SendItem

data class SendUiState(
    val selectedItems: List<SendItem> = emptyList(),
    val outgoingText: String = "",
    val latestTexts: List<ClipboardEntry> = emptyList(),
    val pendingOutgoingOfferTarget: DeviceProfile? = null
)
