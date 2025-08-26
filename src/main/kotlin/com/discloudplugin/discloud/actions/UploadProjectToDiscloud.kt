package com.discloudplugin.discloud.actions

import com.discloudplugin.discloud.settings.ApiKeyState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

class UploadProjectToDiscloudAction : AnAction("Upar na Discloud") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project?.basePath != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val projectPath = project.basePath ?: return
        val configFile = File(projectPath, "discloud.config")
        if (!configFile.exists()) return
        var apiKey = ApiKeyState.getInstance().apiKey
        if (apiKey.isNullOrBlank()) {
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
            apiKey = input
        }
        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, "Empacotando e enviando para Discloud", true) {
                override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                    try {
                        indicator.text = "Criando zip do projeto"
                        val bytes = zipProject(projectPath)
                        if (bytes == null) {
                            SwingUtilities.invokeLater {
                                JOptionPane.showMessageDialog(
                                    null,
                                    "Falha ao criar zip do projeto",
                                    "Discloud",
                                    JOptionPane.ERROR_MESSAGE
                                )
                            }
                            return
                        }
                        indicator.text = "Enviando para Discloud"
                        val filename = "${File(projectPath).name}-project.zip"
                        val result = uploadBytesToUploadEndpoint(bytes, filename, apiKey!!)
                        SwingUtilities.invokeLater {
                            JOptionPane.showMessageDialog(null, result, "Discloud", JOptionPane.INFORMATION_MESSAGE)
                        }
                    } catch (ex: Exception) {
                        SwingUtilities.invokeLater {
                            JOptionPane.showMessageDialog(
                                null,
                                "Erro: ${ex.message}",
                                "Discloud",
                                JOptionPane.ERROR_MESSAGE
                            )
                        }
                    }
                }
            })
    }

    private fun zipProject(projectPath: String): ByteArray? {
        return try {
            val base = Paths.get(projectPath)
            val baos = ByteArrayOutputStream()
            ZipOutputStream(BufferedOutputStream(baos)).use { zos ->
                File(projectPath).walkTopDown().filter { it.isFile }.forEach { f ->
                    val rel = base.relativize(f.toPath()).toString().replace(File.separatorChar, '/')
                    if (shouldExclude(rel)) return@forEach
                    val entry = ZipEntry(rel)
                    zos.putNextEntry(entry)
                    FileInputStream(f).use { fis -> fis.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            baos.toByteArray()
        } catch (ex: Exception) {
            null
        }
    }

    private fun shouldExclude(relPath: String): Boolean {
        val lower = relPath.lowercase()
        if (lower.startsWith(".git/") || lower.contains("/.git/")) return true
        if (lower.startsWith("node_modules/") || lower.contains("/node_modules/")) return true
        if (lower.startsWith("venv/") || lower.contains("/venv/")) return true
        if (lower.startsWith("__pycache__/") || lower.contains("/__pycache__/")) return true
        if (lower.startsWith("target/") || lower.contains("/target/")) return true
        if (lower.startsWith("build/") || lower.contains("/build/")) return true
        if (lower.startsWith(".idea/") || lower.contains("/.idea/")) return true
        if (lower.startsWith(".gradle/") || lower.contains("/.gradle/")) return true
        return false
    }

    private fun uploadBytesToUploadEndpoint(bytes: ByteArray, filename: String, apiKey: String): String {
        return try {
            val boundary = "----DiscloudBoundary${System.currentTimeMillis()}"
            val url = URL("https://api.discloud.app/v2/upload")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("api-token", apiKey)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setChunkedStreamingMode(0)
            DataOutputStream(conn.outputStream).use { dos ->
                dos.writeBytes("--$boundary\r\n")
                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n")
                dos.writeBytes("Content-Type: application/zip\r\n\r\n")
                dos.flush()
                dos.write(bytes)
                dos.flush()
                dos.writeBytes("\r\n--$boundary--\r\n")
                dos.flush()
            }
            val responseCode = conn.responseCode
            val responseText = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Erro desconhecido"
            }
            try {
                val msg = org.json.JSONObject(responseText).optString("message", responseText)
                "Enviado $filename → Status: $responseCode\n$msg"
            } catch (e: Exception) {
                "Enviado $filename → Status: $responseCode\n$responseText"
            }
        } catch (ex: Exception) {
            "Erro ao enviar $filename: ${ex.message}"
        }
    }
}
