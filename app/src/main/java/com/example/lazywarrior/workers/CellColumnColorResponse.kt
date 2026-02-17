package com.example.lazywarrior.workers

data class CellColumnColorResponse(
    val sheets: List<DataResponse>
)

data class DataResponse(
    val data: List<RowDataResponse>
)

data class RowDataResponse(
    val rowData: List<ValuesResponse>
)

data class ValuesResponse(
    val values: List<ValueResponse>
)

data class ValueResponse(
    val userEnteredFormat: UserEnteredFormatColorResponse
)

data class UserEnteredFormatColorResponse(
    val backgroundColorStyle: BackgroundColorStyleResponse
)

//data class UserEnteredFormatColorResponse(
//    val backgroundColorStyle: BackgroundColorStyleResponse
//)

data class BackgroundColorStyleResponse(
    val rgbColor: RgbColorResponse?,
    val themeColor: String?
)

data class RgbColorResponse(
    val red: Double?,
    val green: Double?,
    val blue: Double?
)