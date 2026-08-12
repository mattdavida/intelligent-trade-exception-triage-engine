package com.itee.orchestrator.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ExceptionIngestedListener {

    private static final Logger log = LoggerFactory.getLogger(ExceptionIngestedListener.class);

    private final ExceptionAnalysisService analysisService;

    public ExceptionIngestedListener(ExceptionAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIngested(ExceptionIngestedEvent event) {
        log.info("AFTER_COMMIT analyze kickoff id={}", event.exceptionId());
        analysisService.analyzeAsync(event.exceptionId());
    }
}
