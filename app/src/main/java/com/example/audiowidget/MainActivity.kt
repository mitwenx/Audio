package com.example.audiowidget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // Activity Result Launcher for requesting permissions
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionsGranted = true
            permissions.entries.forEach {
                if (!it.value) {
                    permissionsGranted = false
                    Log.w("MainActivity", "Permission denied: ${it.key}")
                    // Explain why the permission is needed or guide user to settings
                    Toast.makeText(this, "Permission ${it.key.substringAfterLast('.')} required for recording/notifications.", Toast.LENGTH_LONG).show()
                } else {
                     Log.i("MainActivity", "Permission granted: ${it.key}")
                }
            }

            if (permissionsGranted) {
                 Toast.makeText(this, "Permissions Granted!", Toast.LENGTH_SHORT).show()
                // Permissions granted, app can function fully
            } else {
                 Toast.makeText(this, "Some permissions were denied.", Toast.LENGTH_SHORT).show()
                // Handle the case where permissions are denied (e.g., disable features)
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.info_text).text = """
            Audio Widget App
            - Add the widget to your home screen.
            - Double-click the widget icon to start/stop recording.
            - Recordings are saved in the app's external files directory.
            - Ensure necessary permissions are granted.
        """.trimIndent()

        // Request necessary permissions on startup
        checkAndRequestPermissions()
    }

     private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // RECORD_AUDIO is always needed
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // POST_NOTIFICATIONS needed for Android 13 (API 33) and above for the foreground service notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // FOREGROUND_SERVICE_MICROPHONE might be needed for Android 14+ foreground restrictions,
        // though FOREGROUND_SERVICE permission + foregroundServiceType in manifest might suffice.
        // Let's request it explicitly if targeting 34+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
             if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE) != PackageManager.PERMISSION_GRANTED) {
                // Note: This permission usually requires FOREGROUND_SERVICE as well,
                // which is implicitly granted when targeting lower SDKs but good to be aware of.
                // The manifest already declares <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
                // and <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
                // No runtime request typically needed for FOREGROUND_SERVICE itself if declared,
                // but the _TYPE_ permission might be requested or enforced differently.
                // Let's add it to the request list just in case behavior changes or for clarity.
                 // permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE) // Add if needed based on testing on API 34+
             }
        }


        if (permissionsToRequest.isNotEmpty()) {
            Log.d("MainActivity", "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d("MainActivity", "All necessary permissions already granted.")
            // All permissions are already granted
        }
    }
}
