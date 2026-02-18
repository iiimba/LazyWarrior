package com.example.lazywarrior.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "errorLogs")
data class ErrorLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val atTime: String,
    val errorMessage: String
)
