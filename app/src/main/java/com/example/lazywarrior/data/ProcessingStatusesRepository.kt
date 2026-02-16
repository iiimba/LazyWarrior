package com.example.lazywarrior.data

interface ProcessingStatusesRepository {

    /**
     * Retrieve all the items from the the given data source.
     */
    fun getLastProcessingStatus(): ProcessingStatus?

    fun processingStatusId(): Int

    fun updateProcessingStatusStopRunning()

    /**
     * Insert ProcessingStatus in the data source
     */
    fun insertProcessingStatus(item: ProcessingStatus)

    /**
     * Update ProcessingStatus in the data source
     */
    fun updateProcessingStatus(item: ProcessingStatus)

    fun updateProcessingStatusColor(color: String)

    fun getProcessingStatusColor(): String?
}