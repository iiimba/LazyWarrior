package com.example.lazywarrior.data

class OfflineProcessingStatusesRepository(
    private val processingStatusDao: ProcessingStatusDao) : ProcessingStatusesRepository {

    override fun getLastProcessingStatus(): ProcessingStatus? = processingStatusDao.getLastProcessingStatus().firstOrNull()

    override fun processingStatusId(): Int = processingStatusDao.processingStatusId()

    override fun updateProcessingStatusStopRunning() = processingStatusDao.updateProcessingStatusStopRunning()

    override fun insertProcessingStatus(item: ProcessingStatus) = processingStatusDao.insert(item)

    override fun updateProcessingStatus(item: ProcessingStatus) = processingStatusDao.update(item)

    override fun updateProcessingStatusColor(color: String) = processingStatusDao.updateColor(color)

    override fun getProcessingStatusColor(): String? = processingStatusDao.getProcessingStatusColors().firstOrNull()
}
