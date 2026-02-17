package com.example.lazywarrior.workers

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GSheetsService {
    @GET("v4/spreadsheets/{spreadsheetId}")
    suspend fun getSpreadSheetsInfo(
        @Path("spreadsheetId") spreadsheetId: String
    ): Response<SpreadSheetInfo>

    @GET("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun getValuesByRange(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String
    ): Response<ObjectNamesResponse>

    @GET("v4/spreadsheets/{spreadsheetId}")
    suspend fun getCellColumnColor(
        @Path("spreadsheetId") spreadsheetId: String,
        @Query("ranges") ranges: String,
        @Query("fields") fields: String
    ): Response<CellColumnColorResponse>

    @POST("v4/spreadsheets/{spreadsheetId}:batchUpdate")
    suspend fun batchUpdate(
        @Path("spreadsheetId") spreadsheetId: String,
        @Body body: BatchUpdateRequest
    ): Response<Unit>

    @PUT("v4/spreadsheets/{spreadsheetId}/values/{range}?valueInputOption=RAW")
    suspend fun updateValue(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Body body: UpdateValueRequest
    ): Response<Unit>
}