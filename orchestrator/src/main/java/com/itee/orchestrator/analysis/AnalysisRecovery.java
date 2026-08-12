package com.itee.orchestrator.analysis;

import java.util.List;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.itee.orchestrator.domain.ExceptionStatus;
import com.itee.orchestrator.domain.TradeException;
import com.itee.orchestrator.ingest.TradeExceptionRepository;

/**
 * Re-queues rows stuck in NEW / ANALYZING / ANALYZING_FAILED after restart or AI outage.
 */
@Component
public class AnalysisRecovery {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRecovery.class);

    private final TradeExceptionRepository repository;
    private final ExceptionAnalysisService analysisService;
    private final TransactionTemplate transactionTemplate;
    private final Executor taskExecutor;

    public AnalysisRecovery(
            TradeExceptionRepository repository,
            ExceptionAnalysisService analysisService,
            TransactionTemplate transactionTemplate,
            @Qualifier("taskExecutor") Executor taskExecutor) {
        this.repository = repository;
        this.analysisService = analysisService;
        this.transactionTemplate = transactionTemplate;
        this.taskExecutor = taskExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        List<TradeException> stuck = repository.findByStatusInOrderByCreatedAtAsc(
                List.of(
                        ExceptionStatus.NEW,
                        ExceptionStatus.ANALYZING,
                        ExceptionStatus.ANALYZING_FAILED));
        if (stuck.isEmpty()) {
            return;
        }
        log.info("Analysis recovery: re-queueing {} exception(s)", stuck.size());
        for (TradeException ex : stuck) {
            if (ex.getStatus() == ExceptionStatus.ANALYZING) {
                transactionTemplate.executeWithoutResult(status -> {
                    repository
                            .findById(ex.getId())
                            .ifPresent(row -> {
                                row.setStatus(ExceptionStatus.NEW);
                                repository.save(row);
                            });
                });
            }
            taskExecutor.execute(() -> analysisService.analyzeAsync(ex.getId()));
        }
    }
}
