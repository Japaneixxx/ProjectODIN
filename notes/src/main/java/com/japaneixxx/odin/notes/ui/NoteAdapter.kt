package com.japaneixxx.odin.notes.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.japaneixxx.odin.data.entity.NoteWithTags
import com.japaneixxx.odin.notes.databinding.ItemNoteBinding
import com.japaneixxx.odin.notes.ui.utils.DateFormatter

class NoteAdapter(
    private val onNoteClick: (NoteWithTags) -> Unit,
    private val onNoteLongClick: (NoteWithTags) -> Unit
) : ListAdapter<NoteWithTags, NoteAdapter.NoteViewHolder>(NoteDiffCallback) {

    private fun Float.dpToPx(context: Context): Float {
        return this * context.resources.displayMetrics.density
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(private val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(noteWithTags: NoteWithTags) {
            val note = noteWithTags.note
            val tags = noteWithTags.tags

            binding.tvNoteTitle.text = if (note.title.isNotBlank()) note.title else "Sem título"
            binding.tvNoteContent.text = note.content
            binding.tvUpdatedAt.text = DateFormatter.formatRelativeDate(note.updatedAt)
            binding.ivPinned.visibility = if (note.isPinned) View.VISIBLE else View.GONE

            // Limpa e renderiza os chips das tags
            binding.cgNoteTags.removeAllViews()

            if (tags.isNotEmpty()) {
                binding.cgNoteTags.visibility = View.VISIBLE

                tags.forEach { tag ->
                    val chip = Chip(binding.root.context).apply {
                        text = tag.name
                        textSize = 11f
                        isClickable = false
                        isFocusable = false
                        isCheckable = false

                        chipMinHeight = 22f.dpToPx(context)
                        chipStartPadding = 8f.dpToPx(context)
                        chipEndPadding = 8f.dpToPx(context)

                        applyTagColor(this, tag.colorHex)
                    }
                    binding.cgNoteTags.addView(chip)
                }
            } else {
                binding.cgNoteTags.visibility = View.GONE
            }

            // Clique rápido: abre edição
            binding.root.setOnClickListener {
                onNoteClick(noteWithTags)
            }

            // Clique longo: alterna pin com feedback tátil
            binding.root.setOnLongClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onNoteLongClick(noteWithTags)
                true
            }
        }

        private fun applyTagColor(chip: Chip, colorHex: String) {
            try {
                val parsedColor = Color.parseColor(colorHex)
                chip.chipBackgroundColor = ColorStateList.valueOf(parsedColor)
                val isDark = ColorUtils.calculateLuminance(parsedColor) < 0.5
                chip.setTextColor(if (isDark) Color.WHITE else Color.BLACK)
            } catch (_: Exception) {}
        }
    }

    // Callback responsável por calcular diferenças entre listas de forma reativa
    object NoteDiffCallback : DiffUtil.ItemCallback<NoteWithTags>() {
        override fun areItemsTheSame(oldItem: NoteWithTags, newItem: NoteWithTags): Boolean {
            return oldItem.note.id == newItem.note.id
        }

        override fun areContentsTheSame(oldItem: NoteWithTags, newItem: NoteWithTags): Boolean {
            return oldItem == newItem
        }
    }
}