package com.example.lazywarrior.data

import android.content.Context

/**
 * App container for Dependency injection.
 */
interface AppContainer {
    val processingStatusesRepository: ProcessingStatusesRepository

    val errorLogsRepository: ErrorLogsRepository
}

/**
 * [AppContainer] implementation that provides instance of [OfflineProcessingStatusesRepository]
 */
class AppDataContainer(private val context: Context) : AppContainer {
    /**
     * Implementation for [ProcessingStatusesRepository]
     */

    override val processingStatusesRepository: ProcessingStatusesRepository by lazy {
        OfflineProcessingStatusesRepository(
            MainDatabase.getDatabase(context).processingStatusDao())
    }

    override val errorLogsRepository: ErrorLogsRepository by lazy {
        OfflineErrorLogsRepository(
            MainDatabase.getDatabase(context).errorLogDao())
    }
}