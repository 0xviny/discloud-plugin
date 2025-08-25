package com.discloudplugin.discloud.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

class ShowDiscloudToolWindow : AnAction() {
    override fun actionPerformed(p0: AnActionEvent) {
        val project = p0.project ?: return
        val tm = ToolWindowManager.getInstance(project)
        val tw = tm.getToolWindow("Discloud") ?: return

        tw.show(null)
    }
}