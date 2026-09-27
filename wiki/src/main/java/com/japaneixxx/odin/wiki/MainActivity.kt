package com.japaneixxx.odin.wiki

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.japaneixxx.odin.data.entity.FieldType
import com.japaneixxx.odin.data.entity.PersonBlockEntity
import com.japaneixxx.odin.data.entity.PersonBlockFieldEntity
import com.japaneixxx.odin.data.entity.PersonEntity
import com.japaneixxx.odin.data.provider.OdinDataClient
import com.japaneixxx.odin.wiki.databinding.ActivityWikiMainBinding
import com.japaneixxx.odin.wiki.util.AutoLinkParser
import com.japaneixxx.odin.wiki.util.ContactImportHelper
import com.japaneixxx.odin.wiki.util.ImportedContact
import com.japaneixxx.odin.wiki.util.PersonDuplicateMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortMode(val label: String) {
    DEFAULT_ID("ID Numérico (#0001) [Padrão]"),
    NAME_OR_NICKNAME("Alfabética (Nome / Apelido)"),
    RELATIONSHIP("Alfabética por Vínculo / Tipo")
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWikiMainBinding
    private val PERSONS_PROVIDER_URI = Uri.parse("content://com.japaneixxx.odin.core.provider/persons")
    private lateinit var adapter: PersonAdapter
    private val dataClient by lazy { OdinDataClient(contentResolver) }

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

            MaterialAlertDialogBuilder(this)
                .setTitle("Novo Perfil")
                .setItems(options) { _, which ->
                    if (which == 0) {
                        showAddPersonDialog() // Abre o modal manual
                    } else {
                        checkAndOpenContactPicker() // Verifica a permissão e abre a agenda
                    }
                }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            observePersons()
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
                Toast.makeText(this, "Ordenado por: ${currentSortMode.label}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun observePersons() {
        searchJob?.cancel()

        searchJob = lifecycleScope.launch {
            // Busca os dados diretamente do O.D.I.N. Core via ContentResolver em background
            val personsList = withContext(Dispatchers.IO) {
                fetchPersonsFromCore(currentQuery)
            }

            val sortedList = when (currentSortMode) {
                SortMode.DEFAULT_ID -> {
                    personsList.sortedBy { it.id }
                }
                SortMode.NAME_OR_NICKNAME -> {
                    personsList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                        it.nickname?.takeIf { nick -> nick.isNotBlank() } ?: it.name
                    })
                }
                SortMode.RELATIONSHIP -> {
                    personsList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                        it.relationship?.takeIf { rel -> rel.isNotBlank() } ?: "ZZZZ"
                    })
                }
            }

            adapter.submitList(sortedList)
            binding.tvTotalCount.text = String.format("TOTAL: %04d", sortedList.size)
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
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        val etName = EditText(this).apply {
            hint = "Nome Completo (Opcional se tiver apelido)"
            setHintTextColor(getColor(R.color.odin_text_secondary))
            setTextColor(getColor(R.color.odin_text_primary))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        val etRelationship = EditText(this).apply {
            hint = "Vínculo / Tipo (ex: Amigo, Trabalho, Família)"
            setHintTextColor(getColor(R.color.odin_text_secondary))
            setTextColor(getColor(R.color.odin_text_primary))
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

                if (nickname.isNotEmpty() || name.isNotEmpty()) {
                    val finalName = if (name.isNotEmpty()) name else nickname
                    val finalNickname = nickname.ifEmpty { null }

                    savePerson(
                        name = finalName,
                        nickname = finalNickname,
                        relationship = relationship.ifEmpty { null }
                    )
                } else {
                    Toast.makeText(this, "Preencha ao menos o Apelido ou o Nome!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun savePersonToCore(name: String, nickname: String?, relationship: String?): Long {
        val values = ContentValues().apply {
            put("name", name)
            put("nickname", nickname)
            put("relationship", relationship)
            put("createdAt", System.currentTimeMillis())
            put("updatedAt", System.currentTimeMillis())
        }
        val insertedUri = contentResolver.insert(PERSONS_PROVIDER_URI, values)
        return insertedUri?.let { ContentUris.parseId(it) } ?: -1L
    }

    private fun savePerson(name: String, nickname: String?, relationship: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            savePersonToCore(name, nickname, relationship)
            observePersons() // Atualiza a lista na tela
        }
    }

    private fun importContactData(contactUri: android.net.Uri) {
        lifecycleScope.launch {
            val imported = ContactImportHelper.importFromUri(this@MainActivity, contactUri) ?: return@launch

            val matchingPerson = withContext(Dispatchers.IO) {
                PersonDuplicateMatcher.findMatch(fetchPersonsFromCore(imported.name), imported.name)
            }

            if (matchingPerson != null) {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Perfil já existente")
                    .setMessage("Já existe um perfil chamado ${matchingPerson.name}. Deseja combinar os dados da agenda com ele?")
                    .setNegativeButton("Criar novo") { _, _ ->
                        continueContactImport(imported, null)
                    }
                    .setPositiveButton("Combinar") { _, _ ->
                        continueContactImport(imported, matchingPerson.id)
                    }
                    .show()
            } else {
                continueContactImport(imported, null)
            }
        }
    }

    private fun continueContactImport(imported: ImportedContact, existingPersonId: Long?) {
        lifecycleScope.launch {
            val personId = existingPersonId ?: withContext(Dispatchers.IO) {
                savePersonToCore(
                    name = imported.name,
                    nickname = null,
                    relationship = null
                )
            }

            if (personId == -1L) {
                Toast.makeText(this@MainActivity, "Erro ao salvar perfil no Core", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val hasExtraData = imported.phones.isNotEmpty() || imported.emails.isNotEmpty() ||
                    imported.addresses.isNotEmpty() || imported.websites.isNotEmpty() ||
                    imported.birthday != null

            if (hasExtraData) {
                val blockId = dataClient.insertBlock(
                    PersonBlockEntity(
                        personId = personId,
                        title = "Informações Importadas",
                        content = ""
                    )
                )

                val fieldsToInsert = mutableListOf<PersonBlockFieldEntity>()

                imported.phones.forEachIndexed { index, phone ->
                    val fieldType = if (index == 0) FieldType.WHATSAPP else FieldType.PHONE
                    val label = if (index == 0) "WhatsApp" else "Telefone ${index + 1}"
                    val displayValue = AutoLinkParser.extractDisplayValue(
                        this@MainActivity,
                        phone,
                        fieldType
                    ) ?: phone

                    fieldsToInsert.add(
                        PersonBlockFieldEntity(
                            blockId = blockId,
                            label = label,
                            value = displayValue,
                            actionData = phone,
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
                            value = "",
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

                fieldsToInsert.forEach { field ->
                    dataClient.insertField(field)
                }
            }

            observePersons()
            Toast.makeText(this@MainActivity, "Contato ${imported.name} importado com sucesso!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this@MainActivity, PersonDetailActivity::class.java).apply {
                putExtra(PersonDetailActivity.EXTRA_PERSON_ID, personId)
            }
            startActivity(intent)
        }
    }

    // -----------------------------------------------------------------------------------------
    // BLOCO DE PERMISSÕES E ABERTURA DA AGENDA NATIVA
    // -----------------------------------------------------------------------------------------

    private fun checkAndOpenContactPicker() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            // Já tem permissão, abre o seletor normalmente
            openContactPicker()
        } else {
            // Solicita a permissão ao utilizador
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                1001
            )
        }
    }


    private fun fetchPersonsFromCore(query: String): List<PersonEntity> {
        val personList = mutableListOf<PersonEntity>()
        val cursor = contentResolver.query(
            PERSONS_PROVIDER_URI,
            null,
            null,
            arrayOf(query),
            null
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow("id")
            val nameCol = c.getColumnIndexOrThrow("name")
            val nickCol = c.getColumnIndexOrThrow("nickname")
            val relCol = c.getColumnIndexOrThrow("relationship")
            val photoCol = c.getColumnIndexOrThrow("photoPath")

            while (c.moveToNext()) {
                personList.add(
                    PersonEntity(
                        id = c.getLong(idCol),
                        name = c.getString(nameCol) ?: "",
                        nickname = c.getString(nickCol),
                        relationship = c.getString(relCol),
                        photoPath = c.getString(photoCol)
                    )
                )
            }
        }
        return personList
    }

    private fun openContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openContactPicker()
        } else {
            Toast.makeText(this, "Permissão de contatos negada", Toast.LENGTH_SHORT).show()
        }
    }
}