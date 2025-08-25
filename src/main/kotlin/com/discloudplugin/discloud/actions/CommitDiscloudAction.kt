package com.discloudplugin.discloud.actions

import com.discloudplugin.discloud.settings.ApiKeyState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import org.json.JSONObject
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import javax.swing.*

class CommitDiscloudAction : AnAction("Commit Discloud") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = files?.any { it.extension == "jar" } == true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        val apiKey = ApiKeyState.getInstance().apiKey ?: return
        val apps = fetchUserApps(apiKey) ?: return
        if (apps.isEmpty()) return

        var selectedApp: AppInfo? = null

        val step = object : BaseListPopupStep<AppInfo>("Escolha o app para enviar o commit:", apps) {
            override fun getTextFor(value: AppInfo): String = "${value.name} (${value.id})"

            override fun getIconFor(value: AppInfo): Icon? {
                return try {
                    val url = URL(value.avatarIcon)
                    val icon = ImageIcon(url)
                    val img = icon.image.getScaledInstance(32, 32, Image.SCALE_SMOOTH)
                    ImageIcon(makeRounded(img))
                } catch (e: Exception) {
                    null
                }
            }

            override fun onChosen(selectedValue: AppInfo?, finalChoice: Boolean): PopupStep<*>? {
                selectedApp = selectedValue
                return super.onChosen(selectedValue, finalChoice)
            }

            override fun getFinalRunnable(): Runnable? {
                return Runnable {
                    selectedApp?.let { app ->
                        ProgressManager.getInstance()
                            .run(object : Task.Backgroundable(event.project, "Enviando arquivos para Discloud", true) {
                                override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                                    files.filter { it.extension == "jar" }.forEach { file ->
                                        val result = commitToDiscloud(file, apiKey, app.id)
                                        SwingUtilities.invokeLater {
                                            JOptionPane.showMessageDialog(
                                                null,
                                                result,
                                                "Commit Discloud",
                                                JOptionPane.INFORMATION_MESSAGE
                                            )
                                        }
                                    }
                                }
                            })
                    }
                }
            }
        }

        val popup: ListPopup = JBPopupFactory.getInstance().createListPopup(step)
        event.project?.let { popup.showCenteredInCurrentWindow(it) } ?: popup.showInFocusCenter()
    }

    private fun makeRounded(img: Image): BufferedImage {
        val w = img.getWidth(null)
        val h = img.getHeight(null)
        val output = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g2 = output.createGraphics()
        g2.clip = java.awt.geom.Ellipse2D.Float(0f, 0f, w.toFloat(), h.toFloat())
        g2.drawImage(img, 0, 0, null)
        g2.dispose()
        return output
    }

    private fun fetchUserApps(apiKey: String): List<AppInfo>? {
        return try {
            val future = AppExecutorUtil.getAppExecutorService().submit(Callable {
                val url = URL("https://api.discloud.app/v2/user")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("api-token", apiKey)
                conn.connect()
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                if (json.optString("status") != "ok") return@Callable emptyList<AppInfo>()
                val appsJson = json.getJSONObject("user").getJSONArray("apps")
                val apps = mutableListOf<AppInfo>()
                for (i in 0 until appsJson.length()) {
                    val appId = appsJson.getString(i)
                    val appInfo = fetchAppInfoSync(appId, apiKey)
                    if (appInfo != null) apps.add(appInfo)
                }
                apps
            })
            future.get()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchAppInfoSync(appId: String, apiKey: String): AppInfo? {
        return try {
            val url = URL("https://api.discloud.app/v2/app/$appId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("api-token", apiKey)
            conn.connect()
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            if (json.optString("status") != "ok") return null
            val appJson = json.getJSONObject("apps")
            val id = appJson.getString("id")
            val name = appJson.getString("name")
            val avatar = appJson.optString("avatarURL", "")
            AppInfo(id, name, avatar)
        } catch (e: Exception) {
            null
        }
    }

    private fun commitToDiscloud(file: VirtualFile, apiKey: String, appId: String): String {
        return try {
            val fileBytes = AppExecutorUtil.getAppExecutorService().submit(Callable {
                file.inputStream.use { it.readBytes() }
            }).get()
            val fileName = file.name
            val boundary = "----DiscloudBoundary${System.currentTimeMillis()}"
            val url = URL("https://api.discloud.app/v2/app/$appId/commit")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.setRequestProperty("api-token", apiKey)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setChunkedStreamingMode(0)
            DataOutputStream(conn.outputStream).use { dos ->
                dos.writeBytes("--$boundary\r\n")
                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
                dos.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                dos.flush()
                dos.write(fileBytes)
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

            val message = try {
                JSONObject(responseText).optString("message", responseText)
            } catch (e: Exception) {
                responseText
            }

            "Enviado $fileName → Status: $responseCode\n$message"
        } catch (e: Exception) {
            e.printStackTrace()
            "Erro ao enviar ${file.name}: ${e.message}"
        }
    }

    data class AppInfo(val id: String, val name: String, val avatarIcon: String)
}
