package com.japaneixxx.odin

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.japaneixxx.odin.data.database.OdinDatabase
import com.japaneixxx.odin.data.entity.TagEntity
import com.japaneixxx.odin.data.repository.NoteRepository
import com.japaneixxx.odin.databinding.ActivityMainBinding
import com.japaneixxx.odin.ui.AddNoteDialog
import com.japaneixxx.odin.ui.ManageTagsBottomSheet
import com.japaneixxx.odin.ui.NoteAdapter
import com.japaneixxx.odin.ui.NoteViewModel
import com.japaneixxx.odin.ui.NoteViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val viewModel: NoteViewModel by viewModels {
        val database = OdinDatabase.getInstance(applicationContext)
        val repository = NoteRepository(
            noteDao = database.noteDao(),
            tagDao = database.tagDao()
        )
        NoteViewModelFactory(repository)
    }

    private lateinit var noteAdapter: NoteAdapter
    private var addNoteDialog: AddNoteDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearchAndFilters()
        observeViewModel()

        binding.btnManageTags.setOnClickListener {
            val manageSheet = ManageTagsBottomSheet(
                tagsList = viewModel.allTags.value,
                onUpdateTag = { tag, newName, newColor ->
                    viewModel.updateTag(tag, newName, newColor)
                },
                onDeleteTag = { tag ->
                    viewModel.deleteTag(tag)
                }
            )
            manageSheet.show(supportFragmentManager, "ManageTagsBottomSheet")
        }

        binding.fabAddNote.setOnClickListener {
            addNoteDialog = AddNoteDialog(
                allTags = viewModel.allTags.value,
                onSaveNote = { title, content, selectedTagIds, isPinned ->
                    viewModel.insertNoteWithTags(title, content, selectedTagIds)
                },
                onCreateTag = { tagName ->
                    viewModel.createTag(tagName)
                }
            )
            addNoteDialog?.show(supportFragmentManager, "AddNoteDialog")
        }
    }

    private fun setupRecyclerView() {
        noteAdapter = NoteAdapter(
            onNoteClick = { noteWithTags ->
                addNoteDialog = AddNoteDialog(
                    allTags = viewModel.allTags.value,
                    noteToEdit = noteWithTags,
                    onSaveNote = { title, content, selectedTagIds, isPinned ->
                        viewModel.updateNote(
                            note = noteWithTags.note,
                            updatedTitle = title,
                            updatedContent = content,
                            selectedTagIds = selectedTagIds,
                            isPinned = isPinned
                        )
                    },
                    onDeleteNote = { noteToDelete ->
                        viewModel.deleteNote(noteToDelete.note)
                    },
                    onCreateTag = { tagName ->
                        viewModel.createTag(tagName)
                    }
                )
                addNoteDialog?.show(supportFragmentManager, "AddNoteDialog")
            },
            onNoteLongClick = { noteWithTags ->
                viewModel.togglePinNote(noteWithTags)
            }
        )
        binding.rvNotes.layoutManager = GridLayoutManager(this, 2)
        binding.rvNotes.adapter = noteAdapter
    }

    private fun setupSearchAndFilters() {
        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            viewModel.setSearchQuery(text?.toString() ?: "")
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observa a lista de notas filtradas pelo ViewModel
                launch {
                    viewModel.filteredNotesWithTags.collectLatest { notes ->
                        noteAdapter.updateNotes(notes)
                    }
                }

                // Combina a lista de tags e o id da tag selecionada para recriar os chips sempre que um deles mudar
                launch {
                    combine(
                        viewModel.allTags,
                        viewModel.selectedFilterTagId
                    ) { tags, selectedId ->
                        tags to selectedId
                    }.collectLatest { (tags, _) ->
                        renderFilterTags(binding.cgFilterTags, tags)

                        if (addNoteDialog?.isAdded == true) {
                            addNoteDialog?.updateTags(tags)
                        }
                    }
                }
            }
        }
    }

    private fun renderFilterTags(chipGroup: ChipGroup, tags: List<TagEntity>) {
        chipGroup.removeAllViews()

        val selectedId = viewModel.selectedFilterTagId.value

        // Chip "Todas"
        val allChip = Chip(this).apply {
            text = "Todas"
            isCheckable = true
            isChecked = (selectedId == null)
            setTextColor(getColor(R.color.odin_text_primary))
            setOnClickListener {
                viewModel.setFilterTagId(null)
            }
        }
        chipGroup.addView(allChip)

        // Chips de Tags Cadastradas
        tags.forEach { tag ->
            val isSelected = (selectedId == tag.id)
            val chip = Chip(this).apply {
                text = tag.name
                isCheckable = true
                isChecked = isSelected

                // Aplica a cor da tag e formata o texto com contraste
                applyTagColor(this, tag.colorHex)

                setOnClickListener {
                    viewModel.setFilterTagId(tag.id)
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun applyTagColor(chip: Chip, colorHex: String) {
        try {
            val parsedColor = Color.parseColor(colorHex)
            chip.chipBackgroundColor = ColorStateList.valueOf(parsedColor)
            val isDark = ColorUtils.calculateLuminance(parsedColor) < 0.5
            chip.setTextColor(if (isDark) Color.WHITE else Color.BLACK)
        } catch (_: Exception) {
            chip.setTextColor(getColor(R.color.odin_text_primary))
        }
    }
}