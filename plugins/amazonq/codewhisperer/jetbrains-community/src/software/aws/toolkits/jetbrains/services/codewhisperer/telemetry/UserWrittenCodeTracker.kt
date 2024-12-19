// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.telemetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.Alarm
import com.intellij.util.AlarmFactory
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.language.programmingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.util.runIfIdcConnectionOrTelemetryEnabled
import software.aws.toolkits.jetbrains.settings.AwsSettings
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger


@Service(Service.Level.PROJECT)
class UserWrittenCodeTracker(private val project: Project) : Disposable {
    private val userWrittenCodeLineCount = mutableMapOf<CodeWhispererProgrammingLanguage, Long>()
    private val userWrittenCodeCharacterCount = mutableMapOf<CodeWhispererProgrammingLanguage, Long>()
    private val alarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, this)

    private val isShuttingDown = AtomicBoolean(false)
    private val qInvocationCount: AtomicInteger = AtomicInteger(0)
    private val isQMakingEdits = AtomicBoolean(false)

    init {
        scheduleTracker()
    }

    private fun scheduleTracker() {
        if (!alarm.isDisposed && !isShuttingDown.get()) {
            alarm.addRequest({ flush() }, DEFAULT_CHECK_INTERVAL.toMillis())
        }
    }

    private fun isTelemetryEnabled(): Boolean = AwsSettings.getInstance().isTelemetryEnabled

    fun onQFeatureInvoked() {
        qInvocationCount.incrementAndGet()
    }

    fun onQStartsMakingEdits() {
        isQMakingEdits.set(true)
    }

    fun onQFinishesMakingEdits() {
        isQMakingEdits.set(false)
    }

    private fun flush() {
        try {
            if (!isTelemetryEnabled() || qInvocationCount.get() <= 0) {
                return
            }
            emitCodeWhispererCodeContribution()
        } finally {
            scheduleTracker()
        }
    }

    internal fun documentChanged(event: DocumentEvent) {
        // do not listen to document changed made by Amazon Q itself
        if (isQMakingEdits.get()) {
            return
        }

        // When open a file for the first time, IDE will also emit DocumentEvent for loading with `isWholeTextReplaced = true`
        // Added this condition to filter out those events
        if (event.isWholeTextReplaced) {
            LOG.debug { "event with isWholeTextReplaced flag: $event" }
            if (event.oldTimeStamp == 0L) return
        }
        // only count total tokens when it is a user keystroke input
        // do not count doc changes from copy & paste of >=50 characters
        // do not count other changes from formatter, git command, etc
        // edge case: event can be from user hit enter with indentation where change is \n\t\t, count as 1 char increase in total chars
        // when event is auto closing [{(', there will be 2 separated events, both count as 1 char increase in total chars
        val text = event.newFragment.toString()
        val lines = text.split('\n').size - 1
        if (event.newLength < DOCUMENT_COPY_THRESSHOLD && text.trim().isNotEmpty()) {
            // count doc changes from <50 multi character input as total user written code
            // ignore all white space changes, this usually comes from IntelliJ formatting
            val language = PsiDocumentManager.getInstance(project).getPsiFile(event.document)?.programmingLanguage()
            if (language != null) {
                userWrittenCodeLineCount.set(language, userWrittenCodeLineCount.getOrDefault(language, 0) + lines)
                userWrittenCodeCharacterCount.set(language, userWrittenCodeCharacterCount.getOrDefault(language, 0) + event.newLength)
            }
        }
    }


    internal fun emitCodeWhispererCodeContribution() {
        val customizationArn: String? = CodeWhispererModelConfigurator.getInstance().activeCustomization(project)?.arn
        for ((language, _) in userWrittenCodeCharacterCount) {
            if (userWrittenCodeCharacterCount.getOrDefault(language, 0) <= 0 ) {
                continue
            }
            runIfIdcConnectionOrTelemetryEnabled(project) {
                // here acceptedTokensSize is the count of accepted chars post user modification
                try {
                    val response = CodeWhispererClientAdaptor.getInstance(project).sendCodePercentageTelemetry(
                        language,
                        customizationArn,
                        0,
                        0,
                        0,
                        userWrittenCodeCharacterCount = userWrittenCodeCharacterCount.get(language),
                        userWrittenCodeLineCount = userWrittenCodeLineCount.get((language))
                    )
                    LOG.debug { "Successfully sent code percentage telemetry. RequestId: ${response.responseMetadata().requestId()}" }
                } catch (e: Exception) {
                    val requestId = if (e is CodeWhispererRuntimeException) e.requestId() else null
                    LOG.debug {
                        "Failed to send code percentage telemetry. RequestId: $requestId, ErrorMessage: ${e.message}"
                    }
                }
            }
        }


    }

    companion object {
        private val DEFAULT_CHECK_INTERVAL = Duration.ofMinutes(1)
        private const val DEFAULT_MODIFICATION_INTERVAL_IN_SECONDS = 300 // 5 minutes

        private const val DOCUMENT_COPY_THRESSHOLD = 50
        private val LOG = getLogger<UserWrittenCodeTracker>()

        fun getInstance(project: Project) = project.service<UserWrittenCodeTracker>()
    }

    override fun dispose() {
        if (isShuttingDown.getAndSet(true)) {
            return
        }

        flush()
    }
}