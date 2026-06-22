package com.liftley.sync360.core.platform

import com.liftley.sync360.features.sync.domain.model.PickedFile

interface AndroidActivityBridge {
    fun openFilePicker(
        kind: FilePickerKind,
        onFilesSelected: (files: List<PickedFile>) -> Unit
    )

    fun openFile(path: String)
    fun showFileInFolder(path: String)
    fun openDownloadsFolder()
}
