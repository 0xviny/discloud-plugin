package com.discloudplugin.discloud.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

class CreateDiscloudConfig : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return

        val typeOptions = arrayOf("BOT", "SITE")
        val selectedObj = Messages.showChooseDialog(
            "Selecione o tipo de projeto",
            "Discloud Config",
            typeOptions,
            typeOptions[0],
            null
        )
        val selected = selectedObj?.toString() ?: return
        val selectedIndex = typeOptions.indexOf(selected)

        val name = Messages.showInputDialog("Nome do projeto:", "Discloud Config", null)?.trim() ?: return
        val avatar = Messages.showInputDialog("URL do avatar (NULL para nenhuma):", "Discloud Config", null)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "https://i.imgur.com/bWhx7OT.png"
        val main = Messages.showInputDialog("Arquivo principal (ex: index.js):", "Discloud Config", null)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "index.js"
        val ram = Messages.showInputDialog("RAM (MB):", "Discloud Config", null)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: if (selectedIndex == 0) "100" else "512"

        val autorestartOptions = arrayOf("true", "false")
        val autorestartObj = Messages.showChooseDialog(
            "Autorestart?",
            "Discloud Config",
            autorestartOptions,
            autorestartOptions[1],
            null
        )
        val autorestart = autorestartObj?.toString() ?: "false"

        val version =
            Messages.showInputDialog("Versão:", "Discloud Config", null)?.trim()?.takeIf { it.isNotEmpty() } ?: "latest"
        val apt = Messages.showInputDialog("APT packages:", "Discloud Config", null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: "tools"
        val start = Messages.showInputDialog("Comando start:", "Discloud Config", null)?.trim() ?: ""
        val build = Messages.showInputDialog("Comando build:", "Discloud Config", null)?.trim() ?: ""

        val content = if (selectedIndex == 0) {
            """
            NAME=$name
            AVATAR=$avatar
            TYPE=bot
            MAIN=$main
            RAM=$ram
            AUTORESTART=$autorestart
            VERSION=$version
            APT=$apt
            START=$start
            BUILD=$build
            """.trimIndent()
        } else {
            """
            NAME=$name
            AVATAR=$avatar
            ID=${name.lowercase()}
            TYPE=site
            MAIN=$main
            RAM=$ram
            AUTORESTART=$autorestart
            VERSION=$version
            APT=$apt
            START=$start
            BUILD=$build
            """.trimIndent()
        }

        val file = File(basePath, "discloud.config")
        file.writeText(content)

        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)?.let {
            VfsUtil.markDirtyAndRefresh(true, false, false, it)
        }

        Messages.showInfoMessage("Arquivo discloud.config criado com sucesso!", "Discloud Config")
    }
}
