package com.example.lazywarrior.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ErrorLogDao {
    @Query("SELECT * FROM errorLogs ORDER BY Id")
    fun getErrorLogs(): List<ErrorLog>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(item: ErrorLog)
}