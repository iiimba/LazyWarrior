package com.example.lazywarrior.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StrictMode
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.lazywarrior.data.AppContainer
import com.example.lazywarrior.data.AppDataContainer
import com.example.lazywarrior.data.ColorRgb
import com.example.lazywarrior.data.Colors
import com.google.auth.oauth2.GoogleCredentials
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.InputStream
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException

class SheetsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val alphabet = ('A'..'Z').map { it.toString() }

    companion object {
        const val CHANNEL_ID = "ForegroundServiceChannel"
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setContentTitle("Foreground Service")
                    .setContentTitle("Syncing Sheets")
                    .setSmallIcon(android.R.drawable.ic_popup_sync)
                    .build()

                setForeground(ForegroundInfo(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))

                createNotificationChannel()

                val success = updateCellHourStatus()
                if (success) {
                    val nextWork = OneTimeWorkRequestBuilder<SheetsWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .build()

                    WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                        "LazyWarriorWorker",
                        ExistingWorkPolicy.REPLACE,
                        nextWork
                    )

                    return@withContext Result.success()
                }
            } catch (e: CancellationException) {
                makeStatusNotification(
                    "Cancelled, with message: ${e.message.toString()}",
                    applicationContext)
                throw e
            }

            return@withContext Result.retry()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    suspend fun updateCellHourStatus() : Boolean {
        try {
            val container: AppContainer = AppDataContainer(applicationContext)
            val processingStatus = container.processingStatusesRepository.getLastProcessingStatus()
            if (processingStatus == null) {
                makeStatusNotification(
                    "Processing status not found",
                    applicationContext
                )
                return false
            }

            if (!processingStatus.isRunning) {
                makeStatusNotification(
                    "isRunning = false",
                    applicationContext
                )
                return false
            }

            if (LocalDateTime.parse(processingStatus.finishedAt) < LocalDateTime.now()) {
                throw CancellationException("LazyWarrior finished work because finished date: ${processingStatus.finishedAt} has come")
            }

            val regexWithGroup = Regex("https://docs.google.com/spreadsheets/d/([A-Za-z0-9-]+)[ ]*+")
            val match = regexWithGroup.find(processingStatus.excelDocumentUrl)
            if (match == null) {
                makeStatusNotification(
                    "Wrong url link format, spreadsheetId not found",
                    applicationContext
                )
                return false
            }

            waitTillStart(processingStatus.changeAtMinutes)
            val hourToUpdate = LocalDateTime.now().hour
            //val hourToUpdate = 9

            val spreadsheetId = match.groupValues[1]
            val gSService = getGSheetsService()
            var data = retryWithDelay {
                gSService.getValuesByRange(
                    spreadsheetId,
                    "${alphabet[processingStatus.objectNumberAtColumn]}:${alphabet[processingStatus.objectNumberAtColumn]}"
                )
            }

            val objectNumberRawIndex = findObjectNumberRawIndex(
                data.body()!!.values,
                processingStatus.objectNumber
            )
            if (objectNumberRawIndex == null) {
                makeStatusNotification(
                    "Object: $processingStatus.objectNumber not found",
                    applicationContext
                )
                return false
            }

            val spreadSheetsInfo = retryWithDelay { gSService.getSpreadSheetsInfo(spreadsheetId) }
            val sheet = spreadSheetsInfo.body()!!.sheets.firstOrNull { s ->
                s.properties.title.lowercase()
                    .trim() == processingStatus.sheetTitle.lowercase().trim()
            }
            if (sheet == null) {
                makeStatusNotification(
                    "Object: $processingStatus.sheetTitle not found",
                    applicationContext
                )
                return false
            }

            data = retryWithDelay {
                gSService.getValuesByRange(
                    spreadsheetId,
                    "${objectNumberRawIndex + 1}:${objectNumberRawIndex + 1}"
                )
            }

            val color = container.processingStatusesRepository.getProcessingStatusColor()
            val colorValue = color ?: Colors.White.toString()
            val objectNumberColumnIndex = findObjectNumberColumnIndex(
                gSService,
                spreadsheetId,
                objectNumberRawIndex + 1,
                data.body()!!.values,
                hourToUpdate,
                processingStatus.changeAtMinutes)
            if (objectNumberColumnIndex != null) {
                updateCellBackgroundColor(
                    gSService,
                    spreadsheetId,
                    sheet.properties.sheetId,
                    objectNumberRawIndex,
                    objectNumberColumnIndex,
                    colorValue
                )

                makeStatusNotification(
                    "Updated time: $hourToUpdate:00, color: $colorValue",
                    applicationContext)
            } else {
                val previousObjectNumberColumnIndex = findObjectNumberColumnIndex(
                    gSService,
                    spreadsheetId,
                    objectNumberRawIndex + 1,
                    data.body()!!.values,
                    hourToUpdate - 1,
                    processingStatus.changeAtMinutes)
                if (previousObjectNumberColumnIndex == null) {
                    makeStatusNotification(
                        "$hourToUpdate hours not found.",
                        applicationContext
                    )
                    return false
                }

                updateCellBackgroundColor(
                    gSService,
                    spreadsheetId,
                    sheet.properties.sheetId,
                    objectNumberRawIndex,
                    previousObjectNumberColumnIndex + 1,
                    colorValue
                )

                val updateValueRequest = UpdateValueRequest(listOf(listOf("$hourToUpdate:" + processingStatus.changeAtMinutes.toString().padStart(2, '0'))))
                val rowLetter = getRowLetter(previousObjectNumberColumnIndex - 1)
                retryWithDelay {
                    gSService.updateValue(
                        spreadsheetId,
                        rowLetter + (objectNumberRawIndex + 1),
                        updateValueRequest
                    )
                }

                makeStatusNotification(
                    "Updated time: $hourToUpdate:${processingStatus.changeAtMinutes.toString().padStart(2, '0')}, color: $colorValue",
                    applicationContext)
            }
        } catch (throwable: Throwable) {
            makeStatusNotification(
                "Another error: ${throwable.message}",
                applicationContext
            )
            Log.e(
                "OUTPUT",
                "Error",
                throwable
            )
            return false
        } catch (e: Exception) {
            makeStatusNotification(
                "Error: ${e.message}",
                applicationContext
            )
            return false
        }


        return true
    }

    suspend fun waitTillStart(changeAtMinutes: Int) {
        val dateTimeNow = LocalDateTime.now()
        val minutesToWait = (if (changeAtMinutes > dateTimeNow.minute) (changeAtMinutes - dateTimeNow.minute) else (60 - dateTimeNow.minute + changeAtMinutes)).toLong() + 1

        delay(minutesToWait * 60 * 1000)
        //delay(10 * 1000)
    }

    suspend fun findObjectNumberColumnIndex(
        gSService: GSheetsService,
        spreadsheetId: String,
        objectNumberRawIndex: Int,
        timeValues: List<List<String>>,
        hourToUpdate: Int,
        minuteToUpdate: Int): Int? {
        val indexes = timeValues.first()
            .drop(2)
            .indexesOf { !it.isEmpty() && it.startsWith("$hourToUpdate:${minuteToUpdate.toString().padStart(2, '0')}") }

        if (indexes.size == 1) {
//            if (indexes[0] == 23) {
//                return 1
//            }

            return indexes[0] + 2
        }

        if (indexes.size > 1) {
            indexes.forEach { index ->
                if (index - 1 >= 0) {
                    val indexToUpdateColor = findIndexToUpdateColor(
                        gSService,
                        spreadsheetId,
                        objectNumberRawIndex,
                        index - 1)

                    if (indexToUpdateColor != null) {
                        return indexToUpdateColor
                    }
                } else {
                    val previousHour = if (hourToUpdate - 1 < 0) 23 else hourToUpdate - 1
                    val indexesWithPreviousHour = timeValues.first()
                        .drop(2)
                        .indexesOf { !it.isEmpty() && it.startsWith("$previousHour:${minuteToUpdate.toString().padStart(2, '0')}") }

                    if (!indexesWithPreviousHour.any()) {
                        return null
                    }

                    val indexWithPreviousHour = indexesWithPreviousHour.last()
                    val indexToUpdateColor = findIndexToUpdateColor(
                        gSService,
                        spreadsheetId,
                        objectNumberRawIndex,
                        indexWithPreviousHour)

                    if (indexToUpdateColor != null) {
                        return indexToUpdateColor
                    }
                }
            }
        }

        return null
    }

    fun getGSheetsService() : GSheetsService {
        val serviceAccountStream = applicationContext.assets
            .open("ServiceAccount.json")
            .bufferedReader()
            .use { it.readText() }
            .byteInputStream()

        val accessToken = getAccessToken(serviceAccountStream)
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(accessToken))
            .build()

        return Retrofit.Builder()
            .client(client)
            .baseUrl(
                "https://sheets.googleapis.com/"
            )
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(GSheetsService::class.java)
    }

    fun getAccessToken(jsonStream: InputStream): String {
        StrictMode.ThreadPolicy.Builder().permitAll().build()

        val credentials = GoogleCredentials.fromStream(jsonStream)
            .createScoped(listOf("https://www.googleapis.com/auth/spreadsheets"))

        credentials.refreshIfExpired()

        return credentials.accessToken.tokenValue
    }

    suspend fun <T> retryWithDelay(
        times: Int = 8, // Max number of retries
        initialDelay: Long = 10000, // Initial delay in milliseconds (1 second)
        factor: Double = 2.0, // Delay factor (exponential backoff)
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) { attempt ->
            try {
                return block() // Try to execute the network call
            } catch (e: Exception) {
                // Only retry on specific transient errors, e.g., network issues
                if (attempt == times - 1) throw e // Rethrow if max retries exceeded

                // Calculate exponential delay
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            } catch (e: Throwable) {
                // Only retry on specific transient errors, e.g., network issues
                if (attempt == times - 1) throw e // Rethrow if max retries exceeded

                // Calculate exponential delay
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }

        return block() // Last attempt
    }

    fun findObjectNumberRawIndex(
        objectNames: List<List<String>>,
        objectNumber: String): Int? {
        for (values in objectNames) {
            val value = values.firstOrNull() ?: continue
            if (value.lowercase().trim() == objectNumber.lowercase().trim()) {
                return objectNames.indexOf(values)
            }
        }

        return null
    }

    suspend fun updateCellBackgroundColor(
        gSService: GSheetsService,
        spreadsheetId : String,
        sheetId : Int,
        objectNumberRawIndex: Int,
        objectNumberColumnIndex: Int,
        color: String
    ) {
        val colors = listOf(
            ColorRgb(Colors.White, BackgroundColorRequest(1.0, 1.0, 1.0)),
            ColorRgb(Colors.Red, BackgroundColorRequest(1.0, 0.0, 0.0)))

        val batchUpdateRequest = BatchUpdateRequest(
            listOf(
                Request(
                    RepeatCellRequest(
                        CellRequest(
                            UserEnteredFormatRequest(
                                colors.first { c -> c.color.toString() == color }.value
                            )
                        ),
                        Range(
                            sheetId,
                            objectNumberRawIndex,
                            objectNumberRawIndex + 1,
                            objectNumberColumnIndex,
                            objectNumberColumnIndex + 1
                        ),
                        "userEnteredFormat.backgroundColor"
                    )
                )
            )
        )

        retryWithDelay {
            gSService.batchUpdate(
                spreadsheetId,
                batchUpdateRequest
            )
        }
    }

    fun getRowLetter(objectNumberColumnIndex: Int): String {
        var result = ""

        val firstLetterIndex = (objectNumberColumnIndex + 2) / 26

        if (firstLetterIndex > 0) {
            result += alphabet[firstLetterIndex - 1]
        }
        result += alphabet[(objectNumberColumnIndex + 2) % 26]

        return result
    }

    fun <E> Iterable<E>.indexesOf(predicate: (E) -> Boolean) = mapIndexedNotNull { index, elem ->
        index.takeIf { predicate(elem) }
    }

    suspend fun findIndexToUpdateColor(
        gSService: GSheetsService,
        spreadsheetId: String,
        objectNumberRawIndex: Int,
        index: Int) : Int? {
        val range = getRowLetter(index)
        val cellColumnColorResponse = retryWithDelay {
            gSService.getCellColumnColor(
                spreadsheetId,
                range + objectNumberRawIndex,
                "sheets(data(rowData(values(userEnteredFormat.backgroundColorStyle))))"
            )
        }

        val backgroundColorStyle = cellColumnColorResponse.body()!!.sheets.single().data.single().rowData.single().values.single().userEnteredFormat.backgroundColorStyle
        if ((backgroundColorStyle.rgbColor?.red == 1.0 && backgroundColorStyle.rgbColor.green == 1.0 && backgroundColorStyle.rgbColor.blue == 1.0) || backgroundColorStyle.themeColor == "BACKGROUND") {
            return index + 2
        }

        return null
    }
}