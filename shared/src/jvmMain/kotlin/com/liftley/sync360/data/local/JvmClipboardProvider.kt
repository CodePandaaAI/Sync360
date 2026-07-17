package com.liftley.sync360.data.local

import com.liftley.sync360.domain.repository.ClipboardProvider
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

class JvmClipboardProvider : ClipboardProvider {
    override fun provideLatestClipboard(): String? {
        return runCatching {
            Toolkit.getDefaultToolkit()
                .systemClipboard
                .getData(DataFlavor.stringFlavor) as? String
        }.getOrNull()
    }

    override fun setLatestClipboardTextAs(text: String) {
        Toolkit.getDefaultToolkit()
            .systemClipboard
            .setContents(StringSelection(text), null)
    }
}
