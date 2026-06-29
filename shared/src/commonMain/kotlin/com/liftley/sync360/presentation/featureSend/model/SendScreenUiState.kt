package com.liftley.sync360.presentation.featureSend.model

sealed interface SendScreenUiState {
    data object Idle: SendScreenUiState

    data class Sending(val sendingTo: String, val data: String): SendScreenUiState

    data class Sent(val sentTo: String, val data: String): SendScreenUiState

    data class NotSent(val reason: String): SendScreenUiState
}