package com.liftley.sync360.core.debug

expect fun agentDebugLog(
    location: String,
    message: String,
    hypothesisId: String,
    data: Map<String, String> = emptyMap(),
    runId: String = "pre-fix"
)
