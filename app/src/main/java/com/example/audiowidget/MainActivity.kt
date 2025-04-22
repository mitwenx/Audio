package com.example.audiowidget

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var recordingAdapter: RecordingAdapter
    private var mediaPlayer: MediaPlayer? = null
    private val recordingsList = mutableListOf<File>() // Store File objects

    private val TAG = "MainActivity"

    // Activity Result Launcher for requesting permissions
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionsGranted = true
            permissions.entries.forEach {
                if (!it.value) {
                    permissionsGranted = false
                    Log.w(TAG, "Permission denied: ${it.key}")
                    Toast.makeText(
                        this,
                        "Permission ${it.key.substringAfterLast('.')} required.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Log.i(TAG, "Permission granted: ${it.key}")
                }
            }

            if (permissionsGranted) {
                Toast.makeText(this, "Permissions Granted!", Toast.LENGTH_SHORT).show()
                // Re-load recordings if permissions were just granted
                loadRecordings()
            } else {
                Toast.makeText(this, "Some permissions were denied.", Toast.LENGTH_SHORT).show()
                // Handle the case where permissions are denied (e.g., disable features or explain)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recordings_recycler_view)
        setupRecyclerView()

        // Request necessary permissions on startup
        checkAndRequestPermissions() // This will call loadRecordings if permissions are already granted
    }

    override fun onResume() {
        super.onResume()
        // Refresh the list when the activity resumes, in case new recordings were made
        // Only load if permissions are granted
        if (hasRequiredPermissions()) {
            loadRecordings()
        }
    }

    override fun onStop() {
        super.onStop()
        // Release MediaPlayer resources if the activity is stopped
        stopAndReleasePlayer()
    }

    private fun setupRecyclerView() {
        // Initialize adapter with click listener for playback
        recordingAdapter = RecordingAdapter(recordingsList) { recordingFile ->
            playRecording(recordingFile)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = recordingAdapter
    }

    private fun hasRequiredPermissions(): Boolean {
        val audioGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        // Add checks for other permissions if they become strictly necessary for playback
        // or listing files (though getExternalFilesDir usually doesn't need explicit storage perms)

        return audioGranted // For now, primarily checking RECORD_AUDIO which implies ability to record/save
    }


    private fun loadRecordings() {
        Log.d(TAG, "Loading recordings...")
        recordingsList.clear() // Clear the current list

        val storageDir = getExternalFilesDir(null)
        if (storageDir != null && storageDir.exists()) {
            // --- Filter for .m4a files ---
            val files = storageDir.listFiles { _, name -> name.endsWith(".m4a") }
            if (files != null) {
                // Sort files by name (which includes timestamp) descending (newest first)
                files.sortByDescending { it.name }
                recordingsList.addAll(files)
                Log.d(TAG, "Found ${recordingsList.size} recordings.")
            } else {
                Log.d(TAG, "No recording files found in directory.")
            }
        } else {
            Log.e(TAG, "Storage directory not found or doesn't exist.")
            Toast.makeText(this, "Recording directory not found.", Toast.LENGTH_SHORT).show()
        }

        // Notify the adapter that the data has changed
        recordingAdapter.notifyDataSetChanged()

        if (recordingsList.isEmpty()) {
             Toast.makeText(this, "No recordings found.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playRecording(file: File) {
        stopAndReleasePlayer() // Stop any previous playback

        if (!file.exists()) {
            Toast.makeText(this, "Error: Recording file not found!", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "File not found: ${file.absolutePath}")
            // Consider removing this entry from the list if the file is confirmed gone
            // loadRecordings() // Refresh list
            return
        }

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    Log.d(TAG, "MediaPlayer prepared, starting playback.")
                    start()
                    Toast.makeText(this@MainActivity, "Playing ${file.name}", Toast.LENGTH_SHORT).show()
                }
                setOnCompletionListener {
                    Log.d(TAG, "MediaPlayer playback completed.")
                    Toast.makeText(this@MainActivity, "Playback finished", Toast.LENGTH_SHORT).show()
                    stopAndReleasePlayer() // Clean up after completion
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    Toast.makeText(this@MainActivity, "Error playing recording", Toast.LENGTH_SHORT).show()
                    stopAndReleasePlayer() // Clean up on error
                    true // Indicate we handled the error
                }
                Log.d(TAG, "Preparing MediaPlayer for ${file.name}")
                prepareAsync() // Use async preparation
            } catch (e: IOException) {
                Log.e(TAG, "MediaPlayer IOException for ${file.absolutePath}", e)
                Toast.makeText(this@MainActivity, "Error setting up playback", Toast.LENGTH_SHORT).show()
                stopAndReleasePlayer()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "MediaPlayer IllegalStateException for ${file.absolutePath}", e)
                 Toast.makeText(this@MainActivity, "Playback error", Toast.LENGTH_SHORT).show()
                stopAndReleasePlayer()
            } catch (e: SecurityException) {
                 Log.e(TAG, "MediaPlayer SecurityException for ${file.absolutePath}", e)
                 Toast.makeText(this@MainActivity, "Playback permission error?", Toast.LENGTH_SHORT).show()
                stopAndReleasePlayer()
            }
        }
    }

    private fun stopAndReleasePlayer() {
        mediaPlayer?.let {
             try {
                 if (it.isPlaying) {
                     it.stop()
                 }
                 it.reset() 
                 it.release() 
                 Log.d(TAG, "MediaPlayer stopped and released.")
             } catch (e: IllegalStateException) {
                  Log.w(TAG, "Error stopping/releasing MediaPlayer: ${e.message}")
             }
        }
        mediaPlayer = null
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // RECORD_AUDIO is always needed for recording functionality
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // POST_NOTIFICATIONS needed for Android 13+ for the foreground service notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Add other permissions here if needed in the future

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d(TAG, "All necessary permissions already granted.")
             loadRecordings()
        }
    }
}
