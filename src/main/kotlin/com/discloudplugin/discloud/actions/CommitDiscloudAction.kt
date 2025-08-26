package com.discloudplugin.discloud.actions

import com.discloudplugin.discloud.settings.ApiKeyState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
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
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.swing.*

class CommitDiscloudAction : AnAction("Commit Discloud") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = files?.any { it.extension == "jar" || it.extension == "go" || it.extension == "rs" || it.extension == "py" || it.extension == "php" || it.extension == "rb" } == true
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
                                    val runtime = detectRuntime(event.project?.basePath, files)
                                    val artifact = createArtifactForUpload(runtime, event.project?.basePath, files)
                                    if (artifact == null) {
                                        SwingUtilities.invokeLater {
                                            JOptionPane.showMessageDialog(null, "Falha ao criar artefato para envio", "Commit Discloud", JOptionPane.ERROR_MESSAGE)
                                        }
                                        return
                                    }
                                    val (bytes, filename) = artifact
                                    val result = uploadArtifactBytes(bytes, filename, apiKey, app.id, runtime.name.lowercase())
                                    SwingUtilities.invokeLater {
                                        JOptionPane.showMessageDialog(null, result, "Commit Discloud", JOptionPane.INFORMATION_MESSAGE)
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

    private fun detectRuntime(projectBasePath: String?, files: Array<VirtualFile>): RuntimeType {
        projectBasePath?.let { base ->
            if (File(base, "go.mod").exists()) return RuntimeType.GO
            if (File(base, "Cargo.toml").exists()) return RuntimeType.RUST
            if (File(base, "pyproject.toml").exists() || File(base, "requirements.txt").exists() || File(base, "setup.py").exists()) return RuntimeType.PYTHON
            if (File(base, "composer.json").exists()) return RuntimeType.PHP
            if (File(base, "Gemfile").exists()) return RuntimeType.RUBY
            if (File(base, "pom.xml").exists() || File(base, "build.gradle").exists()) return RuntimeType.JAVA
        }
        files.forEach { f ->
            when (f.extension?.lowercase()) {
                "jar" -> return RuntimeType.JAVA
                "go" -> return RuntimeType.GO
                "rs" -> return RuntimeType.RUST
                "py" -> return RuntimeType.PYTHON
                "php" -> return RuntimeType.PHP
                "rb" -> return RuntimeType.RUBY
            }
        }
        return RuntimeType.UNKNOWN
    }

    private fun runBuildCommand(projectPath: String?, command: List<String>): Boolean {
        if (projectPath == null) return false
        return try {
            val pb = ProcessBuilder(command).directory(File(projectPath)).redirectErrorStream(true)
            val proc = pb.start()
            proc.inputStream.bufferedReader().use { it.readText() }
            val rc = proc.waitFor()
            rc == 0
        } catch (ex: Exception) {
            false
        }
    }

    private fun zipDirectoryToBytes(dir: File, prefixPathToStrip: String? = null): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(baos)).use { zos ->
            val basePath = if (prefixPathToStrip != null) Paths.get(prefixPathToStrip) else dir.toPath()
            dir.walkTopDown().forEach { f ->
                val rel = basePath.relativize(f.toPath()).toString().replace(File.separatorChar, '/')
                if (f.isDirectory) {
                    if (f.listFiles()?.isEmpty() == true) {
                        val entryName = if (rel.endsWith("/")) rel else "$rel/"
                        zos.putNextEntry(ZipEntry(entryName))
                        zos.closeEntry()
                    }
                } else {
                    val entryName = rel
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(f).use { fis -> fis.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        return baos.toByteArray()
    }

    private fun zipFilesToBytes(files: List<File>, basePathStrip: String? = null): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(baos)).use { zos ->
            files.forEach { f ->
                if (f.isDirectory) {
                    val basePath = Paths.get(basePathStrip ?: f.parent)
                    f.walkTopDown().filter { it.isFile }.forEach { ff ->
                        val rel = basePath.relativize(ff.toPath()).toString().replace(File.separatorChar, '/')
                        zos.putNextEntry(ZipEntry(rel))
                        FileInputStream(ff).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                } else {
                    val entryName = basePathStrip?.let { Paths.get(it).relativize(f.toPath()).toString().replace(File.separatorChar, '/') } ?: f.name
                    zos.putNextEntry(ZipEntry(entryName))
                    FileInputStream(f).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        return baos.toByteArray()
    }

    private fun createArtifactForUpload(runtime: RuntimeType, projectPath: String?, selectedFiles: Array<VirtualFile>): Pair<ByteArray, String>? {
        return try {
            when (runtime) {
                RuntimeType.JAVA -> {
                    val jarFile = selectedFiles.firstOrNull { it.extension.equals("jar", true) }
                    if (jarFile != null) {
                        val bytes = jarFile.inputStream.use { it.readBytes() }
                        return Pair(bytes, jarFile.name)
                    }
                    projectPath?.let {
                        val dir = File(it)
                        val bytes = zipDirectoryToBytes(dir, it)
                        return Pair(bytes, "${dir.name}.zip")
                    }
                    val sel = selectedFiles.firstOrNull()
                    sel?.let { Pair(it.inputStream.use { s -> s.readBytes() }, it.name) }
                }
                RuntimeType.GO -> {
                    val built = runBuildCommand(projectPath, listOf("go", "build", "-o", "build/discloud_bin", "."))
                    if (built && projectPath != null) {
                        val bin = File(projectPath, "build/discloud_bin")
                        if (bin.exists()) {
                            val bytes = zipFilesToBytes(listOf(bin), projectPath)
                            return Pair(bytes, "${File(projectPath).name}-go.zip")
                        }
                    }
                    projectPath?.let {
                        val bytes = zipDirectoryToBytes(File(it), it)
                        return Pair(bytes, "${File(it).name}-src.zip")
                    }
                    val sel = selectedFiles.firstOrNull()
                    sel?.let { Pair(it.inputStream.use { s -> s.readBytes() }, it.name) }
                }
                RuntimeType.RUST -> {
                    val built = runBuildCommand(projectPath, listOf("cargo", "build", "--release"))
                    if (built && projectPath != null) {
                        val targetDir = File(projectPath, "target/release")
                        val bins = targetDir.listFiles()?.filter { it.isFile && it.canExecute() } ?: emptyList()
                        if (bins.isNotEmpty()) {
                            val bytes = zipFilesToBytes(bins, projectPath)
                            return Pair(bytes, "${File(projectPath).name}-rust-bin.zip")
                        }
                    }
                    projectPath?.let {
                        val bytes = zipDirectoryToBytes(File(it), it)
                        return Pair(bytes, "${File(it).name}-src.zip")
                    }
                    val sel = selectedFiles.firstOrNull()
                    sel?.let { Pair(it.inputStream.use { s -> s.readBytes() }, it.name) }
                }
                RuntimeType.PYTHON, RuntimeType.PHP, RuntimeType.RUBY -> {
                    projectPath?.let {
                        val bytes = zipDirectoryToBytes(File(it), it)
                        return Pair(bytes, "${File(it).name}-${runtime.name.lowercase()}.zip")
                    }
                    val sel = selectedFiles.firstOrNull()
                    sel?.let { Pair(it.inputStream.use { s -> s.readBytes() }, it.name) }
                }
                else -> {
                    val sel = selectedFiles.firstOrNull()
                    if (sel != null) {
                        val bytes = sel.inputStream.use { it.readBytes() }
                        return Pair(bytes, sel.name)
                    }
                    projectPath?.let {
                        val bytes = zipDirectoryToBytes(File(it), it)
                        return Pair(bytes, "${File(it).name}.zip")
                    }
                    null
                }
            }
        } catch (ex: Exception) {
            null
        }
    }

    private fun uploadArtifactBytes(bytes: ByteArray, filename: String, apiKey: String, appId: String, runtime: String?): String {
        return try {
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
                runtime?.let {
                    dos.writeBytes("--$boundary\r\n")
                    dos.writeBytes("Content-Disposition: form-data; name=\"runtime\"\r\n\r\n")
                    dos.writeBytes(it + "\r\n")
                }
                dos.writeBytes("--$boundary\r\n")
                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n")
                dos.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
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
            val message = try {
                JSONObject(responseText).optString("message", responseText)
            } catch (e: Exception) {
                responseText
            }
            "Enviado $filename → Status: $responseCode\n$message"
        } catch (e: Exception) {
            "Erro ao enviar $filename: ${e.message}"
        }
    }

    data class AppInfo(val id: String, val name: String, val avatarIcon: String)
    enum class RuntimeType { JAVA, GO, RUST, PYTHON, PHP, RUBY, UNKNOWN }
}
