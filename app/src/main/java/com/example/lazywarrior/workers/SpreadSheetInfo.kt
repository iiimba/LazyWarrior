package com.example.lazywarrior.workers

data class SpreadSheetInfo(
    val sheets: List<Sheet>
)

data class Sheet(
    val properties: Property
)

data class Property(
    val sheetId: Int,
    val title: String
)