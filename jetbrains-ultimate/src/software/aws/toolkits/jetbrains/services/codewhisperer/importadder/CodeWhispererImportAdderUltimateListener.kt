// Copyright 2023 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.importadder

import com.intellij.lang.javascript.psi.JSFile
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContext
import software.aws.toolkits.jetbrains.services.codewhisperer.popup.CodeWhispererUserActionListener

class CodeWhispererImportAdderUltimateListener: CodeWhispererUserActionListener {

    override fun afterAccept(states: InvocationContext, sessionContext: SessionContext, rangeMarker: RangeMarker) {
        val project = states.requestContext.project
        val document = rangeMarker.document
        val psiFile = PsiDocumentManager.getInstance(states.requestContext.project).getPsiFile(rangeMarker.document)
        val language = states.requestContext.fileContextInfo.programmingLanguage
        LOG.error { "X AFTER ACCEPT" }
        if (psiFile is JSFile) {
            LOG.error { "Java script file" }
            this.insertImportStatementJavascript(project, document, psiFile, "import * as http from 'http';")
        } else {
            LOG.error { "X Other language" }
        }
    }

    private fun insertRawImportStatementToDocument(project: Project, document: Document, rawStatement: String, offset: Int) {
        WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(offset, rawStatement)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }


    private fun insertImportStatementJavascript(project: Project, document: Document, jsFile: JSFile, statement: String) {
        LOG.error{"JS"}
        val currentImports = jsFile.statements
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
                val stmt = currentImports.first()
                offset = stmt.startOffset
                this.insertRawImportStatementToDocument(project, document, "\n"+statement + "\n", offset)
            }
        }
    }

    companion object {
        private val LOG = getLogger<CodeWhispererImportAdderUltimateListener>()
    }

}
