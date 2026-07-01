package com.liftley.sync360.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.liftley.sync360.domain.repository.ClipboardProvider

class AndroidClipboardProvider(private val context: Context) : ClipboardProvider {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    override fun provideLatestClipboard(): String? {
        if (!clipboardManager.hasPrimaryClip()) {
            return null
        }

        val primaryClip = clipboardManager.primaryClip ?: return null

        if (primaryClip.itemCount == 0) {
            return null
        }

        val firstClipItem = primaryClip.getItemAt(0)
        val clipboardText = firstClipItem.coerceToText(context)?.toString()

        return clipboardText
    }

    override fun setLatestClipboardTextAs(text: String) {
        val clipData = ClipData.newPlainText("copied_text", text)

        clipboardManager.setPrimaryClip(clipData)
    }

}