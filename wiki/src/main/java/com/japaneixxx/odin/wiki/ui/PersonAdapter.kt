package com.japaneixxx.odin.wiki.ui

import android.net.Uri
import android.view.LayoutInflater
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
            binding.tvName.text = person.name
            binding.tvRelationship.text = person.relationship ?: "Sem vínculo definido"

            // Carrega a foto no mini avatar do card
            if (!person.photoPath.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(Uri.parse(person.photoPath))
                    .circleCrop()
                    .into(binding.ivListPhoto)
            } else {
                Glide.with(binding.root.context)
                    .load(android.R.drawable.sym_def_app_icon)
                    .circleCrop()
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