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
            .setSmallIcon(R.drawable.ic_mic_widget)
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
            outputFile = File(storageDir, "recording_$timestamp.3gp")
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
                setOutputFile(outputFile!!.absolutePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

                try {
                    prepare()
                    start()
                    Log.i(TAG, "Recording started successfully.")
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "IllegalStateException on prepare/start: ${e.message}", e)
                    stopRecordingAndCleanup()
                    stopSelf()
                } catch (e: IOException) {
                    Log.e(TAG, "IOException on prepare: ${e.message}", e)
                    Log.e(TAG,"Does the app have RECORD_AUDIO permission?")
                    stopRecordingAndCleanup()
                    stopSelf()
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException: Missing RECORD_AUDIO permission? ${e.message}", e)
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
                    Log.w(TAG, "Exception stopping recorder: ${stopException.message}")
                    outputFile?.delete()
                    Log.w(TAG,"Deleted potentially incomplete file: ${outputFile?.name}")
                }
                release()
                Log.d(TAG, "Recorder released.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during recorder cleanup: ${e.message}", e)
        } finally {
            recorder = null
            outputFile = null
            Log.d(TAG, "Recorder resources cleaned up.")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        stopRecordingAndCleanup()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Log.d(TAG,"stopForeground called.")
        super.onDestroy()
        AudioWidget.updateAllWidgets(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
