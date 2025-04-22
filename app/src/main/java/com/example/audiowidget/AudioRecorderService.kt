package com.example.audiowidget

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.File
import java.io.IOException

class AudioRecorderService : Service() {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    companion object {
        private const val CHANNEL_ID = "AudioRecorderServiceChannel"
        private const val NOTIFICATION_ID = 1 // Use a non-zero ID
        private const val TAG = "AudioRecorderService"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        val notification = createNotification()

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0 // Type not needed before Q
                }
            )
            Log.d(TAG, "startForeground called successfully")
            startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
            // Attempt to stop gracefully if foreground fails
            stopSelf()
        }


        // If the service is killed, it will not be automatically restarted.
        // If recording was interrupted, it stops.
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name) // Define in strings.xml
            val descriptionText = getString(R.string.channel_description) // Define in strings.xml
            val importance = NotificationManager.IMPORTANCE_LOW // Low importance for ongoing tasks
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    private fun createNotification(): Notification {
        // Intent to open the app (optional)
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Recording")
            .setContentText("Recording in progress...")
            .setSmallIcon(R.drawable.ic_mic) // Ensure this drawable exists
            .setContentIntent(pendingIntent) // Optional: action when notification tapped
            .setOngoing(true) // Makes the notification non-dismissable by user swipe
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority
             // .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE) // If needed
            .build()
    }

    private fun startRecording() {
        if (recorder != null) {
            Log.w(TAG, "Recorder already running, stopping previous instance first.")
            stopRecordingAndCleanup() // Ensure clean state before starting new
        }

        val storageDir = getExternalFilesDir(null)
        if (storageDir == null) {
            Log.e(TAG, "External storage not available or accessible.")
            // Notify user or log error appropriately
            stopSelf() // Cannot record without storage
            return
        }

        try {
            // Ensure the directory exists
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }

            outputFile = File(storageDir, "recording_${System.currentTimeMillis()}.3gp")
            Log.d(TAG, "Attempting to record to: ${outputFile?.absolutePath}")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setOutputFile(outputFile!!.absolutePath) // Use !! as we checked storageDir
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

                try {
                    prepare()
                    start()
                    Log.i(TAG, "Recording started successfully.")
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "IllegalStateException on prepare/start: ${e.message}", e)
                    stopRecordingAndCleanup() // Clean up resources
                    stopSelf() // Stop service if recording fails to start
                } catch (e: IOException) {
                    Log.e(TAG, "IOException on prepare: ${e.message}", e)
                     Log.e(TAG,"Does the app have RECORD_AUDIO permission?")
                    stopRecordingAndCleanup()
                    stopSelf()
                } catch (e: SecurityException) {
                     Log.e(TAG, "SecurityException: Missing RECORD_AUDIO permission? ${e.message}", e)
                     // Notify user about permission needed
                     stopRecordingAndCleanup()
                     stopSelf()
                 }
            }
        } catch (e: Exception) {
            // Catch any other unexpected errors during setup
             Log.e(TAG, "Unexpected error setting up recorder: ${e.message}", e)
             stopRecordingAndCleanup()
             stopSelf()
        }
    }

    private fun stopRecordingAndCleanup() {
        Log.d(TAG, "Attempting to stop and release recorder.")
        if (recorder == null) {
             Log.d(TAG,"Recorder is already null.")
             return
        }

        try {
            // Need to handle potential exceptions during stop/release
            recorder?.apply {
                // Using stop() might throw IllegalStateException if not recording
                // A check or more robust state machine might be needed in complex cases
                // For simplicity, we try-catch around stop and release
                try {
                    stop()
                    Log.d(TAG, "Recorder stopped.")
                } catch (stopException: IllegalStateException) {
                    Log.w(TAG, "Exception stopping recorder (might have already been stopped or not started): ${stopException.message}")
                    // If stop fails, file might be invalid, consider deleting
                    outputFile?.delete()
                    Log.w(TAG,"Deleted potentially incomplete file: ${outputFile?.name}")
                }

                // Release should always be called
                 release()
                Log.d(TAG, "Recorder released.")
            }
        } catch (e: Exception) {
            // Catch any other exception during cleanup
            Log.e(TAG, "Exception during recorder cleanup: ${e.message}", e)
        } finally {
            recorder = null // Ensure recorder is set to null
             outputFile = null // Clear file reference
            Log.d(TAG, "Recorder resources cleaned up.")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        stopRecordingAndCleanup()
        // Remove the notification when the service is destroyed
        // Use STOP_FOREGROUND_REMOVE to remove the notification
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Log.d(TAG,"stopForeground called.")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We don't provide binding, so return null
        return null
    }
}
