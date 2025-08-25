package com.discloudplugin.discloud.ui

import com.discloudplugin.discloud.api.DiscloudApiClient
import com.discloudplugin.discloud.settings.ApiKeyState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities
import com.intellij.openapi.ui.Messages
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.JTextArea

class DiscloudToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())
        val list = JBList<String>()
        val scroll = JBScrollPane(list)
        val refresh = JButton("Refresh")
        val startBtn = JButton("Start")
        val stopBtn = JButton("Stop")
        val logsBtn = JButton("Logs")

        val buttons = JPanel().apply {
            add(refresh); add(startBtn); add(stopBtn); add(logsBtn)
            border = JBUI.Borders.empty(4)
        }

        panel.add(scroll, BorderLayout.CENTER)
        panel.add(buttons, BorderLayout.SOUTH)

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
        stopBtn.addActionListener {
            val sel = list.selectedValue ?: return@addActionListener
            val id = sel.substringAfterLast("- ").trim()
            ApplicationManager.getApplication().executeOnPooledThread {
                client.stopApp(id)
                reloadList()
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
                    scrollPane.preferredSize = java.awt.Dimension(800, 600)
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
