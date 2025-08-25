package com.discloudplugin.discloud.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JTextField

class ApiKeyConfigurable : Configurable {
    private var apiKeyField = JTextField(25)
    private lateinit var mainPanel: DialogPanel

    override fun getDisplayName(): String = "Discloud Plugin - Settings"

    override fun createComponent(): DialogPanel {
        val state = ApiKeyState.getInstance()

        mainPanel = panel {
            group("Configuração da Discloud") {
                row("API Key:") {
                    cell(apiKeyField)
                        .bindText({ state.apiKey ?: "" }, { state.apiKey = it })
                        .resizableColumn()
                }
                row {
                    comment("Cole aqui sua API Key da Discloud. Você pode obter em <a href='https://docs.discloud.com/suporte/comandos/api'>discloud.com</a>.")
                }
            }
        }
        return mainPanel
    }

    override fun isModified(): Boolean = mainPanel.isModified()
    override fun apply() = mainPanel.apply()
    override fun reset() = mainPanel.reset()
}
