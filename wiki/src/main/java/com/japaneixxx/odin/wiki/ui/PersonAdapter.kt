package com.japaneixxx.odin.wiki

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.japaneixxx.odin.data.entity.PersonEntity
import com.japaneixxx.odin.wiki.databinding.ItemPersonBinding

class PersonAdapter(
    private val onItemClick: (PersonEntity) -> Unit
) : ListAdapter<PersonEntity, PersonAdapter.PersonViewHolder>(PersonDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val binding = ItemPersonBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PersonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PersonViewHolder(
        private val binding: ItemPersonBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(person: PersonEntity) {
            // Formatação do índice Pokédex (ex: #0001)
            binding.tvIndexNumber.text = String.format("#%04d", person.id)

            // LÓGICA DE Destaque: Apelido Grande + Nome Pequeno (ou só Nome Grande)
            if (!person.nickname.isNullOrBlank()) {
                // Tem apelido: Apelido fica GRANDE e o Nome fica PEQUENO abaixo
                binding.tvMainName.text = person.nickname
                binding.tvSubName.text = person.name
                binding.tvSubName.visibility = View.VISIBLE
            } else {
                // Não tem apelido: Nome fica GRANDE e esconde o subtexto
                binding.tvMainName.text = person.name
                binding.tvSubName.visibility = View.GONE
            }

            // Badge de Vínculo / Tipo
            binding.tvRelationshipBadge.text = (person.relationship ?: "GERAL").uppercase()

            // Foto no mini visor
            if (!person.photoPath.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(Uri.parse(person.photoPath))
                    .centerCrop() // Preserva os cantos arredondados definidos no XML
                    .into(binding.ivListPhoto)
            } else {
                Glide.with(binding.root.context)
                    .load(android.R.drawable.sym_def_app_icon)
                    .centerCrop()
                    .into(binding.ivListPhoto)
            }

            binding.root.setOnClickListener {
                onItemClick(person)
            }
        }
    }

    class PersonDiffCallback : DiffUtil.ItemCallback<PersonEntity>() {
        override fun areItemsTheSame(oldItem: PersonEntity, newItem: PersonEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PersonEntity, newItem: PersonEntity): Boolean {
            return oldItem == newItem
        }
    }
}