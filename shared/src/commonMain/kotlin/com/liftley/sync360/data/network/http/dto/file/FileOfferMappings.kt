package com.liftley.sync360.data.network.http.dto.file

import com.liftley.sync360.domain.model.FileTransferOffer
import com.liftley.sync360.domain.model.OfferedFile

fun FileTransferOffer.toFileOfferRequest(): FileOfferRequest {
    return FileOfferRequest(
        senderDeviceId = senderDeviceId,
        senderDeviceName = senderDeviceName,
        files = files.map { file ->
            FileOfferItem(
                index = file.index,
                fileName = file.fileName,
                fileSizeBytes = file.fileSizeBytes,
                mimeType = file.mimeType
            )
        },
        totalSizeBytes = totalSizeBytes
    )
}

fun FileOfferRequest.toFileTransferOffer(): FileTransferOffer {
    return FileTransferOffer(
        senderDeviceId = senderDeviceId,
        senderDeviceName = senderDeviceName,
        files = files.map { file ->
            OfferedFile(
                index = file.index,
                fileName = file.fileName,
                fileSizeBytes = requireNotNull(file.fileSizeBytes) {
                    "File size is required for raw TCP transfer: ${file.fileName}"
                },
                mimeType = file.mimeType
            )
        },
        totalSizeBytes = requireNotNull(totalSizeBytes) {
            "Total file size is required for raw TCP transfer"
        }
    )
}
