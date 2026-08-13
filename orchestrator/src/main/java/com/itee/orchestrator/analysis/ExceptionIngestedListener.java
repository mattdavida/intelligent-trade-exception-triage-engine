package com.itee.orchestrator.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.itee.orchestrator.ingest.TradeExceptionRepository;
import com.itee.orchestrator.web.ExceptionEventHub;

@Component
public class ExceptionIngestedListener {

    private static final Logger log = LoggerFactory.getLogger(ExceptionIngestedListener.class);

    private final TradeExceptionRepository repository;
    private final ExceptionEventHub eventHub;
    private final ExceptionAnalysisService analysisService;

    public ExceptionIngestedListener(
            TradeExceptionRepository repository,
            ExceptionEventHub eventHub,
            ExceptionAnalysisService analysisService) {
        this.repository = repository;
        this.eventHub = eventHub;
        this.analysisService = analysisService;
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIngested(ExceptionIngestedEvent event) {
        repository.findById(event.exceptionId()).ifPresent(eventHub::publish);
        log.info("AFTER_COMMIT analyze kickoff id={}", event.exceptionId());
        analysisService.analyzeAsync(event.exceptionId());
    }
}
