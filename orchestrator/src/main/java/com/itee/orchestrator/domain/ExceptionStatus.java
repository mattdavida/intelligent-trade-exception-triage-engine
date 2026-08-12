package com.itee.orchestrator.domain;

public enum ExceptionStatus {
    NEW,
    ANALYZING,
    PENDING_REVIEW,
    ANALYZING_FAILED,
    RESOLVED,
    REJECTED,
    OVERRIDDEN
}
