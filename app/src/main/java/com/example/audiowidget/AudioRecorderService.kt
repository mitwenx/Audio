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
import java.text.SimpleDateFormat
import java.util.*

class AudioRecorderService : Service() {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    companion object {
        private const val CHANNEL_ID = "AudioRecorderServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "AudioRecorderService"
        private const val AUDIO_BITRATE = 128000 // Standard quality bitrate for AAC
        private const val AUDIO_SAMPLE_RATE = 44100 // Standard CD quality sample rate
        private const val FILE_EXTENSION = ".m4a" // Use M4A extension for AAC audio in MP4 container
        private const val OUTPUT_FORMAT = MediaRecorder.OutputFormat.MPEG_4 // MP4 Container
        private const val AUDIO_ENCODER = MediaRecorder.AudioEncoder.AAC // High-quality AAC encoder
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
                    0
                }
            )
            Log.d(TAG, "startForeground called successfully")
            startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_mic_widget) // Ensure this drawable exists
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startRecording() {
        if (recorder != null) {
            Log.w(TAG, "Recorder already running, stopping previous instance first.")
            stopRecordingAndCleanup()
        }

        val storageDir = getExternalFilesDir(null)
        if (storageDir == null) {
            Log.e(TAG, "External storage not available or accessible.")
            stopSelf()
            return
        }

        try {
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            // Use the new file extension
            outputFile = File(storageDir, "recording_$timestamp$FILE_EXTENSION")
            Log.d(TAG, "Attempting to record to: ${outputFile?.absolutePath}")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                // Set the output format to MPEG_4 (MP4 container)
                setOutputFormat(OUTPUT_FORMAT)
                setOutputFile(outputFile!!.absolutePath)
                // Set the audio encoder to AAC
                setAudioEncoder(AUDIO_ENCODER)
                // Optionally set bitrate and sample rate for quality control
                setAudioEncodingBitRate(AUDIO_BITRATE)
                setAudioSamplingRate(AUDIO_SAMPLE_RATE)

                try {
                    prepare()
                    start()
                    Log.i(TAG, "Recording started successfully (Format: $OUTPUT_FORMAT, Encoder: $AUDIO_ENCODER).")
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "IllegalStateException on prepare/start: ${e.message}", e)
                    stopRecordingAndCleanup()
                    stopSelf()
                } catch (e: IOException) {
                    Log.e(TAG, "IOException on prepare: ${e.message}", e)
                    Log.e(TAG,"Does the app have RECORD_AUDIO permission or sufficient storage?")
                    stopRecordingAndCleanup()
                    stopSelf()
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException: Missing RECORD_AUDIO permission? ${e.message}", e)
                    stopRecordingAndCleanup()
                    stopSelf()
                } catch(e: RuntimeException) {
                    // Catch RuntimeException which can occur if encoder/format is unsupported
                    Log.e(TAG, "RuntimeException on prepare/start (Unsupported format/encoder?): ${e.message}", e)
                    stopRecordingAndCleanup()
                    stopSelf()
                }
            }
        } catch (e: Exception) {
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
            recorder?.apply {
                try {
                    stop()
                    Log.d(TAG, "Recorder stopped.")
                } catch (stopException: IllegalStateException) {
                    // This can happen if stop() is called before start() or after release()
                    Log.w(TAG, "Exception stopping recorder (might not have been started or already stopped): ${stopException.message}")
                    // Consider deleting the file if stop failed, as it might be corrupt/empty
                    outputFile?.delete()
                    Log.w(TAG,"Deleted potentially incomplete file: ${outputFile?.name}")
                } catch (runtimeException: RuntimeException) {
                    // Stop can also throw RuntimeException on some devices/conditions
                    Log.w(TAG, "RuntimeException stopping recorder: ${runtimeException.message}")
                    outputFile?.delete()
                    Log.w(TAG,"Deleted potentially incomplete file: ${outputFile?.name}")
                }
                // release() should always be called to free resources
                release()
                Log.d(TAG, "Recorder released.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during recorder cleanup: ${e.message}", e)
        } finally {
            recorder = null
            outputFile = null // Clear file reference
            Log.d(TAG, "Recorder resources cleaned up.")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        stopRecordingAndCleanup()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Log.d(TAG,"stopForeground called.")
        super.onDestroy()
        // Update widgets to reflect service stopped state
        AudioWidget.updateAllWidgets(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
