package com.japaneixxx.odin.wiki

import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDivider
import com.japaneixxx.odin.data.entity.BlockTemplateEntity
import com.japaneixxx.odin.data.entity.BlockTemplateFieldEntity
import com.japaneixxx.odin.data.provider.OdinDataClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlobalModulesActivity : AppCompatActivity() {
    private val dataClient by lazy { OdinDataClient(contentResolver) }
    private lateinit var moduleList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@GlobalModulesActivity, R.color.odin_background))
            setPadding(24, 24, 24, 24)
        }
        val title = TextView(this).apply {
            text = "MÓDULOS GLOBAIS"
            textSize = 22f
            setTextColor(ContextCompat.getColor(this@GlobalModulesActivity, R.color.odin_text_primary))
        }
        root.addView(title)
        root.addView(TextView(this).apply {
            text = "Templates reutilizáveis para qualquer pessoa"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@GlobalModulesActivity, R.color.odin_text_secondary))
        })
        root.addView(MaterialButton(this).apply {
            text = "Novo módulo global"
            setOnClickListener { showTemplateDialog(null) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        moduleList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(moduleList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        loadTemplates()
    }

    override fun onResume() {
        super.onResume()
        if (::moduleList.isInitialized) loadTemplates()
    }

    private fun loadTemplates() {
        lifecycleScope.launch {
            val templates = withContext(Dispatchers.IO) { dataClient.getTemplates() }
            moduleList.removeAllViews()
            templates.forEach { template ->
                val row = LinearLayout(this@GlobalModulesActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 12, 0, 12)
                }
                row.addView(TextView(this@GlobalModulesActivity).apply {
                    text = template.title
                    textSize = 17f
                    setTextColor(ContextCompat.getColor(this@GlobalModulesActivity, R.color.odin_text_primary))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(MaterialButton(this@GlobalModulesActivity).apply {
                    text = "Campos"
                    setOnClickListener { showTemplateFieldsDialog(template) }
                })
                row.addView(MaterialButton(this@GlobalModulesActivity).apply {
                    text = "Editar"
                    setOnClickListener { showTemplateDialog(template) }
                })
                row.addView(MaterialButton(this@GlobalModulesActivity).apply {
                    text = "Excluir"
                    setOnClickListener { confirmDelete(template) }
                })
                moduleList.addView(row)
                moduleList.addView(MaterialDivider(this@GlobalModulesActivity))
            }
        }
    }

    private fun showTemplateDialog(template: BlockTemplateEntity?) {
        val input = EditText(this).apply {
            hint = "Nome do módulo"
            setText(template?.title.orEmpty())
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (template == null) "Novo módulo global" else "Editar módulo global")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        if (template == null) dataClient.insertTemplate(BlockTemplateEntity(title = title))
                        else dataClient.updateTemplate(template.copy(title = title))
                        withContext(Dispatchers.Main) { loadTemplates() }
                    }
                }
            }
            .show()
    }

    private fun confirmDelete(template: BlockTemplateEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Excluir módulo global?")
            .setMessage("Os módulos já adicionados às pessoas não serão removidos.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    dataClient.deleteTemplate(template)
                    withContext(Dispatchers.Main) { loadTemplates() }
                }
            }
            .show()
    }

    private fun showTemplateFieldsDialog(template: BlockTemplateEntity) {
        lifecycleScope.launch {
            val fields = withContext(Dispatchers.IO) { dataClient.getTemplateFields(template.templateId) }
            val labels = if (fields.isEmpty()) "Nenhum campo definido" else fields.joinToString("\n") { "• ${it.label}" }
            MaterialAlertDialogBuilder(this@GlobalModulesActivity)
                .setTitle("Campos // ${template.title}")
                .setMessage(labels)
                .setPositiveButton("Adicionar campo") { _, _ -> showAddTemplateFieldDialog(template) }
                .setNegativeButton("Fechar", null)
                .show()
        }
    }

    private fun showAddTemplateFieldDialog(template: BlockTemplateEntity) {
        val input = EditText(this).apply { hint = "Nome do campo" }
        MaterialAlertDialogBuilder(this)
            .setTitle("Novo campo no template")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Adicionar") { _, _ ->
                val label = input.text.toString().trim()
                if (label.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        dataClient.insertTemplateField(BlockTemplateFieldEntity(templateId = template.templateId, label = label))
                    }
                }
            }
            .show()
    }
}