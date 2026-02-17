package com.example.lazywarrior.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processingStatuses")
data class ProcessingStatus(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val startAt: String,
    val excelDocumentUrl: String,
    val sheetTitle: String,
    val objectNumber: String,
    val isRunning: Boolean,
    val color: String,
    val changeAtMinutes: Int,
    val objectNumberAtColumn: Int,
    val finishedAt: String
)
