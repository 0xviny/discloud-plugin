package com.discloudplugin.discloud.ui

import com.discloudplugin.discloud.api.AppInfoData
import com.discloudplugin.discloud.api.DiscloudApiClient
import com.discloudplugin.discloud.api.TeamMemberData
import com.discloudplugin.discloud.settings.ApiKeyState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.*

class DiscloudToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val mainPanel = JPanel(BorderLayout())
        val tabs = JTabbedPane()
        mainPanel.add(tabs, BorderLayout.CENTER)
        var token = ApiKeyState.getInstance().apiKey
        if (token.isNullOrBlank()) {
            val input = Messages.showInputDialog(
                project,
                "API token não configurado. Insira seu token da Discloud:",
                "Configurar API Token",
                Messages.getQuestionIcon()
            )
            if (input.isNullOrBlank()) {
                Messages.showErrorDialog(project, "API token é necessário para usar o plugin", "Discloud")
                return
            }
            ApiKeyState.getInstance().apiKey = input
            token = input
        }
        val client = DiscloudApiClient(token)
        val appsPanel = JPanel(BorderLayout())
        val appsTop = JPanel()
        val refreshAppsBtn = JButton("Refresh")
        appsTop.add(refreshAppsBtn)
        appsPanel.add(appsTop, BorderLayout.NORTH)
        val appsList = JBList<String>()
        val appsScroll = JBScrollPane(appsList)
        appsPanel.add(appsScroll, BorderLayout.CENTER)
        val appsPopup = JPopupMenu()
        val startMenu = JMenuItem("Start")
        val restartMenu = JMenuItem("Restart")
        val stopMenu = JMenuItem("Stop")
        val backupMenu = JMenuItem("Backup")
        val logsMenu = JMenuItem("Logs")
        val ramMenu = JMenuItem("Edit Ram")
        val deleteMenu = JMenuItem("Delete")
        appsPopup.add(startMenu)
        appsPopup.add(restartMenu)
        appsPopup.add(stopMenu)
        appsPopup.addSeparator()
        appsPopup.add(backupMenu)
        appsPopup.add(logsMenu)
        appsPopup.add(ramMenu)
        appsPopup.add(deleteMenu)
        appsList.componentPopupMenu = appsPopup
        fun reloadAppsList() {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val apps = client.listApps()
                    SwingUtilities.invokeLater {
                        appsList.setListData(apps.map { "${it.name} (${if (it.online) "online" else "offline"}, ${it.ram}MB) - ${it.id}" }.toTypedArray())
                    }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater {
                        appsList.setListData(arrayOf("Erro: ${t.message}"))
                    }
                }
            }
        }
        refreshAppsBtn.addActionListener { reloadAppsList() }
        startMenu.addActionListener {
            val sel = appsList.selectedValue ?: return@addActionListener
            if (!sel.contains("- ")) {
                Messages.showErrorDialog(project, "Selecione um app válido", "Discloud")
                return@addActionListener
            }
            val id = sel.substringAfterLast("- ").trim()
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.startApp(id)
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Erro ao iniciar app: ${ex.message}", "Discloud")
                    }
                } finally {
                    reloadAppsList()
                }
            }
        }
        restartMenu.addActionListener {
            val sel = appsList.selectedValue ?: return@addActionListener
            if (!sel.contains("- ")) {
                Messages.showErrorDialog(project, "Selecione um app válido", "Discloud")
                return@addActionListener
            }
            val id = sel.substringAfterLast("- ").trim()
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.restartApp(id)
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Erro ao reiniciar app: ${ex.message}", "Discloud")
                    }
                } finally {
                    reloadAppsList()
                }
            }
        }
        stopMenu.addActionListener {
            val sel = appsList.selectedValue ?: return@addActionListener
            if (!sel.contains("- ")) {
                Messages.showErrorDialog(project, "Selecione um app válido", "Discloud")
                return@addActionListener
            }
            val id = sel.substringAfterLast("- ").trim()
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.stopApp(id)
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Erro ao parar app: ${ex.message}", "Discloud")
                    }
                } finally {
                    reloadAppsList()
                }
            }
        }
        backupMenu.addActionListener {
            val sel = appsList.selectedValue ?: return@addActionListener
            if (!sel.contains("- ")) {
                Messages.showErrorDialog(project, "Selecione um app válido", "Discloud")
                return@addActionListener
            }
            val id = sel.substringAfterLast("- ").trim()
            val projectPath = project.basePath ?: return@addActionListener
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.getBackup(id, projectPath)
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(project, "Backup baixado com sucesso em $projectPath/backup_$id.zip", "Discloud")
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Erro ao baixar backup: ${ex.message}", "Discloud")
                    }
                } finally {
                    reloadAppsList()
                }
            }
        }
        logsMenu.addActionListener {
            val sel = appsList.selectedValue ?: return@addActionListener
            if (!sel.contains("- ")) {
                Messages.showErrorDialog(project, "Selecione um app válido", "Discloud")
                return@addActionListener
            }
            val id = sel.substringAfterLast("- ").trim()
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val logs = client.getLogs(id)
                    SwingUtilities.invokeLater {
                        val textArea = JTextArea(logs)
                        textArea.isEditable = false
                        textArea.lineWrap = true
                        textArea.wrapStyleWord = true
                        val scrollPane = JScrollPane(textArea)
                        scrollPane.preferredSize = Dimension(800, 600)
                        JOptionPane.showMessageDialog(appsPanel, scrollPane, "Logs: $id", JOptionPane.INFORMATION_MESSAGE)
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Erro ao buscar logs: ${ex.message}", "Discloud")
                    }
                }
            }
        }
        ramMenu.addActionListener {
            val sel = appsList.selectedValue ?: return@addActionListener
            if (!sel.contains("- ")) {
                Messages.showErrorDialog(project, "Selecione um app válido", "Discloud")
                return@addActionListener
            }
            val id = sel.substringAfterLast("- ").trim()
            val input = Messages.showInputDialog(project, "Informe a nova quantidade de RAM (MB):", "Editar RAM", Messages.getQuestionIcon())
            if (input.isNullOrBlank()) return@addActionListener
            val ramValue = input.toIntOrNull()
            if (ramValue == null || ramValue <= 0) {
                Messages.showErrorDialog(project, "Valor inválido de RAM", "Discloud")
                return@addActionListener
            }
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.updateRam(id, ramValue)
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(project, "RAM alterada com sucesso para $ramValue MB", "Discloud")
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Erro ao alterar RAM: ${ex.message}", "Discloud")
                    }
                } finally {
                    reloadAppsList()
                }
            }
        }
        deleteMenu.addActionListener {
            val sel = appsList.selectedValue ?: return@addActionListener
            if (!sel.contains("- ")) {
                Messages.showErrorDialog(project, "Selecione um app válido", "Discloud")
                return@addActionListener
            }
            val id = sel.substringAfterLast("- ").trim()
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.deleteApp(id)
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Erro ao deletar app: ${ex.message}", "Discloud")
                    }
                } finally {
                    reloadAppsList()
                }
            }
        }
        tabs.addTab("Apps", appsPanel)
        val teamPanel = JPanel(BorderLayout())
        val teamTop = JPanel()
        val appsCombo = JComboBox<String>()
        val refreshTeamAppsBtn = JButton("Refresh Apps")
        teamTop.add(appsCombo)
        teamTop.add(refreshTeamAppsBtn)
        teamPanel.add(teamTop, BorderLayout.NORTH)
        val teamList = JBList<String>()
        val teamScroll = JBScrollPane(teamList)
        teamPanel.add(teamScroll, BorderLayout.CENTER)
        val teamPopup = JPopupMenu()
        val addMember = JMenuItem("Add Member")
        val editPerms = JMenuItem("Edit Perms")
        val removeMember = JMenuItem("Remove Member")
        val teamAppsMenu = JMenuItem("Team Apps")
        val modsMenu = JMenuItem("Get Mods")
        val teamStart = JMenuItem("Start (Team)")
        val teamRestart = JMenuItem("Restart (Team)")
        val teamStop = JMenuItem("Stop (Team)")
        val teamCommit = JMenuItem("Commit File")
        val teamBackup = JMenuItem("Backup (Team)")
        val teamLogs = JMenuItem("Logs (Team)")
        val teamRam = JMenuItem("Edit RAM (Team)")
        val teamStatus = JMenuItem("Status (Team)")
        teamPopup.add(addMember)
        teamPopup.add(editPerms)
        teamPopup.add(removeMember)
        teamPopup.addSeparator()
        teamPopup.add(teamAppsMenu)
        teamPopup.add(modsMenu)
        teamPopup.addSeparator()
        teamPopup.add(teamStart)
        teamPopup.add(teamRestart)
        teamPopup.add(teamStop)
        teamPopup.add(teamCommit)
        teamPopup.add(teamBackup)
        teamPopup.add(teamLogs)
        teamPopup.add(teamRam)
        teamPopup.add(teamStatus)
        teamList.componentPopupMenu = teamPopup
        var appsCache: List<AppInfoData> = emptyList()
        fun reloadTeamAppsCombo() {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val apps = client.listApps()
                    appsCache = apps
                    SwingUtilities.invokeLater {
                        appsCombo.removeAllItems()
                        apps.forEach { appsCombo.addItem("${it.name} - ${it.id}") }
                        if (apps.isNotEmpty()) appsCombo.selectedIndex = 0
                    }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater {
                        appsCombo.removeAllItems()
                        Messages.showErrorDialog(project, "Erro ao listar apps: ${t.message}", "Team Manager")
                    }
                }
            }
        }
        fun reloadTeamForSelectedApp() {
            val sel = appsCombo.selectedItem as? String ?: return
            if (!sel.contains(" - ")) {
                Messages.showErrorDialog(project, "Selecione um app válido", "Team Manager")
                return
            }
            val id = sel.substringAfterLast(" - ").trim()
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val team = client.getAppTeam(id)
                    SwingUtilities.invokeLater {
                        teamList.setListData(team.map { "${it.modID} - ${it.perms.joinToString(",")}" }.toTypedArray())
                    }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater {
                        teamList.setListData(arrayOf("Erro: ${t.message}"))
                    }
                }
            }
        }
        refreshTeamAppsBtn.addActionListener { reloadTeamAppsCombo() }
        appsCombo.addActionListener { reloadTeamForSelectedApp() }
        addMember.addActionListener {
            val sel = appsCombo.selectedItem as? String ?: return@addActionListener
            if (!sel.contains(" - ")) {
                Messages.showErrorDialog(project, "Selecione um app válido", "Team Manager")
                return@addActionListener
            }
            val id = sel.substringAfterLast(" - ").trim()
            val modID = Messages.showInputDialog(project, "Informe o modID do membro:", "Adicionar Membro", Messages.getQuestionIcon()) ?: return@addActionListener
            val permsInput = Messages.showInputDialog(project, "Informe as permissoes separadas por vírgula:", "Adicionar Membro", Messages.getQuestionIcon()) ?: return@addActionListener
            val perms = permsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.addTeamMember(id, modID, perms)
                    reloadTeamForSelectedApp()
                    SwingUtilities.invokeLater { Messages.showInfoMessage(project, "Membro adicionado com sucesso", "Team Manager") }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater { Messages.showErrorDialog(project, "Erro ao adicionar membro: ${t.message}", "Team Manager") }
                }
            }
        }
        editPerms.addActionListener {
            val selApp = appsCombo.selectedItem as? String ?: return@addActionListener
            if (!selApp.contains(" - ")) {
                Messages.showErrorDialog(project, "Selecione um app válido", "Team Manager")
                return@addActionListener
            }
            val appId = selApp.substringAfterLast(" - ").trim()
            val sel = teamList.selectedValue ?: return@addActionListener
            if (!sel.contains(" - ")) {
                Messages.showErrorDialog(project, "Selecione um membro válido", "Team Manager")
                return@addActionListener
            }
            val modID = sel.substringBefore(" - ").trim()
            val permsInput = Messages.showInputDialog(project, "Informe as novas permissoes separadas por vírgula:", "Editar Perms", Messages.getQuestionIcon()) ?: return@addActionListener
            val perms = permsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.editTeamMember(appId, modID, perms)
                    reloadTeamForSelectedApp()
                    SwingUtilities.invokeLater { Messages.showInfoMessage(project, "Permissões atualizadas", "Team Manager") }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater { Messages.showErrorDialog(project, "Erro ao editar permissões: ${t.message}", "Team Manager") }
                }
            }
        }
        removeMember.addActionListener {
            val selApp = appsCombo.selectedItem as? String ?: return@addActionListener
            if (!selApp.contains(" - ")) {
                Messages.showErrorDialog(project, "Selecione um app válido", "Team Manager")
                return@addActionListener
            }
            val appId = selApp.substringAfterLast(" - ").trim()
            val sel = teamList.selectedValue ?: return@addActionListener
            if (!sel.contains(" - ")) {
                Messages.showErrorDialog(project, "Selecione um membro válido", "Team Manager")
                return@addActionListener
            }
            val modID = sel.substringBefore(" - ").trim()
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.removeTeamMember(appId, modID)
                    reloadTeamForSelectedApp()
                    SwingUtilities.invokeLater { Messages.showInfoMessage(project, "Membro removido", "Team Manager") }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater { Messages.showErrorDialog(project, "Erro ao remover membro: ${t.message}", "Team Manager") }
                }
            }
        }
        teamAppsMenu.addActionListener {
            val ownerId = Messages.showInputDialog(project, "Informe o ownerID:", "Team Apps", Messages.getQuestionIcon()) ?: return@addActionListener
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val apps = client.listTeamApps(ownerId)
                    SwingUtilities.invokeLater {
                        val arr = apps.map { "${it.name} (${if (it.online) "online" else "offline"}) - ${it.id}" }.toTypedArray()
                        JOptionPane.showMessageDialog(teamPanel, JBScrollPane(JList(arr)), "Team Apps", JOptionPane.INFORMATION_MESSAGE)
                    }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater { Messages.showErrorDialog(project, "Erro ao obter team apps: ${t.message}", "Team Manager") }
                }
            }
        }
        modsMenu.addActionListener {
            val ownerId = Messages.showInputDialog(project, "Informe o ownerID:", "Get Mods", Messages.getQuestionIcon()) ?: return@addActionListener
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val mods = client.getMods(ownerId)
                    SwingUtilities.invokeLater {
                        val arr = mods.map { "${it.modID} - ${it.perms.joinToString(",")}" }.toTypedArray()
                        JOptionPane.showMessageDialog(teamPanel, JBScrollPane(JList(arr)), "Mods", JOptionPane.INFORMATION_MESSAGE)
                    }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater { Messages.showErrorDialog(project, "Erro ao obter mods: ${t.message}", "Team Manager") }
                }
            }
        }
        teamStart.addActionListener {
            val appId = Messages.showInputDialog(project, "Informe appID ou all:", "Team Start", Messages.getQuestionIcon()) ?: return@addActionListener
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.teamStart(appId)
                    SwingUtilities.invokeLater { Messages.showInfoMessage(project, "Start enviado", "Team Manager") }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater { Messages.showErrorDialog(project, "Erro ao iniciar: ${t.message}", "Team Manager") }
                }
            }
        }
        teamRestart.addActionListener {
            val appId = Messages.showInputDialog(project, "Informe appID ou all:", "Team Restart", Messages.getQuestionIcon()) ?: return@addActionListener
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.teamRestart(appId)
                    SwingUtilities.invokeLater { Messages.showInfoMessage(project, "Restart enviado", "Team Manager") }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater { Messages.showErrorDialog(project, "Erro ao reiniciar: ${t.message}", "Team Manager") }
                }
            }
        }
        teamStop.addActionListener {
            val appId = Messages.showInputDialog(project, "Informe appID ou all:", "Team Stop", Messages.getQuestionIcon()) ?: return@addActionListener
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.teamStop(appId)
                    SwingUtilities.invokeLater { Messages.showInfoMessage(project, "Stop enviado", "Team Manager") }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater { Messages.showErrorDialog(project, "Erro ao parar: ${t.message}", "Team Manager") }
                }
            }
        }
        teamCommit.addActionListener {
            val appId = Messages.showInputDialog(project, "Informe appID:", "Team Commit", Messages.getQuestionIcon()) ?: return@addActionListener
            val filePath = Messages.showInputDialog(project, "Informe o caminho do arquivo para commit:", "Team Commit", Messages.getQuestionIcon()) ?: return@addActionListener
            val file = File(filePath)
            if (!file.exists()) {
                Messages.showErrorDialog(project, "Arquivo não encontrado", "Team Manager")
                return@addActionListener
            }
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.teamCommit(appId, file)
                    SwingUtilities.invokeLater { Messages.showInfoMessage(project, "Commit enviado", "Team Manager") }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater { Messages.showErrorDialog(project, "Erro ao commitar: ${t.message}", "Team Manager") }
                }
            }
        }
        teamBackup.addActionListener {
            val appId = Messages.showInputDialog(project, "Informe appID ou all:", "Team Backup", Messages.getQuestionIcon()) ?: return@addActionListener
            val projectPath = project.basePath ?: return@addActionListener
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.teamBackup(appId, projectPath)
                    SwingUtilities.invokeLater { Messages.showInfoMessage(project, "Backup baixado em $projectPath", "Team Manager") }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater { Messages.showErrorDialog(project, "Erro ao baixar backup: ${t.message}", "Team Manager") }
                }
            }
        }
        teamLogs.addActionListener {
            val appId = Messages.showInputDialog(project, "Informe appID ou all:", "Team Logs", Messages.getQuestionIcon()) ?: return@addActionListener
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val logs = client.teamLogs(appId)
                    SwingUtilities.invokeLater {
                        val textArea = JTextArea(logs)
                        textArea.isEditable = false
                        textArea.lineWrap = true
                        textArea.wrapStyleWord = true
                        val scrollPane = JScrollPane(textArea)
                        scrollPane.preferredSize = Dimension(800, 600)
                        JOptionPane.showMessageDialog(teamPanel, scrollPane, "Logs: $appId", JOptionPane.INFORMATION_MESSAGE)
                    }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater { Messages.showErrorDialog(project, "Erro ao buscar logs: ${t.message}", "Team Manager") }
                }
            }
        }
        teamRam.addActionListener {
            val appId = Messages.showInputDialog(project, "Informe appID:", "Team Edit RAM", Messages.getQuestionIcon()) ?: return@addActionListener
            val input = Messages.showInputDialog(project, "Informe a nova quantidade de RAM (MB):", "Editar RAM", Messages.getQuestionIcon()) ?: return@addActionListener
            val ramValue = input.toIntOrNull()
            if (ramValue == null || ramValue <= 0) {
                Messages.showErrorDialog(project, "Valor inválido de RAM", "Team Manager")
                return@addActionListener
            }
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.teamUpdateRam(appId, ramValue)
                    SwingUtilities.invokeLater { Messages.showInfoMessage(project, "RAM alterada para $ramValue MB", "Team Manager") }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater { Messages.showErrorDialog(project, "Erro ao alterar RAM: ${t.message}", "Team Manager") }
                }
            }
        }
        teamStatus.addActionListener {
            val appId = Messages.showInputDialog(project, "Informe appID ou all:", "Team Status", Messages.getQuestionIcon()) ?: return@addActionListener
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val status = client.teamStatus(appId)
                    SwingUtilities.invokeLater {
                        Messages.showInfoMessage(project, "App: ${status.name}\nID: ${status.id}\nOnline: ${status.online}\nRAM: ${status.ram}MB", "Team Status")
                    }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater { Messages.showErrorDialog(project, "Erro ao obter status: ${t.message}", "Team Manager") }
                }
            }
        }
        tabs.addTab("Team Manager", teamPanel)
        val content = toolWindow.contentManager.factory.createContent(mainPanel, "Discloud", false)
        toolWindow.contentManager.addContent(content)
        reloadAppsList()
        reloadTeamAppsCombo()
    }
}
