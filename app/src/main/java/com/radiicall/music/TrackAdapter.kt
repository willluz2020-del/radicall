package com.radiicall.music

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.radiicall.music.databinding.ItemTrackBinding

class TrackAdapter(
    private val tracks: MutableList<Track>,
    private val onClick: (Int) -> Unit,
    private val onMore: (Int, View) -> Unit
) : RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {

    inner class TrackViewHolder(val binding: ItemTrackBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ItemTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrackViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = tracks[position]
        holder.binding.trackTitle.text = track.title
        holder.binding.trackSubtitle.text = "MP3 local"
        holder.binding.root.setOnClickListener { onClick(holder.bindingAdapterPosition) }
        holder.binding.playTrack.setOnClickListener { onClick(holder.bindingAdapterPosition) }
        holder.binding.moreTrack.setOnClickListener { view ->
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) onMore(adapterPosition, view)
        }
    }

    override fun getItemCount(): Int = tracks.size

    fun refresh() = notifyDataSetChanged()
}
