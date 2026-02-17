package com.example.lazywarrior

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.text.Editable
import android.text.InputFilter
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.annotation.RequiresPermission
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.lazywarrior.data.AppContainer
import com.example.lazywarrior.data.AppDataContainer
import com.example.lazywarrior.data.Colors
import com.example.lazywarrior.data.ProcessingStatus
import com.example.lazywarrior.workers.ForegroundService
import java.time.LocalDateTime
import android.net.Uri
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.lazywarrior.workers.SheetsWorker
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestBatteryOptimizationExemption()

        val container: AppContainer = AppDataContainer(this)
        val status = container.processingStatusesRepository.getLastProcessingStatus()

        val documentUrlInput: EditText = findViewById(R.id.document_url)
        val sheetTitleInput: EditText = findViewById(R.id.sheet_title)
        val objectListInput: Spinner = findViewById(R.id.object_list)
        val radioGroupColors: RadioGroup = findViewById(R.id.radio_group_colors)
        val colorWhite: RadioButton = findViewById(R.id.radio_color_white)
        val colorRed: RadioButton = findViewById(R.id.radio_color_red)
        val changeAtMinutes: Spinner = findViewById(R.id.change_at_minutes)
        val objectNumberAtColumn: Spinner = findViewById(R.id.object_number_at_column)
        val startButton: Button = findViewById(R.id.start_button)
        val stopButton: Button = findViewById(R.id.stop_button)

        documentUrlInput.setText(status?.excelDocumentUrl)
        documentUrlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.toString().isEmpty() && !s.toString().startsWith("https://docs.google.com/spreadsheets/d/")) {
                    documentUrlInput.error = "Wrong format"
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        documentUrlInput.isEnabled = if (status == null) true else if (!status.isRunning) true else false

        sheetTitleInput.setText(status?.sheetTitle)
        sheetTitleInput.isEnabled = if (status == null) true else if (!status.isRunning) true else false

        ArrayAdapter.createFromResource(
            this,
            R.array.objects,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            objectListInput.adapter = adapter
        }
        objectListInput.setSelection(if (status == null) 0 else resources.getStringArray(R.array.objects).indexOf(status.objectNumber))
        objectListInput.isEnabled = if (status == null) true else if (!status.isRunning) true else false

        if (status == null) {
            colorWhite.isChecked = true
        }
        else if (status.color == Colors.White.toString()) {
            colorWhite.isChecked = true
        }
        else {
            colorRed.isChecked = true
        }

        radioGroupColors.setOnCheckedChangeListener { group, checkedId ->
            val selectedRadioButton = findViewById<RadioButton>(checkedId)
            val selectedOption = selectedRadioButton.text.toString()
            if (stopButton.isEnabled && status != null) {
                if (selectedOption != status.color) {
                    container.processingStatusesRepository.updateProcessingStatusColor(selectedOption)
                }
            }
        }

        val minutes = listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, minutes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        changeAtMinutes.adapter = adapter
        changeAtMinutes.setSelection(if (status == null) 0 else minutes.indexOf(status.changeAtMinutes))
        changeAtMinutes.isEnabled = if (status == null) true else if (!status.isRunning) true else false

        val alphabet = ('A'..'Z').map { it.toString() }
        val alphabetAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            alphabet
        )
        alphabetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        objectNumberAtColumn.adapter = alphabetAdapter
        objectNumberAtColumn.setSelection(status?.objectNumberAtColumn ?: 0)
        objectNumberAtColumn.isEnabled = if (status == null) true else if (!status.isRunning) true else false

        startButton.setOnClickListener {
            if (documentUrlInput.text.isEmpty() || !documentUrlInput.text.toString().startsWith("https://docs.google.com/spreadsheets/d/")) {
                documentUrlInput.error = "Wrong format"
                return@setOnClickListener
            }

            val id = container.processingStatusesRepository.processingStatusId();
            if (id > 0) {
                val updatedProcessingStatus = ProcessingStatus(
                    id,
                    LocalDateTime.now().toString(),
                    documentUrlInput.text.toString(),
                    sheetTitleInput.text.toString(),
                    objectListInput.selectedItem.toString(),
                    true,
                    if (colorWhite.isChecked) Colors.White.toString() else Colors.Red.toString(),
                    changeAtMinutes.selectedItem as Int,
                    alphabet.indexOf(objectNumberAtColumn.selectedItem.toString()))
                container.processingStatusesRepository.updateProcessingStatus(updatedProcessingStatus)
            }
            else {
                val newProcessingStatus = ProcessingStatus(
                    0,
                    LocalDateTime.now().toString(),
                    documentUrlInput.text.toString(),
                    sheetTitleInput.text.toString(),
                    objectListInput.selectedItem.toString(),
                    true,
                    if (colorWhite.isChecked) Colors.White.toString() else Colors.Red.toString(),
                    changeAtMinutes.selectedItem as Int,
                    alphabet.indexOf(objectNumberAtColumn.selectedItem.toString()))
                container.processingStatusesRepository.insertProcessingStatus(newProcessingStatus)
            }
            documentUrlInput.isEnabled = false
            sheetTitleInput.isEnabled = false
            objectListInput.isEnabled = false
            changeAtMinutes.isEnabled = false
            objectNumberAtColumn.isEnabled = false
            startButton.isEnabled = false
            stopButton.isEnabled = true

            val firstRun = OneTimeWorkRequestBuilder<SheetsWorker>()
                .setConstraints(
                    Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build()

            WorkManager.getInstance(this).enqueueUniqueWork(
                "LazyWarriorWorker",
                ExistingWorkPolicy.KEEP,
                firstRun)

//            val serviceIntent = Intent(this, ForegroundService::class.java)
//            applicationContext.startForegroundService(serviceIntent)
        }
        startButton.isEnabled = if (status == null) true else if (!status.isRunning) true else false

        stopButton.setOnClickListener {
            container.processingStatusesRepository.updateProcessingStatusStopRunning()
            documentUrlInput.isEnabled = true
            sheetTitleInput.isEnabled = true
            objectListInput.isEnabled = true
            changeAtMinutes.isEnabled = true
            objectNumberAtColumn.isEnabled = true
            startButton.isEnabled = true
            stopButton.isEnabled = false

            WorkManager.getInstance(this).cancelUniqueWork("LazyWarriorWorker")

//            val serviceIntent = Intent(this, ForegroundService::class.java)
//            stopService(serviceIntent)
        }
        stopButton.isEnabled = if (status == null) false else if (!status.isRunning) false else true
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager

            // Check if the app is already ignoring battery optimizations
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }
}