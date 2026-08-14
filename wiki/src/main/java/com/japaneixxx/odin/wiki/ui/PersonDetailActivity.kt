package com.japaneixxx.odin.wiki.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.japaneixxx.odin.data.database.OdinDatabase
import com.japaneixxx.odin.data.entity.PersonEntity
import com.japaneixxx.odin.wiki.databinding.ActivityPersonDetailBinding
import kotlinx.coroutines.launch

class PersonDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonDetailBinding
    private val db by lazy { OdinDatabase.getInstance(this) }
    private var personId: Long = -1L
    private var currentPerson: PersonEntity? = null

    // Variável para guardar temporariamente a nova URI da foto selecionada
    private var selectedPhotoUri: Uri? = null

    // Novo mecanismo do Android para selecionar mídia (Photo Picker)
    private val pickMedia = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedPhotoUri = uri

            // Exibe a foto selecionada temporariamente usando o Glide
            Glide.with(this)
                .load(uri)
                .circleCrop()
                .into(binding.ivProfilePhoto)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        personId = intent.getLongExtra(EXTRA_PERSON_ID, -1L)

        setupToolbar()
        loadPersonData()

        // Botão para alterar a foto
        binding.fabEditPhoto.setOnClickListener {
            // Filtra para abrir apenas arquivos de imagem
            pickMedia.launch("image/*")
        }

        binding.btnSave.setOnClickListener { saveChanges() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadPersonData() {
        if (personId == -1L) {
            Toast.makeText(this, "Perfil não encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            currentPerson = db.personDao().getPersonById(personId)
            currentPerson?.let { person ->
                binding.tvIndexNumber.text = String.format("#%04d", person.id) // Ex: #0001, #0012
                binding.etName.setText(person.name)
                binding.etNickname.setText(person.nickname ?: "")
                binding.etRelationship.setText(person.relationship ?: "")
                binding.etNotes.setText(person.notes ?: "")
                binding.toolbar.title = "REGISTRO #${String.format("%04d", person.id)}"

                // Carrega a foto de perfil existente usando Glide
                if (!person.photoPath.isNullOrEmpty()) {
                    Glide.with(this@PersonDetailActivity)
                        .load(Uri.parse(person.photoPath))
                        .circleCrop()
                        .placeholder(android.R.drawable.sym_def_app_icon) // Imagem temporária enquanto carrega
                        .error(android.R.drawable.sym_def_app_icon)       // Imagem se der erro
                        .into(binding.ivProfilePhoto)
                } else {
                    // Se não tiver foto, carrega o ícone padrão
                    Glide.with(this@PersonDetailActivity)
                        .load(android.R.drawable.sym_def_app_icon)
                        .circleCrop()
                        .into(binding.ivProfilePhoto)
                }

            } ?: run {
                Toast.makeText(this@PersonDetailActivity, "Erro ao carregar dados", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun saveChanges() {
        val name = binding.etName.text.toString().trim()
        val nickname = binding.etNickname.text.toString().trim()
        val relationship = binding.etRelationship.text.toString().trim()
        val notes = binding.etNotes.text.toString().trim()

        if (name.isEmpty()) {
            binding.etName.error = "O nome é obrigatório"
            return
        }

        currentPerson?.let { person ->
            // Define o caminho da foto: usa a nova se selecionada, senão mantém a antiga
            val finalPhotoPath = selectedPhotoUri?.toString() ?: person.photoPath

            val updatedPerson = person.copy(
                name = name,
                nickname = nickname.ifEmpty { null },
                relationship = relationship.ifEmpty { null },
                notes = notes.ifEmpty { null },
                photoPath = finalPhotoPath // Salva a String da URI no banco
            )

            lifecycleScope.launch {
                db.personDao().updatePerson(updatedPerson)
                Toast.makeText(this@PersonDetailActivity, "Perfil atualizado!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Excluir Perfil")
            .setMessage("Tem certeza que deseja excluir o perfil de ${currentPerson?.name}?")
            .setPositiveButton("Excluir") { _, _ ->
                currentPerson?.let { person ->
                    lifecycleScope.launch {
                        db.personDao().deletePerson(person)
                        Toast.makeText(this@PersonDetailActivity, "Perfil excluído", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    companion object {
        const val EXTRA_PERSON_ID = "extra_person_id"
    }
}