package com.japaneixxx.odin.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.japaneixxx.odin.data.entity.TagEntity
import com.japaneixxx.odin.databinding.ItemManageTagBinding

class ManageTagAdapter(
    private var tags: List<TagEntity>,
    private val onUpdateTag: (TagEntity, String, String) -> Unit,
    private val onDeleteTag: (TagEntity) -> Unit
) : RecyclerView.Adapter<ManageTagAdapter.ManageTagViewHolder>() {

    inner class ManageTagViewHolder(val binding: ItemManageTagBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ManageTagViewHolder {
        val binding = ItemManageTagBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ManageTagViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ManageTagViewHolder, position: Int) {
        val tag = tags[position]
        val binding = holder.binding

        binding.etTagName.setText(tag.name)
        binding.etTagColor.setText(tag.colorHex)

        // Atualiza preview inicial da cor
        fun updatePreview(hex: String) {
            try {
                binding.vTagColorPreview.setBackgroundColor(Color.parseColor(hex))
            } catch (e: Exception) {
                // Hex inválido
            }
        }

        updatePreview(tag.colorHex)

        // ABRIR O COLOR PICKER AO CLICAR NA COR PREVIEW
        binding.vTagColorPreview.setOnClickListener {
            DialogColorPicker(holder.itemView.context) { selectedHex ->
                binding.etTagColor.setText(selectedHex)
                updatePreview(selectedHex)
            }.show()
        }

        // Salvar alterações da tag
        binding.btnSaveTag.setOnClickListener {
            val newName = binding.etTagName.text?.toString()?.trim() ?: ""
            val newColor = binding.etTagColor.text?.toString()?.trim() ?: "#2196F3"

            if (newName.isNotEmpty()) {
                onUpdateTag(tag, newName, newColor)
                Toast.makeText(holder.itemView.context, "Tag atualizada!", Toast.LENGTH_SHORT).show()
            }
        }

        // Deletar tag
        binding.btnDeleteTag.setOnClickListener {
            onDeleteTag(tag)
        }
    }

    override fun getItemCount(): Int = tags.size

    fun updateList(newList: List<TagEntity>) {
        tags = newList
        notifyDataSetChanged()
    }
}