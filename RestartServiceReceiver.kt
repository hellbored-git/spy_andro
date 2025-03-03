package com.example.andresionghost

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class RestartServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("RestartServiceReceiver", "Received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_SHUTDOWN || action == "com.example.andresionghost.SERVICE_KILLED") {
            // Check if there's already a pending work to avoid duplicates
            val workManager = WorkManager.getInstance(context)
            val workInfos = workManager.getWorkInfosByTag("ghost_restart_tag").get()

            if (workInfos.isNullOrEmpty() || workInfos.all { it.state.isFinished }) {
                // Schedule a service restart using WorkManager
                val restartWorkRequest = OneTimeWorkRequestBuilder<RestartGhostServiceWorker>()
                    .setInitialDelay(2, TimeUnit.SECONDS)  // Restart after 2 seconds
                    .addTag("tor_restart_tag") // Add a unique tag to identify this work
                    .build()

                workManager.enqueue(restartWorkRequest)
                Log.d("RestartServiceReceiver", "Scheduled service restart after 2 seconds.")
            } else {
                Log.d("RestartServiceReceiver", "Service restart already scheduled, skipping.")
            }
        }
    }
}
