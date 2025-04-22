package com.example.audiowidget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.audiowidget.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recordingAdapter: RecordingListAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingFile: File? = null
    private var currentlyPlayingPosition: Int = -1

    // Define the expected file extension
    private val recordingFileExtension = ".m4a"
    // Define the MIME type for sharing
    private val recordingMimeType = "audio/mp4" // M4A files use the MP4 container format

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                if (!it.value) {
                    allGranted = false
                    Log.w("MainActivity", "Permission denied: ${it.key}")
                } else {
                    Log.i("MainActivity", "Permission granted: ${it.key}")
                }
            }
            if (allGranted) {
                Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.permissions_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        loadRecordings()
    }

    override fun onPause() {
        super.onPause()
        stopPlayback()
    }

    private fun setupRecyclerView() {
        recordingAdapter = RecordingListAdapter(
            onPlayPauseClick = { file, position -> handlePlayPause(file, position) },
            onShareClick = { file -> shareRecording(file) }
        )
        binding.recyclerViewRecordings.apply {
            adapter = recordingAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun loadRecordings() {
        val storageDir = getExternalFilesDir(null)
        if (storageDir != null && storageDir.exists()) {
            // Look for files with the new extension
            val filesArray = storageDir.listFiles { _, name -> name.endsWith(recordingFileExtension) }
            val filesList: List<File> = if (filesArray != null) {
                filesArray.sortedByDescending { it.lastModified() }
            } else {
                emptyList()
            }

            recordingAdapter.submitList(filesList)
            binding.textViewEmpty.visibility = if (filesList.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerViewRecordings.visibility = if (filesList.isEmpty()) View.GONE else View.VISIBLE
        } else {
            recordingAdapter.submitList(emptyList()) // Clear the adapter list
            binding.textViewEmpty.visibility = View.VISIBLE
            binding.recyclerViewRecordings.visibility = View.GONE
            if (storageDir == null) {
                Log.e("MainActivity", "External storage directory not found.")
            } else {
                Log.w("MainActivity", "External storage directory does not exist: ${storageDir.absolutePath}")
            }
        }
        resetPlaybackStateVisuals() // Ensure visuals are reset after loading
    }


    private fun handlePlayPause(file: File, position: Int) {
        if (mediaPlayer != null && currentlyPlayingFile == file) {
            // Toggle play/pause for the currently loaded file
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer?.pause()
                recordingAdapter.updatePlaybackState(position, false)
            } else {
                mediaPlayer?.start()
                recordingAdapter.updatePlaybackState(position, true)
            }
        } else {
            // Stop any previous playback and start the new file
            stopPlayback()
            startPlayback(file, position)
        }
    }

    private fun startPlayback(file: File, position: Int) {
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepareAsync() // Use async prepare to avoid blocking UI thread
                setOnPreparedListener {
                    it.start()
                    currentlyPlayingFile = file
                    currentlyPlayingPosition = position
                    recordingAdapter.updatePlaybackState(position, true)
                    Log.i("MainActivity", "Playback started for ${file.name}")
                }
                setOnCompletionListener {
                    Log.i("MainActivity", "Playback completed for ${file.name}")
                    stopPlayback() // Clean up when playback finishes
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e("MainActivity", "MediaPlayer Error: what=$what, extra=$extra for file: ${file.absolutePath}")
                    Toast.makeText(this@MainActivity, R.string.error_playback, Toast.LENGTH_SHORT).show()
                    stopPlayback() // Clean up on error
                    true // Indicate we've handled the error
                }
            } catch (e: IOException) {
                Log.e("MainActivity", "MediaPlayer prepare failed for file: ${file.absolutePath}", e)
                Toast.makeText(this@MainActivity, R.string.error_playback, Toast.LENGTH_SHORT).show()
                stopPlayback()
            } catch (e: IllegalStateException) {
                // Catch potential state issues (e.g., calling methods in wrong order)
                Log.e("MainActivity", "MediaPlayer state error for file: ${file.absolutePath}", e)
                Toast.makeText(this@MainActivity, R.string.error_playback, Toast.LENGTH_SHORT).show()
                stopPlayback()
            } catch (e: SecurityException) {
                 Log.e("MainActivity", "MediaPlayer security error for file: ${file.absolutePath}", e)
                 Toast.makeText(this@MainActivity, R.string.error_playback, Toast.LENGTH_SHORT).show()
                 stopPlayback()
             }
        }
    }

    private fun stopPlayback() {
        // Check if mediaPlayer is actually initialized before trying to release
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer!!.isPlaying) {
                    mediaPlayer?.stop() // Consider stop() before release() if needed, though release() usually handles it
                }
                mediaPlayer?.reset() // Reset the state
                mediaPlayer?.release() // Release resources
                Log.d("MainActivity", "MediaPlayer released.")
            } catch (e: IllegalStateException) {
                 Log.w("MainActivity", "IllegalStateException during MediaPlayer release/reset.", e)
            } catch (e: Exception) {
                Log.e("MainActivity", "Exception during MediaPlayer release.", e)
            } finally {
                mediaPlayer = null
            }
        }
        // Always reset visuals and state variables
        resetPlaybackStateVisuals()
        currentlyPlayingFile = null
        currentlyPlayingPosition = -1
    }


    private fun resetPlaybackStateVisuals() {
        // Ensure adapter interaction happens only if the position is valid
        if (currentlyPlayingPosition != -1 && currentlyPlayingPosition < recordingAdapter.itemCount) {
            recordingAdapter.updatePlaybackState(currentlyPlayingPosition, false)
        } else if (playingPosition != -1 && playingPosition < recordingAdapter.itemCount) {
            // If playback stopped but state wasn't reset (e.g. error), try resetting previous known position
            recordingAdapter.updatePlaybackState(playingPosition, false)
        }
    }


    private fun shareRecording(file: File) {
        if (!file.exists()) {
            Log.e("MainActivity", "Share failed: File does not exist: ${file.absolutePath}")
            Toast.makeText(this, R.string.error_share, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider", // Authority must match AndroidManifest
                file
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, contentUri)
                // Set the correct MIME type for M4A/MP4 audio
                type = recordingMimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Grant temporary read permission
            }
            // Use a chooser to let the user select the app to share with
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_recording_title)))
            Log.i("MainActivity", "Sharing intent created for ${file.name} with URI $contentUri")

        } catch (e: IllegalArgumentException) {
            // This can happen if the FileProvider setup is incorrect (e.g., authority mismatch)
            Log.e("MainActivity", "FileProvider URI generation failed for ${file.name}. Check FileProvider config.", e)
            Toast.makeText(this, R.string.error_share, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
             // Catch any other exceptions during intent creation or starting activity
             Log.e("MainActivity", "Failed to create or start share intent for ${file.name}.", e)
             Toast.makeText(this, R.string.error_share, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // POST_NOTIFICATIONS needed for Android 13 (API 33)+ for foreground service notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("MainActivity", "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d("MainActivity", "All necessary permissions already granted.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure MediaPlayer resources are released when the activity is destroyed
        stopPlayback()
    }

    // Add a placeholder variable to potentially track the playing position from adapter's perspective
    // This helps if resetPlaybackStateVisuals needs to know the last playing item even if mediaPlayer is null
    private var playingPosition: Int = -1
    // Helper in adapter notifies MainActivity or similar structure if needed, or use this direct variable
    // Example: recordingAdapter.setPlaybackPositionListener { pos -> playingPosition = pos }
    // Or handle within updatePlaybackState in adapter itself. Let's keep it simple for now.

}
