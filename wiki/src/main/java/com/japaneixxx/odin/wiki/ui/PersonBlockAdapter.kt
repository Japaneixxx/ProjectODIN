package com.japaneixxx.odin.wiki

import android.app.DatePickerDialog
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
import com.japaneixxx.odin.data.database.OdinDatabase
import com.japaneixxx.odin.data.entity.FieldType
import com.japaneixxx.odin.data.entity.PersonBlockEntity
import com.japaneixxx.odin.data.entity.PersonBlockFieldEntity
import com.japaneixxx.odin.wiki.databinding.ItemBlockFieldBinding
import com.japaneixxx.odin.wiki.databinding.ItemPersonBlockBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class PersonBlockAdapter(
    private val db: OdinDatabase,
    private val onAddFieldClick: (PersonBlockEntity) -> Unit,
    private val onDeleteBlockClick: (PersonBlockEntity) -> Unit,
    private val onFieldChanged: (PersonBlockFieldEntity, String) -> Unit,
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
            fieldsJob = activity.lifecycleScope.launch {
                db.personBlockDao().getFieldsForBlock(block.id).collectLatest { fields ->
                    renderFields(fields)
                }
            }
        }

        private fun renderFields(fields: List<PersonBlockFieldEntity>) {
            binding.llFieldsContainer.removeAllViews()
            val inflater = LayoutInflater.from(binding.root.context)

            for (field in fields) {
                val fieldBinding = ItemBlockFieldBinding.inflate(inflater, binding.llFieldsContainer, false)
                fieldBinding.tilField.hint = field.label

                // Desativa a edição do texto e esconde a lixeira se NÃO estiver no modo de edição
                fieldBinding.etFieldValue.isEnabled = isEditMode
                fieldBinding.btnDeleteField.visibility = if (isEditMode) View.VISIBLE else View.GONE

                when (field.fieldType) {
                    FieldType.DATE -> {
                        fieldBinding.etFieldValue.isFocusable = false
                        fieldBinding.etFieldValue.isClickable = isEditMode
                        fieldBinding.etFieldValue.setText(field.value)

                        fieldBinding.etFieldValue.setOnClickListener {
                            if (isEditMode) showDatePicker(fieldBinding, field)
                        }
                    }
                    FieldType.NUMBER -> {
                        fieldBinding.etFieldValue.inputType = InputType.TYPE_CLASS_NUMBER
                        fieldBinding.etFieldValue.setText(field.value)
                        setupTextWatcher(fieldBinding, field)
                    }
                    FieldType.TEXT -> {
                        fieldBinding.etFieldValue.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        fieldBinding.etFieldValue.setText(field.value)
                        setupTextWatcher(fieldBinding, field)
                    }
                }

                fieldBinding.btnDeleteField.setOnClickListener {
                    onDeleteFieldClick(field)
                }

                binding.llFieldsContainer.addView(fieldBinding.root)
            }
        }

        private fun setupTextWatcher(fieldBinding: ItemBlockFieldBinding, field: PersonBlockFieldEntity) {
            fieldBinding.etFieldValue.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (isEditMode) { // Só propaga a mudança se estiver no modo edição
                        val newValue = s?.toString() ?: ""
                        onFieldChanged(field, newValue)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        private fun showDatePicker(fieldBinding: ItemBlockFieldBinding, field: PersonBlockFieldEntity) {
            val calendar = Calendar.getInstance()
            val dialog = DatePickerDialog(
                binding.root.context,
                { _, year, month, dayOfMonth ->
                    val formattedDate = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year)
                    fieldBinding.etFieldValue.setText(formattedDate)
                    onFieldChanged(field, formattedDate)
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