package com.example.lazywarrior.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ProcessingStatusDao {

    @Query("SELECT * FROM processingStatuses ORDER BY Id DESC LIMIT 1")
    fun getLastProcessingStatus(): List<ProcessingStatus>

    @Query("SELECT Id from processingStatuses ORDER BY Id DESC LIMIT 1")
    fun processingStatusId(): Int

    @Query("UPDATE processingStatuses SET isRunning = 0")
    fun updateProcessingStatusStopRunning()

    // Specify the conflict strategy as IGNORE, when the user tries to add an
    // existing ProcessingStatus into the database Room ignores the conflict.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(item: ProcessingStatus)

    @Update
    fun update(item: ProcessingStatus)

    @Query("UPDATE processingStatuses SET color = :color")
    fun updateColor(color: String)

    @Query("SELECT color FROM processingStatuses ORDER BY Id DESC LIMIT 1")
    fun getProcessingStatusColors() : List<String>
}
