// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.sono

import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection

const val SONO_REGION = "us-east-1"
const val SONO_URL = "https://view.awsapps.com/start"

const val IDENTITY_CENTER_ROLE_ACCESS_SCOPE = "sso:account:access"

@Deprecated("pending removal, merge into Q_SCOPES")
val CODEWHISPERER_SCOPES = listOf(
    "codewhisperer_internal:completions",
    "codewhisperer_internal:analysis",
)

val Q_SCOPES = listOf(
    "codewhisperer_internal:conversations",
    "codewhisperer_internal:transformations",
    "codewhisperer_internal:taskassist",
    "codewhisperer_internal:completions",
    "codewhisperer_internal:analysis",
)

val CODECATALYST_SCOPES = listOf(
    "codecatalyst:read_write"
)

fun ToolkitConnection?.isSono() = if (this == null) {
    false
} else {
    this is AwsBearerTokenConnection && this.startUrl == SONO_URL
}

fun isInternalUser(startUrl: String?): Boolean = startUrl == "https://amzn.awsapps.com/start"
