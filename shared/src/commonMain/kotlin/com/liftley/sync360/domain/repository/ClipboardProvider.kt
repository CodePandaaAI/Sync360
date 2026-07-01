package com.liftley.sync360.domain.repository

interface ClipboardProvider {
    fun provideLatestClipboard(): String?

    fun setLatestClipboardTextAs(text: String)
}