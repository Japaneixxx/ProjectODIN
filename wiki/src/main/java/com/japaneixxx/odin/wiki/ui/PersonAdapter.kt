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
            binding.tvName.text = person.name

            // Apelido se existir
            if (!person.nickname.isNullOrEmpty()) {
                binding.tvNickname.visibility = View.VISIBLE
                binding.tvNickname.text = person.nickname
            } else {
                binding.tvNickname.visibility = View.GONE
            }

            // Badge de Vínculo / Tipo
            binding.tvRelationshipBadge.text = (person.relationship ?: "GERAL").uppercase()

            // Foto no mini visor
            if (!person.photoPath.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(Uri.parse(person.photoPath))
                    .centerCrop()
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