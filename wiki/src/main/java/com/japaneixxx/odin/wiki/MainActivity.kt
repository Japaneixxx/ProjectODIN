package com.japaneixxx.odin.wiki

import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.japaneixxx.odin.data.database.OdinDatabase
import com.japaneixxx.odin.data.entity.FieldType
import com.japaneixxx.odin.data.entity.PersonBlockEntity
import com.japaneixxx.odin.data.entity.PersonBlockFieldEntity
import com.japaneixxx.odin.data.entity.PersonEntity
import com.japaneixxx.odin.wiki.databinding.ActivityWikiMainBinding
import com.japaneixxx.odin.wiki.util.AutoLinkParser
import com.japaneixxx.odin.wiki.util.ContactImportHelper
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

    private val contactPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val contactUri = result.data!!.data ?: return@registerForActivityResult
            importContactData(contactUri)
        }
    }

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
            val options = arrayOf("Adicionar Manualmente", "Importar da Agenda (Rápido)")

            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Novo Perfil")
                .setItems(options) { _, which ->
                    if (which == 0) {
                        // Seu código antigo de criar pessoa vazia
                    } else {
                        // ABRE A AGENDA DO ANDROID!
                        val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                        contactPickerLauncher.launch(intent)
                    }
                }
                .show()
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
    private fun importContactData(contactUri: android.net.Uri) {
        lifecycleScope.launch {
            // 1. Extrai tudo da agenda
            val imported = ContactImportHelper.importFromUri(this@MainActivity, contactUri) ?: return@launch

            // 2. Cria o Perfil da Pessoa
            val newPerson = PersonEntity(
                name = imported.name,
                nickname = null,
                relationship = null,
                photoPath = null
            )
            // Insere a pessoa e pega o ID gerado
            val personId = db.personDao().insertPerson(newPerson)

            // 3. Se tiver algum dado extra (telefone, email, etc), cria um bloco base
            val hasExtraData = imported.phones.isNotEmpty() || imported.emails.isNotEmpty() ||
                    imported.addresses.isNotEmpty() || imported.websites.isNotEmpty() ||
                    imported.birthday != null

            if (hasExtraData) {
                val newBlock = PersonBlockEntity(
                    personId = personId,
                    title = "Informações Importadas",
                    content = ""
                )
                // Insere o bloco e pega o ID gerado
                val blockId = db.personBlockDao().insertBlock(newBlock)

                // 4. Cria os Subcampos para cada dado encontrado
                val fieldsToInsert = mutableListOf<PersonBlockFieldEntity>()

                // Adiciona WhatsApp para o primeiro número (e telefone para os demais)
                imported.phones.forEachIndexed { index, phone ->
                    val fieldType = if (index == 0) FieldType.WHATSAPP else FieldType.PHONE
                    val label = if (index == 0) "WhatsApp" else "Telefone ${index + 1}"

                    // Se for o WhatsApp, tentamos logo buscar o nome do contato na agenda para ficar bonito!
                    val displayValue = if (index == 0) {
                        AutoLinkParser.extractDisplayValue(this@MainActivity, phone, FieldType.WHATSAPP) ?: phone
                    } else {
                        AutoLinkParser.extractDisplayValue(this@MainActivity, phone, FieldType.PHONE) ?: phone
                    }

                    fieldsToInsert.add(
                        PersonBlockFieldEntity(
                            blockId = blockId,
                            label = label,
                            value = displayValue, // Nome do contato ou número formatado
                            actionData = phone,    // Número puro guardado para as Intents funcionarem
                            fieldType = fieldType
                        )
                    )
                }

                imported.emails.forEach { email ->
                    fieldsToInsert.add(
                        PersonBlockFieldEntity(
                            blockId = blockId,
                            label = "E-mail",
                            value = email,
                            actionData = email,
                            fieldType = FieldType.TEXT
                        )
                    )
                }

                imported.addresses.forEach { address ->
                    fieldsToInsert.add(
                        PersonBlockFieldEntity(
                            blockId = blockId,
                            label = "Endereço",
                            value = "", // O AutoLink vai encurtar
                            actionData = address,
                            fieldType = FieldType.MAPS
                        )
                    )
                }

                imported.websites.forEach { site ->
                    fieldsToInsert.add(
                        PersonBlockFieldEntity(
                            blockId = blockId,
                            label = "Site / Link",
                            value = "",
                            actionData = site,
                            fieldType = FieldType.LINK
                        )
                    )
                }

                imported.birthday?.let { bday ->
                    fieldsToInsert.add(
                        PersonBlockFieldEntity(
                            blockId = blockId,
                            label = "Aniversário",
                            value = bday,
                            actionData = null,
                            fieldType = FieldType.DATE
                        )
                    )
                }

                // Salva todos os campos de uma vez no banco de dados!
                fieldsToInsert.forEach { field ->
                    db.personBlockDao().insertField(field)
                }
            }

            // 5. Redireciona o usuário direto para a tela de detalhes dessa pessoa recém-criada
            Toast.makeText(this@MainActivity, "Contato ${imported.name} importado com sucesso!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this@MainActivity, PersonDetailActivity::class.java).apply {
                putExtra(PersonDetailActivity.EXTRA_PERSON_ID, personId)
            }
            startActivity(intent)
        }
    }
}