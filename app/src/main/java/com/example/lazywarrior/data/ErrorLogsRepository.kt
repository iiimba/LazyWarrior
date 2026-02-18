package com.example.lazywarrior.data

interface ErrorLogsRepository {

    fun getErrorLogs(): List<ErrorLog>

    fun insert(item: ErrorLog)
}