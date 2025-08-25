package com.discloudplugin.discloud.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.NlsContexts
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class ApiKeyConfigurable : Configurable {
    private var apiKeyField = JTextField()
    private var panel = JPanel()

    init {
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.add(JLabel("Digite aqui sua APIKey da Discloud:"))
        panel.add(apiKeyField)
    }

    override fun getDisplayName(): String = "Discloud Plugin - Settings"
    override fun createComponent(): JComponent = panel
    override fun isModified(): Boolean {
        val saved = ApiKeyState.getInstance().apiKey ?: ""
        return saved != apiKeyField.text
    }

    override fun apply() {
        ApiKeyState.getInstance().apiKey = apiKeyField.text
    }

    override fun reset() {
        apiKeyField.text = ApiKeyState.getInstance().apiKey ?: ""
    }
}