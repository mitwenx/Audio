package com.example.audiowidget

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.audiowidget.databinding.ItemRecordingBinding
import java.io.File

class RecordingListAdapter(
    private val onPlayPauseClick: (File, Int) -> Unit,
    private val onShareClick: (File) -> Unit
) : ListAdapter<File, RecordingListAdapter.RecordingViewHolder>(RecordingDiffCallback()) {

    private var playingPosition: Int = -1
    private var isPlaying: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
        val binding = ItemRecordingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecordingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
        val file = getItem(position)
        holder.bind(file, position, playingPosition == position && isPlaying, onPlayPauseClick, onShareClick)
    }

    fun updatePlaybackState(position: Int, isPlaying: Boolean) {
        val previousPlayingPosition = playingPosition
        this.playingPosition = if (isPlaying) position else -1
        this.isPlaying = isPlaying

        if (previousPlayingPosition != -1 && previousPlayingPosition < itemCount) {
             notifyItemChanged(previousPlayingPosition)
        }
        if (this.playingPosition != -1 && this.playingPosition < itemCount) {
            notifyItemChanged(this.playingPosition)
        }
    }


    class RecordingViewHolder(private val binding: ItemRecordingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            file: File,
            position: Int,
            isPlaying: Boolean,
            onPlayPauseClick: (File, Int) -> Unit,
            onShareClick: (File) -> Unit
        ) {
            binding.textViewFileName.text = file.name
            binding.buttonPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            binding.buttonPlayPause.contentDescription = binding.root.context.getString(
                if(isPlaying) R.string.pause_button_cd else R.string.play_button_cd
            )

            binding.buttonPlayPause.setOnClickListener {
                onPlayPauseClick(file, position)
            }
            binding.buttonShare.setOnClickListener {
                onShareClick(file)
            }
        }
    }

    class RecordingDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.absolutePath == newItem.absolutePath
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem == newItem // Compare File objects (path, name, etc.)
        }
    }
}
