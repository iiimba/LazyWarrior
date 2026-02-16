package com.example.lazywarrior.workers

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

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