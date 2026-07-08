package com.liftley.sync360.domain.repository

import com.liftley.sync360.presentation.send.model.PickedFile

interface FilesManager {
    fun processPickedFiles(platformPaths: List<Any>): List<PickedFile>
}