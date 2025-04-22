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
        // Use package name prefix for action string for uniqueness
        const val WIDGET_CLICK_ACTION = "com.example.audiowidget.ACTION_WIDGET_CLICK"
        private const val TAG = "AudioWidget"
        private var lastClickTime: Long = 0L
        private const val DOUBLE_CLICK_TIME_DELTA: Long = 500 // Milliseconds threshold for double-click

        // Check if service is running - More reliable state check
        @Suppress("DEPRECATION") // Needed for older Android versions
        private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    // Check if the service is actually in the foreground
                    if (service.foreground) {
                        return true
                    }
                }
            }
            return false
        }

        // Centralized method to update all widgets
        fun updateAllWidgets(context: Context) {
            Log.d(TAG, "Requesting update for all widgets")
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, AudioWidget::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            if (ids.isNotEmpty()) {
                // Create an explicit intent directed at this AppWidgetProvider
                val intent = Intent(context, AudioWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE // Standard update action
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids) // Specify which widgets to update
                }
                context.sendBroadcast(intent) // Send broadcast to trigger onUpdate
            } else {
                 Log.d(TAG, "No widget instances found to update.")
            }
        }
    }


    // Called for ACTION_APPWIDGET_UPDATE broadcasts and when widgets are first added.
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        Log.d(TAG, "onUpdate called for widget ids: ${ids.joinToString()}")
        // Determine current recording state by checking the service
        val isRecording = isServiceRunning(context, AudioRecorderService::class.java)
        Log.d(TAG, "onUpdate - Service running state: $isRecording")

        // Update each widget instance passed in the ids array
        ids.forEach { id ->
            updateAppWidget(context, manager, id, isRecording)
        }
    }

    // Handles *all* broadcasts sent to this provider.
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive received action: ${intent.action}")
        // Always call super first for standard widget lifecycle actions
        super.onReceive(context, intent)

        // Check if it's our custom click action
        if (WIDGET_CLICK_ACTION == intent.action) {
            Log.d(TAG, "Widget click action received")
            handleWidgetClick(context)
        }
        // Note: onUpdate will be called separately by the system via ACTION_APPWIDGET_UPDATE
        // We don't need to manually call onUpdate from here unless we need immediate UI feedback
        // before the next system update cycle, which we handle via updateAllWidgets after actions.
    }

    private fun handleWidgetClick(context: Context) {
        val currentTime = SystemClock.elapsedRealtime()
        val timeSinceLastClick = currentTime - lastClickTime

        if (timeSinceLastClick < DOUBLE_CLICK_TIME_DELTA) {
            // Double-click detected
            Log.d(TAG, "Double-click detected.")
            val serviceIsRunning = isServiceRunning(context, AudioRecorderService::class.java)

            if (serviceIsRunning) {
                Log.i(TAG, "Double-click: Stopping recording service.")
                stopRecordingService(context)
                Toast.makeText(context, "Recording Stopped", Toast.LENGTH_SHORT).show()
            } else {
                Log.i(TAG, "Double-click: Starting recording service.")
                startRecordingService(context)
                // Note: Starting the service might take a moment. The UI update
                // might slightly lag until the next onUpdate or manual refresh.
                // Toast provides immediate feedback.
                Toast.makeText(context, "Recording Started", Toast.LENGTH_SHORT).show()
            }

            // Request an immediate update for all widgets to reflect the new state
            updateAllWidgets(context)

            // Reset lastClickTime to prevent triple-clicks triggering actions
            lastClickTime = 0L
        } else {
            // Single-click or clicks too far apart
            Log.d(TAG, "Single-click detected (or clicks too far apart). Storing time.")
            // Store the time of this click for potential double-click detection
            lastClickTime = currentTime
            // Optionally, provide feedback for single click
            // Toast.makeText(context, "Double-click to record/stop", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecordingService(context: Context) {
        val serviceIntent = Intent(context, AudioRecorderService::class.java)
        Log.d(TAG, "Attempting to start foreground service.")
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
             Log.i(TAG, "startForegroundService called.")
        } catch (e: SecurityException) {
             Log.e(TAG, "SecurityException starting service. Check permissions (RECORD_AUDIO, FOREGROUND_SERVICE).", e)
             Toast.makeText(context, "Error: Permission missing?", Toast.LENGTH_LONG).show()
        } catch (e: IllegalStateException) {
            // This can happen on newer Android versions if the app tries to start a
            // foreground service while it's in the background without meeting exceptions.
            // Widgets *should* be an exception, but logging helps diagnose.
            Log.e(TAG, "IllegalStateException starting service. App possibly restricted?", e)
            Toast.makeText(context, "Error: Could not start service.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // Catch any other unexpected errors
            Log.e(TAG, "Unexpected error starting service.", e)
            Toast.makeText(context, "Error starting recording.", Toast.LENGTH_LONG).show()
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

    // Updates the appearance of a single widget instance
    private fun updateAppWidget(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        isRecording: Boolean // Pass state explicitly
    ) {
        Log.d(TAG, "Updating widget ID $id. Recording state: $isRecording")
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Set icon and content description based on state
        val iconRes = if (isRecording) R.drawable.ic_stop else R.drawable.ic_mic_widget
        val contentDescRes = if (isRecording) R.string.widget_cd_stop else R.string.widget_cd_record
        views.setImageViewResource(R.id.widget_icon, iconRes)
        views.setContentDescription(R.id.widget_icon, context.getString(contentDescRes))

        // Create the PendingIntent for clicks. It should trigger our onReceive.
        val clickIntent = Intent(context, AudioWidget::class.java).apply {
            action = WIDGET_CLICK_ACTION
            // Add widget ID to intent data to make the PendingIntent unique per widget instance if needed,
            // though our current logic handles clicks globally. This prevents PendingIntents
            // for different widgets from cancelling each other if flags require it.
            // data = Uri.parse("widget://$id")
        }

        // Use FLAG_UPDATE_CURRENT so the extras (like action) are updated if the intent exists.
        // Use FLAG_IMMUTABLE as required for targeting Android 12 (API 31)+.
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0, // Using request code 0. If multiple distinct pending intents are needed, use unique codes.
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Attach the PendingIntent to the widget's icon
        views.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)

        // Tell the AppWidgetManager to apply the changes to the widget
        try {
            manager.updateAppWidget(id, views)
            Log.d(TAG, "AppWidgetManager.updateAppWidget called for ID $id")
        } catch (e: Exception) {
             Log.e(TAG, "Error updating widget ID $id: ${e.message}", e)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // Called when widgets are deleted. Cleanup?
        Log.i(TAG, "Widget(s) deleted: ${appWidgetIds.joinToString()}")
        // Optional: Stop service if the last widget is removed? Check if service is running first.
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
        // Called when the last instance of this widget provider is deleted.
        Log.i(TAG, "Widget provider disabled (last instance removed).")
         // Ensure service stops if it was running
         if (isServiceRunning(context, AudioRecorderService::class.java)) {
             Log.i(TAG,"Stopping service as widget provider is disabled.")
             stopRecordingService(context)
         }
        super.onDisabled(context)
    }

    override fun onEnabled(context: Context) {
        // Called when the first instance of this widget provider is added.
        Log.i(TAG, "Widget provider enabled (first instance added).")
        super.onEnabled(context)
    }
}
