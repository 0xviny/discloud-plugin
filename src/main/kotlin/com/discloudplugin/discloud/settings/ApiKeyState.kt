package com.discloudplugin.discloud.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "DiscloudApiKeyState",
    storages = [Storage("discloud.xml")]
)
class ApiKeyState : PersistentStateComponent<ApiKeyState> {
    var apiKey: String? = null

    override fun getState(): ApiKeyState = this
    override fun loadState(p0: ApiKeyState) {
        this.apiKey = p0.apiKey
    }

    companion object {
        fun getInstance(): ApiKeyState {
            return com.intellij.openapi.application.ApplicationManager
                .getApplication()
                .getService(ApiKeyState::class.java)
        }
    }
}