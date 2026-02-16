package com.example.lazywarrior.workers

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.PARTIAL_WAKE_LOCK
import android.os.StrictMode
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.lazywarrior.data.AppContainer
import com.example.lazywarrior.data.AppDataContainer
import com.example.lazywarrior.data.ColorRgb
import com.example.lazywarrior.data.Colors
import com.google.auth.oauth2.GoogleCredentials
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.InputStream
import java.time.Duration
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException

class ForegroundService : LifecycleService() {

    private lateinit var wakeLock: PowerManager.WakeLock

    private val alphabet = ('A'..'Z').map { it.toString() }

    companion object {
        const val CHANNEL_ID = "ForegroundServiceChannel"
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PARTIAL_WAKE_LOCK, "LazyWarrior:MyWakeLockTag")
        wakeLock.acquire()
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Foreground Service")
            .setContentText("Service is running...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()

        startForeground(1, notification)

        lifecycleScope.launch {
            doWork()
        }

        return START_STICKY // System will restart the service if it's killed
    }

    override fun onBind(intent: Intent): IBinder? {
        return null // Not a bound service
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        val container: AppContainer = AppDataContainer(applicationContext)
        container.processingStatusesRepository.updateProcessingStatusStopRunning()
        makeStatusNotification(
            "Service finished work",
            applicationContext)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    suspend fun doWork() {
        withContext(Dispatchers.IO) {
            makeStatusNotification(
                "Process started",
                applicationContext
            )
            while (true) {
                try {
                    val container: AppContainer = AppDataContainer(applicationContext)
                    val processingStatus = container.processingStatusesRepository.getLastProcessingStatus()
                    if (processingStatus == null) {
                        makeStatusNotification(
                            "Processing status not found",
                            applicationContext
                        )
                        break
                    }

                    if (!processingStatus.isRunning) {
                        makeStatusNotification(
                            "isRunning = false",
                            applicationContext
                        )
                        break
                    }

                    val regexWithGroup = Regex("https://docs.google.com/spreadsheets/d/([A-Za-z0-9-]+)[ ]*+")
                    val match = regexWithGroup.find(processingStatus.excelDocumentUrl)
                    if (match == null) {
                        makeStatusNotification(
                            "Wrong url link format, spreadsheetId not found",
                            applicationContext
                        )
                        break
                    }

                    val hourToUpdate = getHoursToUpdate(processingStatus.changeAtMinutes)

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
                        break
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
                        break
                    }

                    data = retryWithDelay {
                        gSService.getValuesByRange(
                            spreadsheetId,
                            "${objectNumberRawIndex + 1}:${objectNumberRawIndex + 1}"
                        )
                    }

                    val color = container.processingStatusesRepository.getProcessingStatusColor()
                    val colorValue = color ?: Colors.White.toString()
                    var objectNumberColumnIndex = findObjectNumberColumnIndex(data.body()!!.values, hourToUpdate)
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
                        objectNumberColumnIndex = findObjectNumberColumnIndex(data.body()!!.values, hourToUpdate - 1)
                        if (objectNumberColumnIndex == null) {
                            makeStatusNotification(
                                "$hourToUpdate hours not found.",
                                applicationContext
                            )
                            continue
                        }

                        updateCellBackgroundColor(
                            gSService,
                            spreadsheetId,
                            sheet.properties.sheetId,
                            objectNumberRawIndex + 1,
                            objectNumberColumnIndex + 1,
                            colorValue
                        )

                        val updateValueRequest = UpdateValueRequest(listOf(listOf("$hourToUpdate:" + processingStatus.changeAtMinutes.toString().padStart(2, '0'))))
                        val rowLetter = getRowLetter(objectNumberColumnIndex)
                        retryWithDelay {
                            gSService.updateValue(
                                spreadsheetId,
                                rowLetter + (objectNumberRawIndex + 1),
                                updateValueRequest
                            )
                        }

                        makeStatusNotification(
                            "Updated time: $hourToUpdate:$processingStatus.changeAtMinutes.toString().padStart(2, '0'), color: $colorValue",
                            applicationContext)
                    }
                } catch (e: CancellationException) {
                    makeStatusNotification(
                        "Cancelled, with message: ${e.message.toString()}",
                        applicationContext
                    )
                    Log.d("ExcelWorker", "ExcelWorker cancelled, performing cleanup...")
                    break
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
                    break
                } catch (e: Exception) {
                    makeStatusNotification(
                        "Error: ${e.message}",
                        applicationContext
                    )
                    break
                }
            }
        }
    }

    suspend fun getHoursToUpdate(changeAtMinutes: Int) : Int {
        val dateTimeNow = LocalDateTime.now()
        val minutesToWait = (if (changeAtMinutes > dateTimeNow.minute) (changeAtMinutes - dateTimeNow.minute) else (60 - dateTimeNow.minute + changeAtMinutes)).toLong() + 1

//        makeStatusNotification(
//            "Sleep for: $minutesToWait minutes",
//            applicationContext)
//        delay(10 * 1000)

        delay(minutesToWait * 60 * 1000)

//        while (dateTimeNow.hour == LocalDateTime.now().hour) {
//            delay(5 * 60 * 1000)
//            val duration = Duration.between(dateTimeNow, LocalDateTime.now())
//            makeStatusNotification(
//                "Waiting, service delayed: ${duration.toHours().toString().padStart(2, '0')}:${(duration.toMinutes() % 60).toString().padStart(2, '0')}:${(duration.toSeconds() % 60).toString().padStart(2, '0')}",
//                applicationContext)
//        }

        return LocalDateTime.now().hour
    }

    suspend fun getGSheetsService() : GSheetsService {
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

    fun findObjectNumberColumnIndex(
        objectNames: List<List<String>>,
        hourToUpdate: Int): Int? {
        for (value in objectNames.first().drop(1)) {
            if (value.isEmpty())
                continue
            if (value.startsWith("$hourToUpdate:00")) {
                return objectNames.first().indexOf(value)
            }
        }

        return null
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    suspend fun getAccessToken(jsonStream: InputStream): String {
        StrictMode.ThreadPolicy.Builder().permitAll().build()

        val credentials = GoogleCredentials.fromStream(jsonStream)
            .createScoped(listOf("https://www.googleapis.com/auth/spreadsheets"))
        credentials.refreshIfExpired()

        return credentials.accessToken.tokenValue
    }

    fun getRowLetter(objectNumberColumnIndex: Int): String {
        var result = ""
        if (objectNumberColumnIndex > 26) {
            result += "A"
        }
        result += alphabet[objectNumberColumnIndex + 1]

        return result
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
}