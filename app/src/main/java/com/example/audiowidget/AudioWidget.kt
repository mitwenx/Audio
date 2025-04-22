package com.example.audiowidget

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat

class AudioWidget : AppWidgetProvider() {

    companion object {
        const val WIDGET_CLICK_ACTION = "com.example.audiowidget.ACTION_WIDGET_CLICK"
        private const val TAG = "AudioWidget"
        private var lastClickTime: Long = 0L
        private const val DOUBLE_CLICK_TIME_DELTA: Long = 500

        @Suppress("DEPRECATION")
        private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            manager?.getRunningServices(Integer.MAX_VALUE)?.forEach { service ->
                if (serviceClass.name == service.service.className) {
                    if (service.foreground) {
                        return true
                    }
                }
            }
            return false
        }

        fun updateAllWidgets(context: Context) {
            Log.d(TAG, "Requesting update for all widgets")
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, AudioWidget::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            if (ids.isNotEmpty()) {
                val isRecording = isServiceRunning(context, AudioRecorderService::class.java)
                Log.d(TAG, "updateAllWidgets - Service running state: $isRecording")
                val intent = Intent(context, AudioWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    putExtra("IS_RECORDING_STATE", isRecording)
                }
                context.sendBroadcast(intent)
            } else {
                 Log.d(TAG, "No widget instances found to update.")
            }
        }
    }

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        Log.d(TAG, "onUpdate called for widget ids: ${ids.joinToString()}")
        val isRecording = isServiceRunning(context, AudioRecorderService::class.java)
        Log.d(TAG, "onUpdate - Service running state (queried): $isRecording")

        ids.forEach { id ->
            updateAppWidget(context, manager, id, isRecording)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive received action: ${intent.action}")
        val action = intent.action

        if (WIDGET_CLICK_ACTION == action) {
             Log.d(TAG, "Widget click action received")
             handleWidgetClick(context)
        } else if (AppWidgetManager.ACTION_APPWIDGET_UPDATE == action && intent.hasExtra("IS_RECORDING_STATE")) {
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            val isRecording = intent.getBooleanExtra("IS_RECORDING_STATE", false)
            Log.d(TAG, "onReceive - Manual update broadcast - State: $isRecording")
            if (ids != null) {
                 val manager = AppWidgetManager.getInstance(context)
                 ids.forEach { id ->
                     updateAppWidget(context, manager, id, isRecording)
                 }
            } else {
                super.onReceive(context, intent)
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    private fun handleWidgetClick(context: Context) {
        val currentTime = SystemClock.elapsedRealtime()
        val timeSinceLastClick = currentTime - lastClickTime

        if (timeSinceLastClick < DOUBLE_CLICK_TIME_DELTA) {
            Log.d(TAG, "Double-click detected.")
            val serviceIsRunning = isServiceRunning(context, AudioRecorderService::class.java)

            if (serviceIsRunning) {
                Log.i(TAG, "Double-click: Stopping recording service.")
                stopRecordingService(context)
                Toast.makeText(context, R.string.recording_stopped, Toast.LENGTH_SHORT).show()
            } else {
                Log.i(TAG, "Double-click: Starting recording service.")
                startRecordingService(context)
                Toast.makeText(context, R.string.recording_started, Toast.LENGTH_SHORT).show()
            }

            updateAllWidgets(context)
            lastClickTime = 0L
        } else {
            Log.d(TAG, "Single-click detected. Storing time.")
            lastClickTime = currentTime
        }
    }

    private fun startRecordingService(context: Context) {
        val serviceIntent = Intent(context, AudioRecorderService::class.java)
        Log.d(TAG, "Attempting to start foreground service.")
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.i(TAG, "startForegroundService called.")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting service.", e)
            Toast.makeText(context, R.string.error_permission_missing, Toast.LENGTH_LONG).show()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException starting service.", e)
            Toast.makeText(context, R.string.error_start_service, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting service.", e)
            Toast.makeText(context, R.string.error_start_recording, Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecordingService(context: Context) {
        val serviceIntent = Intent(context, AudioRecorderService::class.java)
        Log.d(TAG, "Attempting to stop service.")
        val stopped = context.stopService(serviceIntent)
        Log.i(TAG, "stopService called. Result: $stopped")
        if (!stopped) {
            Log.w(TAG,"stopService returned false. Service might not have been running.")
        }
    }

    private fun updateAppWidget(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        isRecording: Boolean
    ) {
        Log.d(TAG, "Updating widget ID $id. Recording state: $isRecording")
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        val iconRes = if (isRecording) R.drawable.ic_stop else R.drawable.ic_mic_widget
        val contentDescRes = if (isRecording) R.string.widget_cd_stop else R.string.widget_cd_record
        views.setImageViewResource(R.id.widget_icon, iconRes)
        views.setContentDescription(R.id.widget_icon, context.getString(contentDescRes))

        val clickIntent = Intent(context, AudioWidget::class.java).apply {
            action = WIDGET_CLICK_ACTION
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)

        try {
            manager.updateAppWidget(id, views)
            Log.d(TAG, "AppWidgetManager.updateAppWidget called for ID $id")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget ID $id: ${e.message}", e)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Log.i(TAG, "Widget(s) deleted: ${appWidgetIds.joinToString()}")
        val manager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, AudioWidget::class.java)
        if (manager.getAppWidgetIds(componentName).isEmpty()) {
             Log.d(TAG,"Last widget instance deleted.")
             if (isServiceRunning(context, AudioRecorderService::class.java)) {
                 Log.i(TAG,"Stopping service as last widget was deleted.")
                 stopRecordingService(context)
             }
        }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        Log.i(TAG, "Widget provider disabled (last instance removed).")
         if (isServiceRunning(context, AudioRecorderService::class.java)) {
             Log.i(TAG,"Stopping service as widget provider is disabled.")
             stopRecordingService(context)
         }
        super.onDisabled(context)
    }

    override fun onEnabled(context: Context) {
        Log.i(TAG, "Widget provider enabled (first instance added).")
        super.onEnabled(context)
    }
}
