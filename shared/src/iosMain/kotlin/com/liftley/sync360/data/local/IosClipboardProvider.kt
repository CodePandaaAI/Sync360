package com.liftley.sync360.data.local

import com.liftley.sync360.domain.repository.ClipboardProvider
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIPasteboard

@OptIn(ExperimentalForeignApi::class)
class IosClipboardProvider : ClipboardProvider {
    private val pasteboard = UIPasteboard.generalPasteboard

    override fun provideLatestClipboard(): String? {
        return pasteboard.string
    }

    override fun setLatestClipboardTextAs(text: String) {
        pasteboard.string = text
    }
}
