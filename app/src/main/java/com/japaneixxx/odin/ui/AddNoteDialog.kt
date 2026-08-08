package com.japaneixxx.odin.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.DialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.japaneixxx.odin.R
import com.japaneixxx.odin.data.entity.NoteWithTags
import com.japaneixxx.odin.data.entity.TagEntity
import com.japaneixxx.odin.databinding.DialogAddNoteBinding

class AddNoteDialog(
    private var allTags: List<TagEntity>,
    private val noteToEdit: NoteWithTags? = null,
    private val onSaveNote: (title: String, content: String, selectedTagIds: List<Long>, isPinned: Boolean) -> Unit,
    private val onDeleteNote: ((noteToEdit: NoteWithTags) -> Unit)? = null,
    private val onCreateTag: (tagName: String) -> Unit
) : DialogFragment() {

    private var _binding: DialogAddNoteBinding? = null
    private val binding get() = _binding!!
    private var isPinnedState: Boolean = false

    // Guarda os IDs das tags selecionadas no diálogo
    private val selectedTagIds = mutableSetOf<Long>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Preenche com as tags já associadas caso seja uma edição
        if (noteToEdit != null) {
            binding.etTitle.setText(noteToEdit.note.title)
            binding.etContent.setText(noteToEdit.note.content)
            binding.btnSave.text = "Salvar"
            binding.btnDelete.visibility = View.VISIBLE
            isPinnedState = noteToEdit.note.isPinned
            selectedTagIds.addAll(noteToEdit.tags.map { it.id })
        }

        updatePinIcon()

        binding.btnPin.setOnClickListener {
            isPinnedState = !isPinnedState
            updatePinIcon()
        }

        binding.btnDelete.setOnClickListener {
            noteToEdit?.let { note ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Excluir nota")
                    .setMessage("Tem certeza que deseja excluir esta anotação?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Excluir") { _, _ ->
                        onDeleteNote?.invoke(note)
                        dismiss()
                    }
                    .show()
            }
        }

        renderTags(allTags)

        binding.btnCreateTag.setOnClickListener {
            val tagName = binding.etTagName.text?.toString()?.trim() ?: ""
            if (tagName.isNotEmpty()) {
                onCreateTag(tagName)
                binding.etTagName.setText("")
            }
        }

        binding.btnSave.setOnClickListener {
            val title = binding.etTitle.text?.toString()?.trim() ?: ""
            val content = binding.etContent.text?.toString()?.trim() ?: ""

            if (title.isNotEmpty() || content.isNotEmpty()) {
                // Passa a lista atualizada de IDs das tags selecionadas
                onSaveNote(title, content, selectedTagIds.toList(), isPinnedState)
                dismiss()
            } else {
                Toast.makeText(requireContext(), "Preencha o título ou o conteúdo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderTags(tags: List<TagEntity>) {
        binding.cgSelectableTags.removeAllViews()

        tags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag.name
                isCheckable = true
                isChecked = selectedTagIds.contains(tag.id)

                applyTagColor(this, tag.colorHex)

                // Garante a sincronização do conjunto de IDs selecionados ao clicar
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedTagIds.add(tag.id)
                    } else {
                        selectedTagIds.remove(tag.id)
                    }
                }
            }
            binding.cgSelectableTags.addView(chip)
        }
    }

    fun updateTags(newTags: List<TagEntity>) {
        allTags = newTags
        if (_binding != null) {
            renderTags(newTags)
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

    private fun updatePinIcon() {
        val tintColor = if (isPinnedState) {
            ContextCompat.getColor(requireContext(), R.color.odin_gold)
        } else {
            ContextCompat.getColor(requireContext(), R.color.odin_text_secondary)
        }
        binding.btnPin.setColorFilter(tintColor)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}