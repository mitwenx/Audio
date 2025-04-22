package com.example.audiowidget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingAdapter(
    private val recordings: List<File>,
    private val onItemClicked: (File) -> Unit // Lambda for click events
) : RecyclerView.Adapter<RecordingAdapter.RecordingViewHolder>() {

    // SimpleDateFormat for formatting the timestamp from the filename
    private val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val inputPattern = Regex("recording_(\\d+)\\.3gp") // Regex to extract timestamp

    // ViewHolder holds references to the views in list_item_recording.xml
    inner class RecordingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val filenameTextView: TextView = itemView.findViewById(R.id.recording_filename_text)

        fun bind(file: File) {
            // Attempt to format the name nicely
            val formattedName = formatFileName(file.name)
            filenameTextView.text = formattedName

            // Set the click listener on the entire item view
            itemView.setOnClickListener {
                onItemClicked(file) // Pass the File object back to the activity
            }
        }

        private fun formatFileName(originalName: String): String {
            val matchResult = inputPattern.matchEntire(originalName)
            return if (matchResult != null) {
                try {
                    val timestamp = matchResult.groupValues[1].toLong()
                    val date = Date(timestamp)
                    "Rec_${outputFormat.format(date)}.3gp" // Prepend "Rec_" and format date
                } catch (e: Exception) {
                    originalName // Fallback to original name if timestamp parsing fails
                }
            } else {
                originalName // Fallback if filename doesn't match pattern
            }
        }
    }

    // Called when RecyclerView needs a new ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
        // Inflate the layout for each list item
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_recording, parent, false)
        return RecordingViewHolder(view)
    }

    // Called by RecyclerView to display the data at the specified position
    override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
        val recordingFile = recordings[position]
        holder.bind(recordingFile) // Bind the data to the ViewHolder
    }

    // Returns the total number of items in the list
    override fun getItemCount(): Int {
        return recordings.size
    }
}
