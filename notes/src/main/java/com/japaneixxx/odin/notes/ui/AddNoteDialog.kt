package com.japaneixxx.odin.notes.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.japaneixxx.odin.data.entity.NoteWithTags
import com.japaneixxx.odin.data.entity.PersonEntity
import com.japaneixxx.odin.data.entity.TagEntity
import com.japaneixxx.odin.notes.R
import com.japaneixxx.odin.notes.databinding.DialogAddNoteBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AddNoteDialog(
    private var allTags: List<TagEntity>,
    private val noteToEdit: NoteWithTags? = null,
    private val onSearchMention: suspend (query: String) -> List<PersonEntity>,
    private val onSaveNote: (
        title: String,
        content: String,
        selectedTagIds: List<Long>,
        isPinned: Boolean,
        mentionedPersonIds: List<Long>
    ) -> Unit,
    private val onDeleteNote: ((noteToEdit: NoteWithTags) -> Unit)? = null,
    private val onCreateTag: (tagName: String) -> Unit
) : DialogFragment() {

    private var _binding: DialogAddNoteBinding? = null
    private val binding get() = _binding!!
    private var isPinnedState: Boolean = false

    private val selectedTagIds = mutableSetOf<Long>()

    private var mentionSearchJob: Job? = null
    private var currentMentionStartIndex: Int = -1
    private val currentMentionedPersons = mutableListOf<PersonEntity>()
    private val mentionedPersonIds = mutableSetOf<Long>()
    private lateinit var mentionAdapter: MentionSuggestionAdapter

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

        if (noteToEdit != null) {
            binding.etTitle.setText(noteToEdit.note.title)
            binding.etContent.setText(noteToEdit.note.content)
            binding.btnSave.text = "Salvar"
            binding.btnDelete.visibility = View.VISIBLE
            isPinnedState = noteToEdit.note.isPinned
            selectedTagIds.clear()
            selectedTagIds.addAll(noteToEdit.tags.map { it.id })
        } else {
            binding.btnSave.text = "Criar"
            binding.btnDelete.visibility = View.GONE
        }

        updatePinIcon()
        setupMentionSuggestions()

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
                onSaveNote(
                    title,
                    content,
                    selectedTagIds.toList(),
                    isPinnedState,
                    mentionedPersonIds.toList()
                )
                dismiss()
            } else {
                Toast.makeText(requireContext(), "Preencha o título ou o conteúdo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupMentionSuggestions() {
        mentionAdapter = MentionSuggestionAdapter { selectedPerson ->
            val displayName = selectedPerson.nickname?.takeIf { it.isNotBlank() }
                ?: selectedPerson.name.split(" ")[0]

            mentionedPersonIds.add(selectedPerson.id)

            val editable = binding.etContent.text
            if (editable != null && currentMentionStartIndex != -1) {
                val cursorPosition = binding.etContent.selectionStart
                if (cursorPosition >= currentMentionStartIndex) {
                    editable.replace(currentMentionStartIndex, cursorPosition, "@$displayName ")
                }
            }
            hideMentionSuggestions()
        }

        binding.rvMentionSuggestions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMentionSuggestions.adapter = mentionAdapter

        binding.etContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: return
                val cursorPosition = binding.etContent.selectionStart

                if (cursorPosition <= 0) {
                    hideMentionSuggestions()
                    return
                }

                var atSymbolIndex = -1
                for (i in cursorPosition - 1 downTo 0) {
                    if (text[i] == '@') {
                        if (i == 0 || text[i - 1].isWhitespace()) {
                            atSymbolIndex = i
                            break
                        }
                    } else if (text[i].isWhitespace()) {
                        break
                    }
                }

                if (atSymbolIndex != -1) {
                    currentMentionStartIndex = atSymbolIndex
                    val query = text.substring(atSymbolIndex + 1, cursorPosition)
                    searchMention(query)
                } else {
                    hideMentionSuggestions()
                    currentMentionStartIndex = -1
                }
            }
        })
    }

    private fun searchMention(query: String) {
        mentionSearchJob?.cancel()
        mentionSearchJob = lifecycleScope.launch {
            delay(50)
            val cleanQuery = query.trim()

            // 🎯 Chama a busca fornecida pela ViewModel / Repositório
            val results = onSearchMention(cleanQuery)

            Log.d("ODIN_MENTION", "Busca por '$cleanQuery' retornou ${results.size} resultados: ${results.map { it.name }}")

            if (!isAdded || _binding == null) return@launch

            currentMentionedPersons.clear()
            if (results.isNotEmpty()) {
                currentMentionedPersons.addAll(results)
                mentionAdapter.submitList(currentMentionedPersons)
                binding.rvMentionSuggestions.visibility = View.VISIBLE
                binding.rvMentionSuggestions.bringToFront()
            } else {
                hideMentionSuggestions()
            }
        }
    }

    private fun hideMentionSuggestions() {
        if (_binding != null) {
            binding.rvMentionSuggestions.visibility = View.GONE
        }
        currentMentionedPersons.clear()
    }

    private fun renderTags(tags: List<TagEntity>) {
        binding.cgSelectableTags.removeAllViews()

        tags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag.name
                isCheckable = true
                isChecked = selectedTagIds.contains(tag.id)
                applyTagColor(this, tag.colorHex)

                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedTagIds.add(tag.id) else selectedTagIds.remove(tag.id)
                }
            }
            binding.cgSelectableTags.addView(chip)
        }
    }

    fun updateTags(newTags: List<TagEntity>) {
        allTags = newTags
        if (_binding != null) renderTags(newTags)
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

    inner class MentionSuggestionAdapter(
        private val onItemClick: (PersonEntity) -> Unit
    ) : RecyclerView.Adapter<MentionSuggestionAdapter.ViewHolder>() {

        private var items = listOf<PersonEntity>()

        fun submitList(newItems: List<PersonEntity>) {
            items = newItems.toList()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mention_suggestion, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val person = items[position]
            val displayName = person.nickname?.takeIf { it.isNotBlank() } ?: person.name
            holder.tvName.text = "👤 $displayName"
            holder.itemView.setOnClickListener { onItemClick(person) }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvMentionName)
        }
    }
}