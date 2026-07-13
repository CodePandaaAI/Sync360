package com.liftley.sync360.data.file

import com.liftley.sync360.domain.model.SelectedFile

interface SelectedFileReader {
    fun readSelectedFiles(platformFiles: List<Any>): List<SelectedFile>
}
