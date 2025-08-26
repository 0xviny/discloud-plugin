package com.discloudplugin.discloud.ui

import com.discloudplugin.discloud.api.DiscloudApiClient
import com.discloudplugin.discloud.settings.ApiKeyState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

class DiscloudToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())

        val topBar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 8))
        val refresh = JButton("Refresh")
        topBar.add(refresh)
        panel.add(topBar, BorderLayout.NORTH)

        val list = JBList<String>()
        val scroll = JBScrollPane(list)
        panel.add(scroll, BorderLayout.CENTER)

        val startBtn = JButton(IconLoader.getIcon("/icons/start.svg", javaClass))
        val restartBtn = JButton(IconLoader.getIcon("/icons/restart.svg", javaClass))
        val stopBtn = JButton(IconLoader.getIcon("/icons/stop.svg", javaClass))
        val backupBtn = JButton(IconLoader.getIcon("/icons/backup.svg", javaClass))
        val logsBtn = JButton(IconLoader.getIcon("/icons/logs.svg", javaClass))

        val actionButtons = JPanel(FlowLayout(FlowLayout.CENTER, 16, 8)).apply {
            add(startBtn); add(restartBtn); add(stopBtn); add(backupBtn); add(logsBtn)
            border = JBUI.Borders.empty(8)
        }
        panel.add(actionButtons, BorderLayout.SOUTH)

        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)

        val token = ApiKeyState.getInstance().apiKey
        val client = DiscloudApiClient(token!!)

        fun reloadList() {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val apps = client.listApps()
                    SwingUtilities.invokeLater {
                        list.setListData(apps.map {
                            "${it.name} (${if (it.online) "online" else "offline"}, ${it.ram}MB) - ${it.id}"
                        }.toTypedArray())
                    }
                } catch (t: Throwable) {
                    SwingUtilities.invokeLater {
                        list.setListData(arrayOf("Erro: ${t.message}"))
                    }
                }
            }
        }

        refresh.addActionListener { reloadList() }

        startBtn.addActionListener {
            val sel = list.selectedValue ?: return@addActionListener
            val id = sel.substringAfterLast("- ").trim()
            ApplicationManager.getApplication().executeOnPooledThread {
                client.startApp(id)
                reloadList()
            }
        }

        restartBtn.addActionListener {
            val sel = list.selectedValue ?: return@addActionListener
            val id = sel.substringAfterLast("- ").trim()
            ApplicationManager.getApplication().executeOnPooledThread {
                client.restartApp(id)
                reloadList()
            }
        }

        stopBtn.addActionListener {
            val sel = list.selectedValue ?: return@addActionListener
            val id = sel.substringAfterLast("- ").trim()
            ApplicationManager.getApplication().executeOnPooledThread {
                client.stopApp(id)
                reloadList()
            }
        }

        backupBtn.addActionListener {
            val sel = list.selectedValue ?: return@addActionListener
            val id = sel.substringAfterLast("- ").trim()
            val projectPath = project.basePath ?: return@addActionListener
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    client.getBackup(id, projectPath)
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            "Backup baixado com sucesso em $projectPath/backup_$id.zip",
                            "Discloud"
                        )
                    }
                    reloadList()
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog("Erro ao baixar backup: ${ex.message}", "Discloud")
                    }
                }
            }
        }

        logsBtn.addActionListener {
            val sel = list.selectedValue ?: return@addActionListener
            val id = sel.substringAfterLast("- ").trim()
            ApplicationManager.getApplication().executeOnPooledThread {
                val logs = client.getLogs(id)
                SwingUtilities.invokeLater {
                    val textArea = JTextArea(logs)
                    textArea.isEditable = false
                    textArea.lineWrap = true
                    textArea.wrapStyleWord = true
                    val scrollPane = JScrollPane(textArea)
                    scrollPane.preferredSize = Dimension(800, 600)
                    JOptionPane.showMessageDialog(
                        panel,
                        scrollPane,
                        "Logs: $id",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            }
        }

        reloadList()
    }
}
