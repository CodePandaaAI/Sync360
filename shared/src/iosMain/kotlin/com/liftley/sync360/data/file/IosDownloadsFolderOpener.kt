package com.liftley.sync360.data.file

import com.liftley.sync360.core.platform.IosViewControllerProvider
import com.liftley.sync360.domain.repository.DownloadsFolderOpener
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeItem

@OptIn(ExperimentalForeignApi::class)
class IosDownloadsFolderOpener(
    private val viewControllerProvider: IosViewControllerProvider
) : DownloadsFolderOpener {
    override fun openDownloads() {
        val downloadsUrl = runCatching {
            iosDownloadsDirectoryUrl()
        }.getOrNull() ?: return
        var presentingViewController =
            viewControllerProvider.rootViewController ?: return
        while (presentingViewController.presentedViewController != null) {
            presentingViewController =
                presentingViewController.presentedViewController ?: break
        }
        val documentPicker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeItem),
            asCopy = false
        ).apply {
            directoryURL = downloadsUrl
        }

        presentingViewController.presentViewController(
            viewControllerToPresent = documentPicker,
            animated = true,
            completion = null
        )
    }
}
