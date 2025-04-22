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
        if (storageDir != null) {
            val files = storageDir.listFiles { _, name -> name.endsWith(".3gp") }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyArray()

            recordingAdapter.submitList(files.toList())
            binding.textViewEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerViewRecordings.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
        } else {
            binding.textViewEmpty.visibility = View.VISIBLE
            binding.recyclerViewRecordings.visibility = View.GONE
            Log.e("MainActivity", "External storage directory not found.")
        }
        resetPlaybackStateVisuals()
    }

    private fun handlePlayPause(file: File, position: Int) {
        if (mediaPlayer != null && currentlyPlayingFile == file) {
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer?.pause()
                recordingAdapter.updatePlaybackState(position, false)
            } else {
                mediaPlayer?.start()
                recordingAdapter.updatePlaybackState(position, true)
            }
        } else {
            stopPlayback()
            startPlayback(file, position)
        }
    }

    private fun startPlayback(file: File, position: Int) {
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepareAsync()
                setOnPreparedListener {
                    it.start()
                    currentlyPlayingFile = file
                    currentlyPlayingPosition = position
                    recordingAdapter.updatePlaybackState(position, true)
                }
                setOnCompletionListener {
                    stopPlayback()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("MainActivity", "MediaPlayer Error: what=$what, extra=$extra")
                    Toast.makeText(this@MainActivity, R.string.error_playback, Toast.LENGTH_SHORT).show()
                    stopPlayback()
                    true
                }
            } catch (e: IOException) {
                Log.e("MainActivity", "MediaPlayer prepare failed", e)
                Toast.makeText(this@MainActivity, R.string.error_playback, Toast.LENGTH_SHORT).show()
                stopPlayback()
            } catch (e: IllegalStateException) {
                Log.e("MainActivity", "MediaPlayer state error", e)
                 Toast.makeText(this@MainActivity, R.string.error_playback, Toast.LENGTH_SHORT).show()
                 stopPlayback()
            }
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        resetPlaybackStateVisuals()
        currentlyPlayingFile = null
        currentlyPlayingPosition = -1
    }

    private fun resetPlaybackStateVisuals() {
        if (currentlyPlayingPosition != -1) {
            recordingAdapter.updatePlaybackState(currentlyPlayingPosition, false)
        }
    }

    private fun shareRecording(file: File) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "audio/3gp"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_recording_title)))
        } catch (e: IllegalArgumentException) {
            Log.e("MainActivity", "FileProvider URI generation failed.", e)
            Toast.makeText(this, R.string.error_share, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
             Log.e("MainActivity", "Failed to share file.", e)
             Toast.makeText(this, R.string.error_share, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

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
        stopPlayback()
    }
}
