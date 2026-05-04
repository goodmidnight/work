package io.goodmidnight.transfer.data.datasource // 또는 data.source

interface AndroidFileDataSource {
    fun createMetaFileDescriptor(fileName: String): Int

    fun createTransferFileDescriptor(fileName: String, relativePath: String): Int
}