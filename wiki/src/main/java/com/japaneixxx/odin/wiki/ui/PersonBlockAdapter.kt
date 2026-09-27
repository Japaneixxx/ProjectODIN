package com.japaneixxx.odin.wiki

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.japaneixxx.odin.data.entity.FieldType
import com.japaneixxx.odin.data.entity.PersonBlockEntity
import com.japaneixxx.odin.data.entity.PersonBlockFieldEntity
import com.japaneixxx.odin.data.provider.OdinDataClient
import com.japaneixxx.odin.wiki.databinding.ItemBlockFieldBinding
import com.japaneixxx.odin.wiki.databinding.ItemPersonBlockBinding
import com.japaneixxx.odin.wiki.util.AutoLinkParser
import com.japaneixxx.odin.wiki.util.PixPayloadGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class PersonBlockAdapter(
    private val dataClient: OdinDataClient,
    private val onAddFieldClick: (PersonBlockEntity) -> Unit,
    private val onDeleteBlockClick: (PersonBlockEntity) -> Unit,
    private val onFieldUpdated: (PersonBlockFieldEntity) -> Unit,
    private val getDraftField: (Long) -> PersonBlockFieldEntity?, // <-- RECUPERADO: Pega o rascunho
    private val onDeleteFieldClick: (PersonBlockFieldEntity) -> Unit
) : ListAdapter<PersonBlockEntity, PersonBlockAdapter.BlockViewHolder>(BlockDiffCallback()) {

    // Controle do Modo de Edição
    var isEditMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged() // Força a re-renderização quando o modo muda
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockViewHolder {
        val binding = ItemPersonBlockBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BlockViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BlockViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BlockViewHolder(
        private val binding: ItemPersonBlockBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var fieldsJob: Job? = null

        fun bind(block: PersonBlockEntity) {
            binding.tvBlockTitle.text = block.title.uppercase()

            // Alterna visibilidade dos botões do bloco baseados no Modo de Edição
            binding.btnDeleteBlock.visibility = if (isEditMode) View.VISIBLE else View.GONE
            binding.btnAddField.visibility = if (isEditMode) View.VISIBLE else View.GONE

            binding.btnDeleteBlock.setOnClickListener { onDeleteBlockClick(block) }
            binding.btnAddField.setOnClickListener { onAddFieldClick(block) }

            val activity = binding.root.context as? AppCompatActivity ?: return

            // Cancela o job anterior para evitar vazamento de memória ao recarregar a lista
            fieldsJob?.cancel()

            // 🌟 Roda em Background (Dispatchers.IO) para não travar a tela ao varrer a agenda
            fieldsJob = activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                dataClient.observeFields(block.id).collectLatest { dbFields ->

                    var hasUpdates = false
                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                        activity, android.Manifest.permission.READ_CONTACTS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                    // MÁGICA: Sincronização Silenciosa da Agenda (Apenas no Modo Leitura)
                    if (hasPermission && !isEditMode) {
                        for (field in dbFields) {
                            if (field.fieldType == FieldType.WHATSAPP || field.fieldType == FieldType.PHONE) {
                                // Pega o número real gravado no banco
                                val targetData = field.actionData?.takeIf { it.isNotBlank() } ?: field.value

                                // Vai na agenda do Android ver qual é o nome do contato HOJE
                                val freshName = com.japaneixxx.odin.wiki.util.AutoLinkParser.extractDisplayValue(activity, targetData, field.fieldType)

                                // Se achou um nome novo que está diferente do gravado, atualiza o banco sozinho!
                                if (freshName != null && freshName != field.value) {
                                    dataClient.updateField(field.copy(value = freshName))
                                    hasUpdates = true
                                }
                            }
                        }
                    }

                    // Se ele achou nomes novos e atualizou o banco, o "collectLatest" do Room
                    // vai disparar de novo automaticamente! Por isso, só desenhamos a tela
                    // se NÃO houve atualizações pendentes neste ciclo.
                    if (!hasUpdates) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            renderFields(dbFields)
                        }
                    }
                }
            }
        }

        private fun renderFields(dbFields: List<PersonBlockFieldEntity>) {
            binding.llFieldsContainer.removeAllViews()
            val inflater = LayoutInflater.from(binding.root.context)

            for (dbField in dbFields) {
                // Recupera o rascunho (se houver) para a tela não apagar o que está sendo digitado
                val field = getDraftField(dbField.id) ?: dbField

                val fieldBinding = ItemBlockFieldBinding.inflate(inflater, binding.llFieldsContainer, false)
                fieldBinding.tilField.hint = field.label

                fieldBinding.etFieldValue.isEnabled = isEditMode
                fieldBinding.btnDeleteField.visibility = if (isEditMode) View.VISIBLE else View.GONE

                // Por padrão, esconde campos extras
                fieldBinding.tilFieldUri.visibility = View.GONE
                fieldBinding.btnOpenLink.visibility = View.GONE

                // 🌟 NOVIDADE: COPIAR TEXTO COM 1 CLIQUE NO MODO LEITURA
                if (!isEditMode) {
                    fieldBinding.root.setOnClickListener {
                        val textToCopy = field.value
                        if (textToCopy.isNotBlank()) {
                            val context = it.context
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText(field.label, textToCopy)
                            clipboard.setPrimaryClip(clip)

                            // Aviso visual que o texto foi copiado
                            android.widget.Toast.makeText(context, "${field.label} copiado!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // Remove a ação de cópia se estivermos editando (para não atrapalhar o teclado)
                    fieldBinding.root.setOnClickListener(null)
                    fieldBinding.root.isClickable = false
                }

                when (field.fieldType) {
                    FieldType.DATE -> {
                        fieldBinding.etFieldValue.isFocusable = false
                        fieldBinding.etFieldValue.isClickable = isEditMode
                        fieldBinding.etFieldValue.setText(field.value)
                        fieldBinding.etFieldValue.setOnClickListener {
                            if (isEditMode) showDatePicker(fieldBinding, field)
                        }
                    }
                    FieldType.NUMBER, FieldType.TEXT -> {
                        val inputFlag = if (field.fieldType == FieldType.NUMBER) InputType.TYPE_CLASS_NUMBER
                        else (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
                        fieldBinding.etFieldValue.inputType = inputFlag
                        fieldBinding.etFieldValue.setText(field.value)
                        setupTextWatcher(fieldBinding, field)
                    }
                    FieldType.LINK, FieldType.WHATSAPP, FieldType.MAPS, FieldType.PHONE, FieldType.PIX -> {
                        fieldBinding.etFieldValue.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                        fieldBinding.etFieldValue.setText(field.value)

                        if (isEditMode) {
                            // MODO EDIÇÃO: Mostra o campo extra para colar a URL/Número
                            fieldBinding.tilFieldUri.visibility = View.VISIBLE
                            fieldBinding.etFieldUri.isEnabled = true
                            fieldBinding.etFieldUri.setText(field.actionData ?: "")
                        } else {
                            // MODO LEITURA: Mostra o botão de Ação Interativa
                            val data = field.actionData
                            if (!data.isNullOrBlank()) {
                                fieldBinding.btnOpenLink.visibility = View.VISIBLE

                                when (field.fieldType) {
                                    FieldType.WHATSAPP -> fieldBinding.btnOpenLink.setImageResource(android.R.drawable.ic_menu_call)
                                    FieldType.MAPS -> fieldBinding.btnOpenLink.setImageResource(android.R.drawable.ic_menu_mapmode)
                                    FieldType.PHONE -> fieldBinding.btnOpenLink.setImageResource(android.R.drawable.ic_menu_call)
                                    FieldType.PIX -> fieldBinding.btnOpenLink.setImageResource(android.R.drawable.ic_menu_agenda)
                                    FieldType.LINK -> fieldBinding.btnOpenLink.setImageResource(android.R.drawable.ic_menu_compass)
                                    else -> fieldBinding.btnOpenLink.setImageResource(android.R.drawable.ic_menu_send)
                                }

                                // Ação Primária Inteligente (Abrir Maps/Banco/Zap) ao clicar no ÍCONE
                                fieldBinding.btnOpenLink.setOnClickListener {
                                    handleFieldAction(it, field)
                                }
                            }
                        }
                        setupTextWatcher(fieldBinding, field)
                    }
                }

                fieldBinding.btnDeleteField.setOnClickListener { onDeleteFieldClick(field) }
                binding.llFieldsContainer.addView(fieldBinding.root)
            }
        }

        private fun setupTextWatcher(fieldBinding: ItemBlockFieldBinding, field: PersonBlockFieldEntity) {
            // Monitora o Texto Visível Principal
            fieldBinding.etFieldValue.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (isEditMode && fieldBinding.etFieldValue.hasFocus()) {
                        val newValue = s?.toString() ?: ""
                        val currentData = fieldBinding.etFieldUri.text?.toString()
                        onFieldUpdated(field.copy(value = newValue, actionData = currentData))
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            // Monitora o Campo de URL/Ação
            fieldBinding.etFieldUri.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (isEditMode && fieldBinding.etFieldUri.hasFocus()) {
                        val newData = s?.toString() ?: ""
                        var currentValue = fieldBinding.etFieldValue.text?.toString() ?: ""

                        // AUTOMAÇÃO: Se colar uma URL, Chave Pix, Endereço ou Tel e o valor visível estiver genérico ou vazio, extrai a versão bonita!
                        val extractedDisplay = AutoLinkParser.extractDisplayValue(fieldBinding.root.context, newData, field.fieldType)
                        if (extractedDisplay != null && (currentValue.isBlank() || currentValue == "Pix" || currentValue == "Endereço" || currentValue == "Link / Rede" || currentValue.startsWith("@"))) {
                            fieldBinding.etFieldValue.setText(extractedDisplay)
                            currentValue = extractedDisplay
                        }

                        onFieldUpdated(field.copy(value = currentValue, actionData = newData))
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        // Dispara a Intent nativa do Android para abrir apps, mapas ou navegadores
        private fun handleFieldAction(view: View, field: PersonBlockFieldEntity) {
            val context = view.context
            // Se o dado de ação técnico estiver preenchido, usa ele; senão, usa o texto visível
            val targetData = field.actionData?.takeIf { it.isNotBlank() } ?: field.value

            try {
                val intent = when (field.fieldType) {
                    FieldType.WHATSAPP -> {
                        val cleanNumber = targetData.replace(Regex("[^0-9]"), "")
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/55$cleanNumber"))
                    }
                    FieldType.MAPS -> {
                        val encodedAddress = Uri.encode(targetData)
                        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedAddress"))
                    }
                    FieldType.PHONE -> {
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$targetData"))
                    }
                    FieldType.PIX -> {
                        // 1. Gera o BRCode universal (Pix Copia e Cola)
                        val brCodePayload = PixPayloadGenerator.generatePayload(
                            pixKey = targetData,
                            merchantName = "Contato Odin",
                            city = "BRASIL"
                        )

                        // 2. Copia o CÓDIGO COMPLETO (Payload) para a área de transferência
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Pix Copia e Cola", brCodePayload)
                        clipboard.setPrimaryClip(clip)

                        // 3. Lista dos principais bancos utilizados
                        val banks = arrayOf(
                            "Nubank" to "com.nu.production",
                            "Banco Inter" to "br.com.intermedium",
                            "Itaú" to "com.itau",
                            "Mercado Pago" to "com.mercadopago.wallet",
                            "PicPay" to "com.picpay",
                            "Caixa" to "br.com.cefetba.caixa",
                            "C6 Bank" to "br.com.smdpp.bancoc6",
                            "Santander" to "com.santander.app"
                        )

                        val bankNames = banks.map { it.first }.toTypedArray()

                        // 4. Cria um menu de seleção customizado do O.D.I.N.
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                            .setTitle("Pix Copiado! Abrir qual banco?")
                            .setItems(bankNames) { _, which ->
                                val packageName = banks[which].second
                                // Procura a intenção de abertura nativa da aplicação do banco
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)

                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Aplicação do ${banks[which].first} não instalada neste telemóvel.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()

                        return
                    }
                    FieldType.LINK -> {
                        var url = targetData
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "https://$url"
                        }
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    }
                    else -> return
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Não foi possível abrir o aplicativo correspondente.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        private fun showDatePicker(
            fieldBinding: ItemBlockFieldBinding,
            field: PersonBlockFieldEntity
        ) {
            val calendar = Calendar.getInstance()
            val dialog = DatePickerDialog(
                binding.root.context,
                { _, year, month, dayOfMonth ->
                    val formattedDate = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year)
                    fieldBinding.etFieldValue.setText(formattedDate)
                    onFieldUpdated(field.copy(value = formattedDate))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            dialog.show()
        }
    }

    class BlockDiffCallback : DiffUtil.ItemCallback<PersonBlockEntity>() {
        override fun areItemsTheSame(oldItem: PersonBlockEntity, newItem: PersonBlockEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: PersonBlockEntity, newItem: PersonBlockEntity): Boolean =
            oldItem == newItem
    }
}