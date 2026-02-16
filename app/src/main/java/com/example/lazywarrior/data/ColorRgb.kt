package com.example.lazywarrior.data

import androidx.room.PrimaryKey
import com.example.lazywarrior.workers.BackgroundColorRequest

data class ColorRgb(
    val color: Colors,
    val value: BackgroundColorRequest
)