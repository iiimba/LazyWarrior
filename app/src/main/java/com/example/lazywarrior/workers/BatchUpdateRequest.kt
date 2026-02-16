package com.example.lazywarrior.workers

data class BatchUpdateRequest(
    val requests: List<Request>
)

data class Request(
    val repeatCell: RepeatCellRequest
)

data class RepeatCellRequest(
    val cell : CellRequest,
    val range : Range,
    val fields : String
)

data class CellRequest(
    val userEnteredFormat : UserEnteredFormatRequest
)

data class UserEnteredFormatRequest(
    val backgroundColor: BackgroundColorRequest
)

data class BackgroundColorRequest(
    val red: Double,
    val green: Double,
    val blue: Double
)

data class Range(
    val sheetId: Int,
    val startRowIndex: Int,
    val endRowIndex: Int,
    val startColumnIndex: Int,
    val endColumnIndex: Int
)