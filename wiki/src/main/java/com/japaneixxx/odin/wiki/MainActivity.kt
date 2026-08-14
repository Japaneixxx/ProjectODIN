package com.japaneixxx.odin.wiki

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.japaneixxx.odin.data.database.OdinDatabase
import com.japaneixxx.odin.data.entity.PersonEntity
import com.japaneixxx.odin.wiki.databinding.ActivityWikiMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class SortMode(val label: String) {
    DEFAULT_ID("ID Numérico (#0001) [Padrão]"),
    NAME_OR_NICKNAME("Alfabética (Nome / Apelido)"),
    RELATIONSHIP("Alfabética por Vínculo / Tipo")
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWikiMainBinding
    private lateinit var adapter: PersonAdapter
    private val db by lazy { OdinDatabase.getInstance(this) }

    private var currentQuery = ""
    private var currentSortMode = SortMode.DEFAULT_ID
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = ContextCompat.getColor(this, R.color.odin_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.odin_background)

        binding = ActivityWikiMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearchListener()

        // Botão para trocar a ordenação
        binding.btnSort.setOnClickListener {
            showSortOptionsDialog()
        }

        // Carga inicial
        observePersons()

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
                currentQuery = s?.toString()?.trim() ?: ""
                observePersons()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun showSortOptionsDialog() {
        val options = SortMode.values().map { it.label }.toTypedArray()
        val checkedItem = SortMode.values().indexOf(currentSortMode)

        MaterialAlertDialogBuilder(this)
            .setTitle("MODO DE ORDENAÇÃO // DEX")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                currentSortMode = SortMode.values()[which]
                observePersons()
                dialog.dismiss()
                Toast.makeText(this, "Ordenado por: ${currentSortMode.label}", Toast.LENGTH_SHORT)
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun observePersons() {
        searchJob?.cancel()

        searchJob = lifecycleScope.launch {
            val flow = if (currentQuery.isEmpty()) {
                db.personDao().getAllPersons()
            } else {
                db.personDao().searchPersons(currentQuery)
            }

            flow.collectLatest { personsList ->
                // Aplica a ordenação escolhida em memória com alta performance
                val sortedList = when (currentSortMode) {
                    SortMode.DEFAULT_ID -> {
                        personsList.sortedBy { it.id }
                    }
                    SortMode.NAME_OR_NICKNAME -> {
                        // Se tiver apelido preenchido, usa ele; senão, usa o nome
                        personsList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                            it.nickname?.takeIf { nick -> nick.isNotBlank() } ?: it.name
                        })
                    }
                    SortMode.RELATIONSHIP -> {
                        // Ordena pelo Vínculo/Tipo A-Z; se vazio/nulo, fica no final
                        personsList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                            it.relationship?.takeIf { rel -> rel.isNotBlank() } ?: "ZZZZ"
                        })
                    }
                }

                adapter.submitList(sortedList)
                binding.tvTotalCount.text = String.format("TOTAL: %04d", sortedList.size)
            }
        }
    }

    private fun showAddPersonDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 10)
        }

        val etNickname = EditText(this).apply {
            hint = "Apelido / Como chama (ex: Alemão, Thor)"
            setHintTextColor(getColor(R.color.odin_text_secondary))
            setTextColor(getColor(R.color.odin_text_primary))
            // Capitaliza cada palavra
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        val etName = EditText(this).apply {
            hint = "Nome Completo (Opcional se tiver apelido)"
            setHintTextColor(getColor(R.color.odin_text_secondary))
            setTextColor(getColor(R.color.odin_text_primary))
            // Capitaliza cada palavra
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        val etRelationship = EditText(this).apply {
            hint = "Vínculo / Tipo (ex: Amigo, Trabalho, Família)"
            setHintTextColor(getColor(R.color.odin_text_secondary))
            setTextColor(getColor(R.color.odin_text_primary))
            // Capitaliza cada palavra
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        container.addView(etNickname)
        container.addView(etName)
        container.addView(etRelationship)

        MaterialAlertDialogBuilder(this)
            .setTitle("NOVA ENTRADA // DEX")
            .setView(container)
            .setPositiveButton("Salvar") { _, _ ->
                val nickname = etNickname.text.toString().trim()
                val name = etName.text.toString().trim()
                val relationship = etRelationship.text.toString().trim()

                // Validação: Precisa ter pelo menos UM dos dois (Nome ou Apelido)
                if (nickname.isNotEmpty() || name.isNotEmpty()) {
                    // Se o nome estiver vazio, usamos o próprio apelido como identificador base do registro
                    val finalName = if (name.isNotEmpty()) name else nickname
                    val finalNickname = nickname.ifEmpty { null }

                    savePerson(
                        name = finalName,
                        nickname = finalNickname,
                        relationship = relationship.ifEmpty { null }
                    )
                } else {
                    Toast.makeText(
                        this,
                        "Preencha ao menos o Apelido ou o Nome!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun savePerson(name: String, nickname: String?, relationship: String?) {
        lifecycleScope.launch {
            val person = PersonEntity(
                name = name,
                nickname = nickname,
                relationship = relationship
            )
            db.personDao().insertPerson(person)
        }
    }
}