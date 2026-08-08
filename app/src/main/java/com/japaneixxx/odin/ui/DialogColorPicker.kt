package com.japaneixxx.odin.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DialogColorPicker(
    private val context: Context,
    private val onColorSelected: (String) -> Unit
) {
    private val colorPalette = listOf(
        "#2196F3", "#00BCD4", "#009688", "#4CAF50",
        "#8BC34A", "#FFEB3B", "#FFC107", "#FF9800",
        "#FF5722", "#F44336", "#E91E63", "#9C27B0",
        "#673AB7", "#3F51B5", "#607D8B", "#9E9E9E"
    )

    fun show() {
        val gridView = GridView(context).apply {
            numColumns = 4
            horizontalSpacing = 16
            verticalSpacing = 16
            setPadding(32, 32, 32, 32)
            adapter = ColorAdapter(context, colorPalette)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Selecione uma cor")
            .setView(gridView)
            .setNegativeButton("Cancelar", null)
            .create()

        gridView.setOnItemClickListener { _, _, position, _ ->
            onColorSelected(colorPalette[position])
            dialog.dismiss()
        }

        dialog.show()
    }

    private class ColorAdapter(
        private val context: Context,
        private val colors: List<String>
    ) : BaseAdapter() {

        override fun getCount(): Int = colors.size

        // Retorna explicitamente a String contendo o Hex da cor
        override fun getItem(position: Int): String = colors[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: View(context).apply {
                layoutParams = AbsListView.LayoutParams(120, 120)
            }

            val colorHex = getItem(position)

            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(colorHex))
            }

            view.background = drawable
            return view
        }
    }
}