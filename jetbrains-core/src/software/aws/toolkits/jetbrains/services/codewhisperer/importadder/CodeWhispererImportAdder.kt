package software.aws.toolkits.jetbrains.services.codewhisperer.importadder

import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix.importClass
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.progress.ModalTaskOwner.project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.GlobalSearchScope
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererPopupManager.Companion.CODEWHISPERER_USER_ACTION_PERFORMED
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererUserActionListener


class CodeWhispererImportAdder {

    init {
        val connect = ApplicationManager.getApplication().messageBus.connect()
        connect.subscribe(
            CODEWHISPERER_USER_ACTION_PERFORMED,
            object : CodeWhispererUserActionListener {
                override fun afterAccept(states: InvocationContext, sessionContext: SessionContext, rangeMarker: RangeMarker) {
                    val project = states.requestContext.project
                    val psiFile = PsiDocumentManager.getInstance(states.requestContext.project).getPsiFile(rangeMarker.document)
                    LOG.error { "AFTER ACCEPT" }
                    if (psiFile is PsiJavaFile) {
                        LOG.error { "JAVA" }
                        val importClass = JavaPsiFacade.getInstance(project).findClass("java.util.ArrayList", GlobalSearchScope.everythingScope(project))
                        if(importClass!=null) {
                            LOG.error { "ADD importClass" }
                            val importElement: PsiImportStatement = PsiElementFactory.getInstance(project).createImportStatement(importClass)
                            psiFile.importList?.add(importElement)
                        }
                    } else {

                    }
                }
            }
        )
    }

    companion object {
        private val LOG = getLogger<CodeWhispererImportAdder>()
        fun getInstance(): CodeWhispererImportAdder = service()
    }

}
