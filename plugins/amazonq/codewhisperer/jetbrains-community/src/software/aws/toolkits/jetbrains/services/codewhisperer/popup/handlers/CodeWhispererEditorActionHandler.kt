// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.popup.handlers

import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContextNew

abstract class CodeWhispererEditorActionHandler(val states: InvocationContext) : EditorActionHandler()
abstract class CodeWhispererEditorActionHandlerNew(val sessionContext: SessionContextNew) : EditorActionHandler()
