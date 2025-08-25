package com.discloudplugin.discloud

import com.discloudplugin.discloud.settings.ApiKeyState
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.Messages

class PluginLauncher : StartupActivity {
    override fun runActivity(project: Project) {
        val state = ApiKeyState.getInstance()
        if (state.apiKey.isNullOrBlank()) {
            val input = Messages.showInputDialog(
                "Digite sua APIKey da Discloud:",
                "Configuração Inicial",
                Messages.getQuestionIcon()
            )

            if (!input.isNullOrBlank()) {
                state.apiKey = input
            }
        }
    }
}