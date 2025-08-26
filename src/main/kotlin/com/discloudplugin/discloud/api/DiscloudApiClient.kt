package com.discloudplugin.discloud.api

import org.json.JSONObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class UserResponse(val status: String, val message: String, val user: UserData)

@Serializable
data class UserData(
    val userID: String,
    val username: String,
    val totalRamMb: Int,
    val ramUsedMb: Int,
    val apps: List<String>
)

@Serializable
data class AppResponse(val status: String, val message: String, val apps: AppInfoData)

@Serializable
data class AppInfoData(
    val id: String,
    val name: String,
    val online: Boolean,
    val ram: Int
)

@Serializable
data class AppLogsResponse(val status: String, val message: String, val apps: AppLogs)

@Serializable
data class AppLogs(val id: String, val terminal: AppTerminal)

@Serializable
data class AppTerminal(val big: String, val small: String, val url: String)

class DiscloudApiClient(private val token: String) {

    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val base = "https://api.discloud.app/v2"

    private fun buildRequest(path: String, method: String = "GET", body: RequestBody? = null): Request {
        val rb = Request.Builder()
            .url("$base$path")
            .addHeader("api-token", token)
        when (method) {
            "GET" -> rb.get()
            "PUT" -> rb.put(body ?: RequestBody.create(null, ByteArray(0)))
            "POST" -> rb.post(body ?: RequestBody.create(null, ByteArray(0)))
            else -> rb.get()
        }
        return rb.build()
    }

    fun listApps(): List<AppInfoData> {
        val req = buildRequest("/user")
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: throw RuntimeException("Empty response")
            if (!resp.isSuccessful) throw RuntimeException("Erro: ${resp.code} - $text")

            val userResp = json.decodeFromString<UserResponse>(text)
            val appsList = mutableListOf<AppInfoData>()

            userResp.user.apps.forEach { appId ->
                val appReq = buildRequest("/app/$appId")
                client.newCall(appReq).execute().use { appResp ->
                    val appText = appResp.body?.string() ?: return@use
                    if (!appResp.isSuccessful) return@use
                    val appInfo = json.decodeFromString<AppResponse>(appText).apps
                    appsList.add(appInfo)
                }
            }
            return appsList
        }
    }

    fun startApp(appId: String) {
        val req = buildRequest("/app/$appId/start", "PUT")
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Start failed: ${resp.code}")
        }
    }

    fun restartApp(appId: String) {
        val req = buildRequest("/app/$appId/restart", "PUT")
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Restart failed: ${resp.code}")
        }
    }

    fun stopApp(appId: String) {
        val req = buildRequest("/app/$appId/stop", "PUT")
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Stop failed: ${resp.code}")
        }
    }

    fun getBackup(appId: String, projectBasePath: String) {
        val req = buildRequest("/app/$appId/backup", "GET")
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Backup failed: ${resp.code}")

            val bodyStr = resp.body?.string() ?: throw RuntimeException("Resposta vazia")
            val json = JSONObject(bodyStr)

            if (json.getString("status") == "ok") {
                val backupUrl = json.getJSONObject("backups").getString("url")

                val backupFile = File(projectBasePath, "backup_$appId.zip")
                URL(backupUrl).openStream().use { input ->
                    Files.copy(input, backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                throw RuntimeException("Erro no backup: ${json.optString("message")}")
            }
        }
    }

    fun getLogs(appId: String): String {
        val req = buildRequest("/app/$appId/logs")
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw RuntimeException("Logs failed: ${resp.code}")
            val logsResp = json.decodeFromString<AppLogsResponse>(text)
            return logsResp.apps.terminal.big
        }
    }
}
