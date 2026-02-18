package com.example.lazywarrior.data

class OfflineErrorLogsRepository(private val errorLogDao: ErrorLogDao) : ErrorLogsRepository {
    override fun getErrorLogs(): List<ErrorLog> = errorLogDao.getErrorLogs()

    override fun insert(item: ErrorLog) = errorLogDao.insert(item)
}