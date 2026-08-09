package com.japaneixxx.odin.notes.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.japaneixxx.odin.data.entity.NoteWithTags
import com.japaneixxx.odin.data.entity.TagEntity
import com.japaneixxx.odin.notes.databinding.DialogEditNoteBinding

class EditNoteBottomSheet(
    private val noteWithTags: NoteWithTags,
    private val availableTags: List<TagEntity>,
    private val onUpdate: (title: String, content: String, selectedTagIds: List<Long>) -> Unit,
    private val onDelete: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogEditNoteBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Preenche os campos com os dados atuais da nota
        binding.etEditTitle.setText(noteWithTags.note.title)
        binding.etEditContent.setText(noteWithTags.note.content)

        // Renderiza as tags disponíveis marcando as que a nota já possui
        val currentTagIds = noteWithTags.tags.map { it.id }.toSet()

        availableTags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag.name
                isCheckable = true
                isChecked = currentTagIds.contains(tag.id)
                this.tag = tag.id
            }
            binding.cgEditTags.addView(chip)
        }

        // Evento de Salvar
        binding.btnUpdateNote.setOnClickListener {
            val updatedTitle = binding.etEditTitle.text.toString().trim()
            val updatedContent = binding.etEditContent.text.toString().trim()

            val selectedTagIds = mutableListOf<Long>()
            for (i in 0 until binding.cgEditTags.childCount) {
                val chip = binding.cgEditTags.getChildAt(i) as? Chip
                if (chip?.isChecked == true) {
                    (chip.tag as? Long)?.let { selectedTagIds.add(it) }
                }
            }

            if (updatedTitle.isNotEmpty() && updatedContent.isNotEmpty()) {
                onUpdate(updatedTitle, updatedContent, selectedTagIds)
                dismiss()
            } else {
                Toast.makeText(context, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
            }
        }

        // Evento de Deletar
        binding.btnDeleteNote.setOnClickListener {
            onDelete()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}