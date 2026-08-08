package com.japaneixxx.odin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.japaneixxx.odin.data.entity.TagEntity
import com.japaneixxx.odin.databinding.DialogManageTagsBinding

class ManageTagsBottomSheet(
    private var tagsList: List<TagEntity>,
    private val onUpdateTag: (TagEntity, String, String) -> Unit,
    private val onDeleteTag: (TagEntity) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogManageTagsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ManageTagAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogManageTagsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ManageTagAdapter(
            tags = tagsList,
            onUpdateTag = onUpdateTag,
            onDeleteTag = onDeleteTag
        )

        binding.rvManageTags.layoutManager = LinearLayoutManager(requireContext())
        binding.rvManageTags.adapter = adapter
    }

    fun updateTags(newTags: List<TagEntity>) {
        tagsList = newTags
        if (_binding != null) {
            adapter.updateList(newTags)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}