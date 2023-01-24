package software.aws.toolkits.jetbrains.services.codewhisperer.importadder

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.jetbrains.python.psi.PyFile
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
                    val document = rangeMarker.document
                    val psiFile = PsiDocumentManager.getInstance(states.requestContext.project).getPsiFile(rangeMarker.document)
                    LOG.error { "AFTER ACCEPT" }
                    if (psiFile is PsiJavaFile) {
                        LOG.error { "JAVA" }
                        getInstance().insertImportStatementJava(project, document, psiFile, "import com.google.protobuf.ByteString;","com.google.protobuf.ByteString")
                    } else if (psiFile is PyFile) {
                        getInstance().insertImportStatementPython(project, document, psiFile, "import pandas as pd")
                    } else {
                        LOG.error { "Other language" }
                    }
                }
            }
        )
    }

    private fun insertRawImportStatementToDocument(project: Project, document: Document, rawStatement: String, offset: Int) {
        WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(offset, rawStatement)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }

    private fun insertImportStatementJava(project: Project, document: Document, psiJavaFile: PsiJavaFile, statement: String, qualifiedName: String) {
        LOG.error{"JJAVA"}
        val currentImports = psiJavaFile.importList
        val className = qualifiedName.split('.').last()
        if (currentImports?.findSingleImportStatement(className) == null) {
            LOG.error { "Import Stmt does not exists" }
            val importClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.everythingScope(project))
            if (importClass != null) {
                LOG.error { "ADD importClass" }
                val importElement: PsiImportStatement = PsiElementFactory.getInstance(project).createImportStatement(importClass)
                runWriteAction {
                    psiJavaFile.importList?.add(importElement)
                }
            } else {
                LOG.error { "No local import importClass" }
                val existingImports = psiJavaFile.importList?.allImportStatements
                val existingPackages = psiJavaFile.packageStatement
                var offset = 0
                if(existingPackages != null) offset = existingPackages.endOffset
                if(existingImports != null) {
                    offset = existingImports.last().endOffset
                }
                this.insertRawImportStatementToDocument(project, document, "\n"+statement, offset)
            }
        }

    }

    private fun insertImportStatementPython(project: Project, document: Document, pyFile: PyFile, statement: String) {
        LOG.error{"SPYTHON"}
        val currentImports = pyFile.importBlock
        var exists = false;
        currentImports.forEach {
            if (it.text == statement) {
                exists = true
            }
        }
        if (!exists) {
            LOG.error { "Import Stmt does not exists" }
            var offset = 0
            if(currentImports.size != 0) {
                offset = currentImports.last().endOffset
                this.insertRawImportStatementToDocument(project, document, "\n"+statement, offset)
            }
            else {
                val stmt = pyFile.statements.first()
                offset = stmt.startOffset
                this.insertRawImportStatementToDocument(project, document, "\n"+statement + "\n", offset)
            }
        }
    }



    companion object {
        private val LOG = getLogger<CodeWhispererImportAdder>()
        fun getInstance(): CodeWhispererImportAdder = service()
    }

}
