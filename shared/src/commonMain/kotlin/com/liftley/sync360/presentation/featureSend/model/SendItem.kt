package com.liftley.sync360.presentation.featureSend.model

interface SendItem {
    data class Text(val text: String): SendItem
}