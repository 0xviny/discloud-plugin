package com.discloudplugin.discloud.api

import org.json.JSONObject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody.Part
import okhttp3.MediaType
import okhttp3.RequestBody.Companion.asRequestBody
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
    val online: Boolean = false,
    val ram: Int = 0
)

@Serializable
data class AppLogsResponse(val status: String, val message: String, val apps: AppLogs)

@Serializable
data class AppLogs(val id: String, val terminal: AppTerminal)

@Serializable
data class AppTerminal(val big: String, val small: String, val url: String)

@Serializable
data class TeamMemberData(val modID: String, val perms: List<String> = emptyList())

@Serializable
data class TeamMembersResponse(val status: String, val message: String, val team: List<TeamMemberData> = emptyList())

@Serializable
data class TeamAppsResponse(val status: String, val message: String, val apps: List<AppInfoData> = emptyList())

@Serializable
data class ModsResponse(val status: String, val message: String, val mods: List<TeamMemberData> = emptyList())

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
            "PUT" -> rb.put(body ?: "".toRequestBody(null))
            "POST" -> rb.post(body ?: "".toRequestBody(null))
            "DELETE" -> if (body != null) rb.delete(body) else rb.delete()
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
            val jsonObj = JSONObject(bodyStr)
            if (jsonObj.optString("status") == "ok") {
                val backups = jsonObj.optJSONObject("backups")
                val backupUrl = backups?.optString("url") ?: throw RuntimeException("Backup URL não encontrado")
                val backupFile = File(projectBasePath, "backup_$appId.zip")
                URL(backupUrl).openStream().use { input ->
                    Files.copy(input, backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                throw RuntimeException("Erro no backup: ${jsonObj.optString("message")}")
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

    fun deleteApp(appId: String) {
        val req = buildRequest("/app/$appId/delete", "DELETE")
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Delete failed: ${resp.code}")
        }
    }

    fun updateRam(appId: String, ramMB: Int) {
        val bodyJson = json.encodeToString(mapOf("ramMB" to ramMB))
        val body = bodyJson.toRequestBody("application/json".toMediaType())
        val req = buildRequest("/app/$appId/ram", "PUT", body)
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Update RAM failed: ${resp.code}")
        }
    }

    fun getAppTeam(appId: String): List<TeamMemberData> {
        val req = buildRequest("/app/$appId/team")
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw RuntimeException("Get team failed: ${resp.code}")
            val teamResp = json.decodeFromString<TeamMembersResponse>(text)
            return teamResp.team
        }
    }

    fun addTeamMember(appId: String, modID: String, perms: List<String>) {
        val bodyJson = json.encodeToString(mapOf("modID" to modID, "perms" to perms))
        val body = bodyJson.toRequestBody("application/json".toMediaType())
        val req = buildRequest("/app/$appId/team", "POST", body)
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Add team member failed: ${resp.code}")
        }
    }

    fun editTeamMember(appId: String, modID: String, perms: List<String>) {
        val bodyJson = json.encodeToString(mapOf("modID" to modID, "perms" to perms))
        val body = bodyJson.toRequestBody("application/json".toMediaType())
        val req = buildRequest("/app/$appId/team", "PUT", body)
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Edit team member failed: ${resp.code}")
        }
    }

    fun removeTeamMember(appId: String, modID: String) {
        val req = buildRequest("/app/$appId/team/$modID", "DELETE")
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Remove team member failed: ${resp.code}")
        }
    }

    fun listTeamApps(ownerId: String): List<AppInfoData> {
        val req = buildRequest("/team?ownerID=$ownerId")
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw RuntimeException("List team apps failed: ${resp.code}")
            val appsResp = json.decodeFromString<TeamAppsResponse>(text)
            return appsResp.apps
        }
    }

    fun getMods(ownerId: String): List<TeamMemberData> {
        val req = buildRequest("/team/$ownerId/getMods")
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw RuntimeException("Get mods failed: ${resp.code}")
            val modsResp = json.decodeFromString<ModsResponse>(text)
            return modsResp.mods
        }
    }

    fun teamStart(appId: String) {
        val req = buildRequest("/team/$appId/start", "PUT")
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Team start failed: ${resp.code}")
        }
    }

    fun teamRestart(appId: String) {
        val req = buildRequest("/team/$appId/restart", "PUT")
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Team restart failed: ${resp.code}")
        }
    }

    fun teamStop(appId: String) {
        val req = buildRequest("/team/$appId/stop", "PUT")
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Team stop failed: ${resp.code}")
        }
    }

    fun teamCommit(appId: String, file: File) {
        val mediaType = "application/octet-stream".toMediaType()
        val fileBody = file.asRequestBody(mediaType)
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, fileBody)
            .build()
        val req = buildRequest("/team/$appId/commit", "PUT", multipartBody)
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Team commit failed: ${resp.code}")
        }
    }

    fun teamBackup(appId: String, projectBasePath: String) {
        val req = buildRequest("/team/$appId/backup", "GET")
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Team backup failed: ${resp.code}")
            val bodyStr = resp.body?.string() ?: throw RuntimeException("Resposta vazia")
            val jsonObj = JSONObject(bodyStr)
            if (jsonObj.optString("status") == "ok") {
                val backups = jsonObj.optJSONObject("backups")
                val backupUrl = backups?.optString("url") ?: throw RuntimeException("Backup URL não encontrado")
                val backupFile = File(projectBasePath, "team_backup_$appId.zip")
                URL(backupUrl).openStream().use { input ->
                    Files.copy(input, backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                throw RuntimeException("Erro no team backup: ${jsonObj.optString("message")}")
            }
        }
    }

    fun teamLogs(appId: String): String {
        val req = buildRequest("/team/$appId/logs")
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw RuntimeException("Team logs failed: ${resp.code}")
            val logsResp = json.decodeFromString<AppLogsResponse>(text)
            return logsResp.apps.terminal.big
        }
    }

    fun teamUpdateRam(appId: String, ramMB: Int) {
        val bodyJson = json.encodeToString(mapOf("ramMB" to ramMB))
        val body = bodyJson.toRequestBody("application/json".toMediaType())
        val req = buildRequest("/team/$appId/ram", "PUT", body)
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Team update RAM failed: ${resp.code}")
        }
    }

    fun teamStatus(appId: String): AppInfoData {
        val req = buildRequest("/team/$appId/status")
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw RuntimeException("Team status failed: ${resp.code}")
            val appResp = json.decodeFromString<AppResponse>(text)
            return appResp.apps
        }
    }
}
