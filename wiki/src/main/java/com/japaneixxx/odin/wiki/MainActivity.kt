package com.japaneixxx.odin.wiki

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.japaneixxx.odin.data.database.OdinDatabase
import com.japaneixxx.odin.data.entity.PersonEntity
import com.japaneixxx.odin.wiki.databinding.ActivityWikiMainBinding
import com.japaneixxx.odin.wiki.ui.PersonDetailActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWikiMainBinding
    private lateinit var adapter: PersonAdapter
    private val db by lazy { OdinDatabase.getInstance(this) }

    // Job para controlar e cancelar corrotinas de busca anteriores enquanto digita
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWikiMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearchListener()

        // Carga inicial (sem filtro)
        observePersons("")

        binding.fabAddPerson.setOnClickListener {
            showAddPersonDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = PersonAdapter { person ->
            val intent = Intent(this, PersonDetailActivity::class.java).apply {
                putExtra(PersonDetailActivity.EXTRA_PERSON_ID, person.id)
            }
            startActivity(intent)
        }
        binding.recyclerViewPersons.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPersons.adapter = adapter
    }

    private fun setupSearchListener() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                observePersons(query)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observePersons(query: String) {
        searchJob?.cancel()

        searchJob = lifecycleScope.launch {
            val flow = if (query.isEmpty()) {
                db.personDao().getAllPersons()
            } else {
                db.personDao().searchPersons(query)
            }

            flow.collectLatest { personsList ->
                adapter.submitList(personsList)
                // Atualiza o contador Pokédex no topo (ex: TOTAL: 0004)
                binding.tvTotalCount.text = String.format("TOTAL: %04d", personsList.size)
            }
        }
    }

    private fun showAddPersonDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val etName = EditText(this).apply {
            hint = "Nome (ex: João Silva)"
            setHintTextColor(getColor(R.color.odin_text_secondary))
            setTextColor(getColor(R.color.odin_text_primary))
        }
        val etRelationship = EditText(this).apply {
            hint = "Vínculo (ex: Amigo, Faculdade, Trabalho)"
            setHintTextColor(getColor(R.color.odin_text_secondary))
            setTextColor(getColor(R.color.odin_text_primary))
        }

        container.addView(etName)
        container.addView(etRelationship)

        MaterialAlertDialogBuilder(this)
            .setTitle("Novo Perfil Wiki")
            .setView(container)
            .setPositiveButton("Salvar") { _, _ ->
                val name = etName.text.toString().trim()
                val relationship = etRelationship.text.toString().trim()

                if (name.isNotEmpty()) {
                    savePerson(name, relationship.ifEmpty { null })
                } else {
                    Toast.makeText(this, "O nome não pode ser vazio!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun savePerson(name: String, relationship: String?) {
        lifecycleScope.launch {
            val person = PersonEntity(
                name = name,
                relationship = relationship
            )
            db.personDao().insertPerson(person)
        }
    }
}