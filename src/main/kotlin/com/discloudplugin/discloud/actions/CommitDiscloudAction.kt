package com.discloudplugin.discloud.actions

import com.discloudplugin.discloud.settings.ApiKeyState
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class CommitDiscloudAction : AnAction("Commit Discloud") {
    override fun update(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = files?.any { it.extension == "jar" } == true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        val apiKey = ApiKeyState.getInstance().apiKey

        if (apiKey.isNullOrBlank()) {
            Messages.showErrorDialog("Você precisa configurar sua API Key primeiro em Settings → Discloud Settings.", "Erro")
            return
        }

        files.filter { it.extension == "jar" }.forEach { file ->
            val result = commitToDiscloud(file, apiKey)
            Messages.showInfoMessage(result, "Commit Discloud")
        }
    }

    private fun commitToDiscloud(file: VirtualFile, apiKey: String): String {
        return try {
            val url = URL("https://api.discloud.app/v2/app")
            val boundary = "----DiscloudBoundary${System.currentTimeMillis()}"

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", apiKey)
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val output = DataOutputStream(conn.outputStream)

            output.writeBytes("--$boundary\r\n")
            output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
            output.writeBytes("Content-Type: application/java-archive\r\n\r\n")
            output.write(file.contentsToByteArray())
            output.writeBytes("\r\n")

            output.writeBytes("--$boundary--\r\n")
            output.flush()
            output.close()

            val responseCode = conn.responseCode
            val response = conn.inputStream.bufferedReader().use { it.readText() }

            "Enviado ${file.name} → Status: $responseCode\nResposta: $response"
        } catch (e: Exception) {
            "Erro ao enviar ${file.name}: ${e.message}"
        }
    }
}
