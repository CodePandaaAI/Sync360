package com.liftley.sync360.data.file

interface DownloadsWriter<Input> {
    fun writeFile(
        fileName: String,
        mimeType: String?,
        fileSizeBytes: Long,
        input: Input,
        onBytesWritten: (byteCount: Int) -> Unit
    )
}
