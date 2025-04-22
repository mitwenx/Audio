package com.example.audiowidget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.audiowidget.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingAdapter(
    private val recordings: List<File>,
    private val onItemClicked: (File) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.RecordingViewHolder>() {

    
    private val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val inputPattern = Regex("recording_(\\d+)\\.m4a")

    inner class RecordingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val filenameTextView: TextView = itemView.findViewById(R.id.recording_filename_text)

        fun bind(file: File) {
            val formattedName = formatFileName(file.name)
            filenameTextView.text = formattedName
            itemView.setOnClickListener {
                onItemClicked(file)
            }
        }

        private fun formatFileName(originalName: String): String {
            val matchResult = inputPattern.matchEntire(originalName)
            return if (matchResult != null) {
                try {
                    val timestamp = matchResult.groupValues[1].toLong()
                    val date = Date(timestamp)
                    
                    "Rec_${outputFormat.format(date)}.m4a"
                } catch (e: Exception) {
                    originalName
                }
            } else {
                originalName
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_recording, parent, false)
        return RecordingViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
        val recordingFile = recordings[position]
        holder.bind(recordingFile)
    }

    override fun getItemCount(): Int {
        return recordings.size
    }
}
