// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import kotlinx.coroutines.Job
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.QInlineCompletionProvider
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererServiceNew
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.CodewhispererTriggerType
import java.util.concurrent.atomic.AtomicReference

class CodeWhispererRecommendationAction : AnAction(message("codewhisperer.trigger.service")), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null && e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (QRegionProfileManager.getInstance().hasValidConnectionButNoActiveProfile(project)) {
            return
        }
        val editor = e.getRequiredData(CommonDataKeys.EDITOR)
        if (!(
                if (CodeWhispererFeatureConfigService.getInstance().getNewAutoTriggerUX()) {
                    CodeWhispererServiceNew.getInstance().canDoInvocation(editor, CodewhispererTriggerType.OnDemand)
                } else {
                    CodeWhispererService.getInstance().canDoInvocation(editor, CodewhispererTriggerType.OnDemand)
                }
                )
        ) {
            return
        }

        QInlineCompletionProvider.invokeCompletion(editor)
    }

    companion object {
        val ACTION_JOB_KEY = Key.create<AtomicReference<Job?>>("amazonq.codewhisperer.job")
    }
}
