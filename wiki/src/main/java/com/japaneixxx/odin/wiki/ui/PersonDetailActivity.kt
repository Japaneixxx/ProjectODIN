package com.japaneixxx.odin.wiki

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.japaneixxx.odin.data.database.OdinDatabase
import com.japaneixxx.odin.data.entity.BlockTemplateEntity
import com.japaneixxx.odin.data.entity.FieldType
import com.japaneixxx.odin.data.entity.PersonBlockEntity
import com.japaneixxx.odin.data.entity.PersonBlockFieldEntity
import com.japaneixxx.odin.data.entity.PersonEntity
import com.japaneixxx.odin.wiki.databinding.ActivityPersonDetailBinding
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class PersonDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonDetailBinding
    private lateinit var blockAdapter: PersonBlockAdapter
    private val db by lazy { OdinDatabase.getInstance(this) }

    private var personId: Long = -1L
    private var currentPerson: PersonEntity? = null
    private var selectedPhotoUri: Uri? = null

    // VARIÁVEL DE ESTADO: MODO DE EDIÇÃO
    private var isEditMode: Boolean = false

    private val updatedFieldsMap = mutableMapOf<Long, PersonBlockFieldEntity>()

    private val cropResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val croppedUri = UCrop.getOutput(result.data!!)
            if (croppedUri != null) {
                selectedPhotoUri = croppedUri
                displayPhoto(croppedUri)
            }
        }
    }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) startCrop(uri)
    }

    private val requestContactPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Permissão recusada. Os nomes não serão extraídos da agenda.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = ContextCompat.getColor(this, R.color.odin_background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.odin_background)

        binding = ActivityPersonDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        personId = intent.getLongExtra(EXTRA_PERSON_ID, -1L)

        setupToolbar()
        setupBlocksRecyclerView()
        loadPersonData()
        observeBlocks()

        binding.fabEditPhoto.setOnClickListener { pickMedia.launch("image/*") }
        binding.btnAddBlock.setOnClickListener { showAddBlockDialog() }
        binding.btnSave.setOnClickListener { saveChanges() }
        binding.btnDelete.setOnClickListener { confirmDelete() }

        // Inicializa a tela travada no Modo Leitura
        applyEditMode()
    }

    private fun startCrop(sourceUri: Uri) {
        val destinationFileName = "profile_${System.currentTimeMillis()}.jpg"
        val destinationUri = Uri.fromFile(File(cacheDir, destinationFileName))

        val options = UCrop.Options().apply {
            setCompressionQuality(90)
            setToolbarColor(ContextCompat.getColor(this@PersonDetailActivity, R.color.odin_background))
            setStatusBarColor(ContextCompat.getColor(this@PersonDetailActivity, R.color.odin_background))
            setActiveControlsWidgetColor(ContextCompat.getColor(this@PersonDetailActivity, R.color.odin_primary))
            setToolbarWidgetColor(ContextCompat.getColor(this@PersonDetailActivity, R.color.odin_text_primary))
            setToolbarTitle("AJUSTAR FOTO DE PERFIL")
            setFreeStyleCropEnabled(false)
        }

        val uCropIntent = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1000, 1000)
            .withOptions(options)
            .getIntent(this)

        cropResult.launch(uCropIntent)
    }

    private fun displayPhoto(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .centerCrop()
            .placeholder(android.R.drawable.sym_def_app_icon)
            .into(binding.ivProfilePhoto)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Adiciona o botão de Editar dinamicamente na Toolbar
        binding.toolbar.menu.add(0, 1, 0, "Editar").apply {
            setIcon(android.R.drawable.ic_menu_edit)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) {
                isEditMode = !isEditMode
                applyEditMode()
                true
            } else false
        }
    }

    // ALTERA A INTERFACE INTEIRA ENTRE LEITURA E EDIÇÃO
    private fun applyEditMode() {
        val visibility = if (isEditMode) View.VISIBLE else View.GONE

        // Esconde botões de ação
        binding.fabEditPhoto.visibility = visibility
        binding.btnAddBlock.visibility = visibility
        binding.btnSave.visibility = visibility
        binding.btnDelete.visibility = visibility

        // Trava os campos principais
        binding.etName.isEnabled = isEditMode
        binding.etNickname.isEnabled = isEditMode
        binding.etRelationship.isEnabled = isEditMode

        // Avisa o adapter dos blocos para travar os subcampos
        blockAdapter.isEditMode = isEditMode

        // Altera o ícone da Toolbar (Lápis vs X)
        val menuItem = binding.toolbar.menu.findItem(1)
        if (isEditMode) {
            menuItem?.setIcon(android.R.drawable.ic_menu_close_clear_cancel) // Ícone de Fechar
        } else {
            menuItem?.setIcon(android.R.drawable.ic_menu_edit) // Ícone de Lápis
        }
        if (isEditMode) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestContactPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun setupBlocksRecyclerView() {
        blockAdapter = PersonBlockAdapter(
            db = db,
            onAddFieldClick = { block -> showAddSubfieldDialog(block) },
            onDeleteBlockClick = { block ->
                lifecycleScope.launch { db.personBlockDao().deleteBlock(block) }
            },
            onFieldUpdated = { updatedField ->
                updatedFieldsMap[updatedField.id] = updatedField
            },
            getDraftField = { fieldId ->
                // Envia o rascunho digitado para o Adapter impedir que a tela se apague sozinha
                updatedFieldsMap[fieldId]
            },
            onDeleteFieldClick = { field ->
                lifecycleScope.launch {
                    db.personBlockDao().deleteField(field)
                    updatedFieldsMap.remove(field.id)
                }
            }
        )
        binding.rvBlocks.layoutManager = LinearLayoutManager(this)
        binding.rvBlocks.adapter = blockAdapter
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
                binding.tvIndexNumber.text = String.format("#%04d", person.id)
                binding.etName.setText(person.name)
                binding.etNickname.setText(person.nickname ?: "")
                binding.etRelationship.setText(person.relationship ?: "")
                binding.toolbar.title = "REGISTRO #${String.format("%04d", person.id)}"

                if (!person.photoPath.isNullOrEmpty()) {
                    displayPhoto(Uri.parse(person.photoPath))
                } else {
                    Glide.with(this@PersonDetailActivity)
                        .load(android.R.drawable.sym_def_app_icon)
                        .centerCrop()
                        .into(binding.ivProfilePhoto)
                }
            } ?: run { finish() }
        }
    }

    private fun observeBlocks() {
        lifecycleScope.launch {
            db.personBlockDao().getBlocksForPerson(personId).collectLatest { blockList ->
                blockAdapter.submitList(blockList)
            }
        }
    }

    private fun showAddBlockDialog() {
        lifecycleScope.launch {
            val templates = db.blockTemplateDao().getAllTemplatesSync()
            val options = mutableListOf<String>()
            options.add("➕ Criar Novo Bloco Global...")
            options.addAll(templates.map { "📋 ${it.title}" })

            MaterialAlertDialogBuilder(this@PersonDetailActivity)
                .setTitle("ADICIONAR MÓDULO // DEX")
                .setItems(options.toTypedArray()) { _, which ->
                    if (which == 0) showCreateNewTemplateDialog()
                    else addBlockToPerson(templates[which - 1].title)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun showCreateNewTemplateDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 10)
        }

        val etTitle = EditText(this).apply {
            hint = "Ex: Datas Importantes, Tamanhos, Redes"
            setHintTextColor(getColor(R.color.odin_text_secondary))
            setTextColor(getColor(R.color.odin_text_primary))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        container.addView(etTitle)

        MaterialAlertDialogBuilder(this)
            .setTitle("Novo Tipo Global de Bloco")
            .setView(container)
            .setPositiveButton("Salvar & Usar") { _, _ ->
                val title = etTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    lifecycleScope.launch {
                        db.blockTemplateDao().insertTemplate(BlockTemplateEntity(title = title))
                        addBlockToPerson(title)
                    }
                }
            }
            .setNegativeButton("Voltar") { _, _ -> showAddBlockDialog() }
            .show()
    }

    private fun addBlockToPerson(title: String) {
        lifecycleScope.launch {
            val newBlock = PersonBlockEntity(personId = personId, title = title, content = "")
            db.personBlockDao().insertBlock(newBlock)
        }
    }

    private fun showAddSubfieldDialog(block: PersonBlockEntity) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }

        val etLabel = EditText(this).apply {
            hint = "Nome do Subcampo"
            setHintTextColor(getColor(R.color.odin_text_secondary))
            setTextColor(getColor(R.color.odin_text_primary))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        val tvTypeLabel = TextView(this).apply {
            text = "TIPO DE CAMPO / AÇÃO:"
            textSize = 11F
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(getColor(R.color.odin_primary))
            setPadding(0, 30, 0, 10)
        }

        val typeOptions = listOf(
            "📝 Texto Livre (Padrão)",
            "📅 Data (Calendário)",
            "🔢 Número",
            "💬 WhatsApp (Chat Rápido)",
            "📍 Endereço (Maps / Waze)",
            "💸 Chave Pix (Copiar)",
            "📞 Telefone (Discar)",
            "🌐 Link / Site / Instagram"
        )

        // Mapeamento dos nomes de rótulo automáticos
        val defaultLabels = mapOf(
            0 to "Info",
            1 to "Data",
            2 to "Número",
            3 to "WhatsApp",
            4 to "Endereço",
            5 to "Pix",
            6 to "Telefone",
            7 to "Link / Rede"
        )

        val spinnerType = Spinner(this).apply {
            val spinnerAdapter = ArrayAdapter(
                this@PersonDetailActivity,
                android.R.layout.simple_spinner_item,
                typeOptions
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            adapter = spinnerAdapter

            // AUTOMAÇÃO: Mudar o rótulo sozinho ao selecionar a opção no Spinner
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val currentText = etLabel.text.toString().trim()
                    // Se o campo estiver vazio ou tiver um rótulo padrão anterior, atualiza para o novo
                    if (currentText.isEmpty() || defaultLabels.values.contains(currentText)) {
                        etLabel.setText(defaultLabels[position] ?: "")
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        container.addView(etLabel)
        container.addView(tvTypeLabel)
        container.addView(spinnerType)

        MaterialAlertDialogBuilder(this)
            .setTitle("Novo Subcampo em '${block.title}'")
            .setView(container)
            .setPositiveButton("Adicionar") { _, _ ->
                val label = etLabel.text.toString().trim()
                val selectedType = when (spinnerType.selectedItemPosition) {
                    1 -> FieldType.DATE
                    2 -> FieldType.NUMBER
                    3 -> FieldType.WHATSAPP
                    4 -> FieldType.MAPS
                    5 -> FieldType.PIX
                    6 -> FieldType.PHONE
                    7 -> FieldType.LINK
                    else -> FieldType.TEXT
                }

                if (label.isNotEmpty()) {
                    lifecycleScope.launch {
                        val newField = PersonBlockFieldEntity(
                            blockId = block.id,
                            label = label,
                            value = "",
                            actionData = null,
                            fieldType = selectedType
                        )
                        db.personBlockDao().insertField(newField)
                    }
                } else {
                    Toast.makeText(this, "O nome do subcampo não pode ser vazio", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveChanges() {
        val name = binding.etName.text.toString().trim()
        val nickname = binding.etNickname.text.toString().trim()
        val relationship = binding.etRelationship.text.toString().trim()

        if (name.isEmpty() && nickname.isEmpty()) {
            binding.etName.error = "Preencha ao menos o Nome ou Apelido"
            binding.etNickname.error = "Preencha ao menos o Nome ou Apelido"
            return
        }

        currentPerson?.let { person ->
            val finalPhotoPath = selectedPhotoUri?.toString() ?: person.photoPath
            val finalName = if (name.isNotEmpty()) name else nickname
            val finalNickname = nickname.ifEmpty { null }

            val updatedPerson = person.copy(
                name = finalName,
                nickname = finalNickname,
                relationship = relationship.ifEmpty { null },
                photoPath = finalPhotoPath
            )

            lifecycleScope.launch {
                db.personDao().updatePerson(updatedPerson)

                updatedFieldsMap.values.forEach { field ->
                    db.personBlockDao().updateField(field)
                }

                Toast.makeText(this@PersonDetailActivity, "Registro salvo com sucesso!", Toast.LENGTH_SHORT).show()
                finish() // Ao salvar, sai da tela
            }
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Excluir Perfil")
            .setMessage("Tem certeza que deseja excluir este perfil e todos os seus blocos?")
            .setPositiveButton("Excluir") { _, _ ->
                currentPerson?.let { person ->
                    lifecycleScope.launch {
                        db.personDao().deletePerson(person)
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