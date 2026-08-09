package com.japaneixxx.odin.notes

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.japaneixxx.odin.data.database.OdinDatabase
import com.japaneixxx.odin.data.entity.NoteWithTags
import com.japaneixxx.odin.data.entity.TagEntity
import com.japaneixxx.odin.data.repository.NoteRepository
import com.japaneixxx.odin.notes.databinding.ActivityMainBinding
import com.japaneixxx.odin.notes.ui.AddNoteDialog
import com.japaneixxx.odin.notes.ui.ManageTagsBottomSheet
import com.japaneixxx.odin.notes.ui.NoteAdapter
import com.japaneixxx.odin.notes.ui.NoteViewModel
import com.japaneixxx.odin.notes.ui.NoteViewModelFactory
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
            showAddNoteDialog(null)
        }
    }

    private fun setupRecyclerView() {
        noteAdapter = NoteAdapter(
            onNoteClick = { noteWithTags ->
                showAddNoteDialog(noteWithTags)
            },
            onNoteLongClick = { noteWithTags ->
                viewModel.togglePinNote(noteWithTags)
            }
        )
        binding.rvNotes.layoutManager = GridLayoutManager(this, 2)
        binding.rvNotes.adapter = noteAdapter
        binding.rvNotes.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && binding.fabAddNote.isShown) {
                    binding.fabAddNote.hide() // Esconde suavemente ao rolar para baixo
                } else if (dy < 0 && !binding.fabAddNote.isShown) {
                    binding.fabAddNote.show() // Reaparece ao rolar para cima
                }
            }
        })
    }

    private fun showAddNoteDialog(noteToEdit: NoteWithTags?) {
        addNoteDialog = AddNoteDialog(
            allTags = viewModel.allTags.value,
            noteToEdit = noteToEdit,
            onSaveNote = { title, content, selectedTagIds, isPinned ->
                if (noteToEdit == null) {
                    viewModel.insertNoteWithTags(title, content, selectedTagIds)
                } else {
                    viewModel.updateNote(
                        note = noteToEdit.note,
                        updatedTitle = title,
                        updatedContent = content,
                        selectedTagIds = selectedTagIds,
                        isPinned = isPinned
                    )
                }
            },
            onDeleteNote = { noteToDelete ->
                val deletedNoteEntity = noteToDelete.note
                viewModel.deleteNote(deletedNoteEntity)

                Snackbar.make(binding.root, "Anotação excluída", Snackbar.LENGTH_LONG)
                    .setAction("DESFAZER") {
                        viewModel.insertNoteWithTags(
                            title = deletedNoteEntity.title,
                            content = deletedNoteEntity.content,
                            selectedTagIds = noteToDelete.tags.map { it.id }
                        )
                    }
                    .setAnchorView(binding.fabAddNote)
                    .show()
            },
            onCreateTag = { tagName ->
                viewModel.createTag(tagName)
            }
        )
        addNoteDialog?.show(supportFragmentManager, "AddNoteDialog")
    }

    private fun setupSearchAndFilters() {
        // Dispara busca na ViewModel a cada caractere digitado (Tempo Real)
        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            viewModel.setSearchQuery(text?.toString() ?: "")
        }
    }

    private fun observeViewModel() {
        // Observa as notas filtradas (Grid reativo + Empty States)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredNotesWithTags.collectLatest { notes ->
                    noteAdapter.submitList(notes)
                    updateEmptyState(notes.isEmpty())
                }
            }
        }

        // Observa a combinação de [Tags Globais + Tag Selecionada] para o Carrossel
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.allTags,
                    viewModel.selectedFilterTagId
                ) { tags, selectedTagId ->
                    tags to selectedTagId
                }.collectLatest { (tags, selectedTagId) ->
                    renderFilterTags(binding.cgFilterTags, tags, selectedTagId)

                    // Se o Dialog de criação/edição estiver aberto, atualiza as tags dele também
                    if (addNoteDialog?.isAdded == true) {
                        addNoteDialog?.updateTags(tags)
                    }
                }
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.rvNotes.visibility = View.GONE
            binding.llEmptyState.visibility = View.VISIBLE

            val query = viewModel.searchQuery.value
            val selectedTagId = viewModel.selectedFilterTagId.value

            when {
                query.isNotBlank() -> {
                    binding.tvEmptyIcon.text = "🔍"
                    binding.tvEmptyTitle.text = "Nenhum resultado"
                    binding.tvEmptySubtitle.text = "Não encontramos notas para \"$query\""
                }
                selectedTagId != null -> {
                    binding.tvEmptyIcon.text = "🏷️"
                    binding.tvEmptyTitle.text = "Tag sem notas"
                    binding.tvEmptySubtitle.text = "Nenhuma anotação vinculada a esta tag"
                }
                else -> {
                    binding.tvEmptyIcon.text = "📜"
                    binding.tvEmptyTitle.text = "Sua lista está vazia"
                    binding.tvEmptySubtitle.text = "Toque no botão + para criar sua primeira nota"
                }
            }
        } else {
            binding.rvNotes.visibility = View.VISIBLE
            binding.llEmptyState.visibility = View.GONE
        }
    }

    private fun renderFilterTags(
        chipGroup: ChipGroup,
        tags: List<TagEntity>,
        selectedTagId: Long?
    ) {
        chipGroup.removeAllViews()

        // Chip "Todas"
        val allChip = Chip(this).apply {
            text = "Todas"
            isCheckable = true
            isChecked = (selectedTagId == null)
            setTextColor(getColor(R.color.odin_text_primary))
            setOnClickListener {
                viewModel.setFilterTagId(null)
            }
        }
        chipGroup.addView(allChip)

        // Chips para cada Tag da lista
        tags.forEach { tag ->
            val isSelected = (selectedTagId == tag.id)
            val chip = Chip(this).apply {
                text = tag.name
                isCheckable = true
                isChecked = isSelected

                setOnClickListener {
                    // Reatividade: Se clicar na tag que já está selecionada, limpa o filtro (toggle)
                    val newFilterId = if (isSelected) null else tag.id
                    viewModel.setFilterTagId(newFilterId)
                }
            }
            chipGroup.addView(chip)
        }
    }
}